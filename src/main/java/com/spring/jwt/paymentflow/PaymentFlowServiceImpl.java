package com.spring.jwt.paymentflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spring.jwt.exception.ResourceNotFoundException;
import com.spring.jwt.paymentflow.config.PaymentFlowProperties;
import com.spring.jwt.paymentflow.dto.*;
import com.spring.jwt.paymentflow.gateway.PaymentGatewayAdapter;
import com.spring.jwt.paymentflow.gateway.PaymentGatewayResult;
import com.spring.jwt.paymentflow.webhook.PaymentFlowWebhookSignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentFlowServiceImpl implements PaymentFlowService {

    private final PaymentFlowRepository paymentFlowRepository;
    private final PaymentFlowGatewayRegistry gatewayRegistry;
    private final ObjectMapper objectMapper;
    private final PaymentFlowProperties properties;

    @Override
    @Transactional
    public CreatePaymentResponse create(Long userId, CreatePaymentRequest request, String idempotencyKey) {
        String idem = requireIdempotency(idempotencyKey);
        Optional<PaymentFlowEntity> existing = paymentFlowRepository.findByUserIdAndIdempotencyKey(userId, idem);
        if (existing.isPresent()) {
            return toCreateResponse(existing.get());
        }

        String abandonSecret = java.util.UUID.randomUUID().toString().replace("-", "");
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("billingName", request.getBillingName().trim());
        meta.put("billingAddress", request.getBillingAddress().trim());
        meta.put("billingTel", request.getBillingTel().trim());

        PaymentFlowEntity e = PaymentFlowEntity.builder()
                .userId(userId)
                .orderRef(request.getOrderRef().trim())
                .amount(request.getAmount().setScale(2, java.math.RoundingMode.HALF_UP))
                .currency(trimCurrency(request.getCurrency()))
                .status(PaymentFlowStatus.INITIATED)
                .gatewayKind("CCAVENUE_FORM")
                .idempotencyKey(idem)
                .metadataJson(meta.toString())
                .abandonSecret(abandonSecret)
                .build();
        e = paymentFlowRepository.saveAndFlush(e);
        meta.put("merchantOrderId", "PFL-" + e.getId());
        e.setMetadataJson(meta.toString());
        e = paymentFlowRepository.save(e);
        return toCreateResponse(e);
    }

    @Override
    @Transactional
    public InitiatePaymentResponse initiate(Long userId, InitiatePaymentRequest request, String idempotencyKey) {
        String idem = requireIdempotency(idempotencyKey);
        PaymentFlowEntity p = paymentFlowRepository.findByIdForUpdate(request.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("payment not found"));
        if (!p.getUserId().equals(userId)) {
            throw new SecurityException("payment does not belong to current user");
        }

        if (p.getInitiateIdempotencyKey() != null && idem.equals(p.getInitiateIdempotencyKey())) {
            return buildInitiateResponseFromEntity(p);
        }
        if (p.getInitiateIdempotencyKey() != null
                && !idem.equals(p.getInitiateIdempotencyKey())
                && p.getStatus() != PaymentFlowStatus.PENDING_GATEWAY) {
            throw new IllegalStateException("Use the same Idempotency-Key as the first initiate call for this payment");
        }

        PaymentFlowStatus st = p.getStatus();
        if (st == PaymentFlowStatus.SUCCESS || st == PaymentFlowStatus.FAILED) {
            return buildInitiateResponseFromEntity(p);
        }
        if (st == PaymentFlowStatus.ABANDONED) {
            throw new IllegalStateException("payment was abandoned");
        }
        if (st != PaymentFlowStatus.INITIATED && st != PaymentFlowStatus.PENDING_GATEWAY) {
            throw new IllegalStateException("payment cannot be initiated in status " + st);
        }

        PaymentGatewayAdapter adapter = gatewayRegistry.require(p.getGatewayKind());
        PaymentGatewayResult r = adapter.initiate(p);
        p.setInitiateIdempotencyKey(idem);
        Instant now = Instant.now();

        if (r instanceof PaymentGatewayResult.DeterministicFailure df) {
            p.setStatus(PaymentFlowStatus.FAILED);
            p.setErrorCode(df.errorCode());
            p.setErrorMessage(truncate(df.errorMessage(), 500));
            p.setPendingGatewaySince(null);
            p.setGatewayClientPayloadJson(null);
            paymentFlowRepository.save(p);
            return buildInitiateResponseFromEntity(p);
        }
        if (r instanceof PaymentGatewayResult.PendingGateway pg) {
            p.setStatus(PaymentFlowStatus.PENDING_GATEWAY);
            p.setPendingGatewaySince(now);
            p.setErrorCode("PENDING_GATEWAY");
            p.setErrorMessage(truncate(pg.reason(), 500));
            p.setGatewayClientPayloadJson(null);
            paymentFlowRepository.save(p);
            return buildInitiateResponseFromEntity(p);
        }
        if (r instanceof PaymentGatewayResult.ProcessingPayload pp) {
            p.setStatus(PaymentFlowStatus.PROCESSING);
            p.setGatewayTransactionId(pp.provisionalGatewayTxnId());
            p.setGatewayClientPayloadJson(pp.clientPayloadJson());
            p.setPendingGatewaySince(now);
            p.setErrorCode(null);
            p.setErrorMessage(null);
            paymentFlowRepository.save(p);
            return buildInitiateResponseFromEntity(p);
        }
        throw new IllegalStateException("unknown gateway result type");
    }

    @Override
    @Transactional
    public void abandon(AbandonPaymentRequest request) {
        PaymentFlowEntity p = paymentFlowRepository.findByIdForUpdate(request.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("payment not found"));
        if (!p.getAbandonSecret().equals(request.getAbandonSecret())) {
            throw new SecurityException("invalid abandon secret");
        }
        PaymentFlowStatus st = p.getStatus();
        if (st == PaymentFlowStatus.SUCCESS) {
            return;
        }
        if (st == PaymentFlowStatus.FAILED) {
            return;
        }
        if (st == PaymentFlowStatus.ABANDONED) {
            return;
        }
        if (st == PaymentFlowStatus.INITIATED || st == PaymentFlowStatus.PROCESSING || st == PaymentFlowStatus.PENDING_GATEWAY) {
            p.setStatus(PaymentFlowStatus.ABANDONED);
            p.setAbandonedAt(Instant.now());
            paymentFlowRepository.save(p);
            log.info("payment abandoned id={}", p.getId());
        }
    }

    @Override
    @Transactional
    public void handleWebhook(String rawBody, String signatureHexHeader) {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("payment-flow webhook secret not configured — rejecting webhook");
            throw new SecurityException("webhook not configured");
        }
        String expected = PaymentFlowWebhookSignatureUtil.hmacSha256Hex(secret, rawBody);
        if (!PaymentFlowWebhookSignatureUtil.secureEqualsHex(expected, signatureHexHeader == null ? "" : signatureHexHeader.trim())) {
            throw new SecurityException("invalid webhook signature");
        }

        JsonNode root = parseJson(rawBody);
        Long paymentId = root.path("paymentId").asLong(0);
        if (paymentId == 0L) {
            throw new IllegalArgumentException("paymentId required");
        }
        String gatewayTxn = text(root, "gatewayTransactionId");
        if (gatewayTxn == null || gatewayTxn.isBlank()) {
            throw new IllegalArgumentException("gatewayTransactionId required");
        }
        boolean success = root.path("success").asBoolean(false);
        BigDecimal amount = new BigDecimal(root.path("amount").asText("0")).setScale(2, java.math.RoundingMode.HALF_UP);

        PaymentFlowEntity p = paymentFlowRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("payment not found"));

        Optional<PaymentFlowEntity> other = paymentFlowRepository.findByGatewayTransactionId(gatewayTxn);
        if (other.isPresent() && !other.get().getId().equals(paymentId)) {
            throw new IllegalStateException("gateway transaction already bound to another payment");
        }

        if (p.getAmount().compareTo(amount) != 0) {
            throw new SecurityException("amount mismatch");
        }

        if (p.getStatus() == PaymentFlowStatus.SUCCESS && gatewayTxn.equals(p.getGatewayTransactionId())) {
            return;
        }

        if (success) {
            p.setStatus(PaymentFlowStatus.SUCCESS);
            p.setGatewayTransactionId(gatewayTxn);
            p.setErrorCode(null);
            p.setErrorMessage(null);
            p.setPendingGatewaySince(null);
            paymentFlowRepository.save(p);
            log.info("payment webhook SUCCESS id={} txn={}", paymentId, gatewayTxn);
            return;
        }

        if (p.getStatus() == PaymentFlowStatus.SUCCESS) {
            return;
        }
        p.setStatus(PaymentFlowStatus.FAILED);
        p.setGatewayTransactionId(gatewayTxn);
        p.setErrorCode("WEBHOOK_FAILURE");
        p.setErrorMessage(truncate(text(root, "message"), 500));
        p.setPendingGatewaySince(null);
        paymentFlowRepository.save(p);
        log.info("payment webhook FAILED id={} txn={}", paymentId, gatewayTxn);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse status(Long userId, Long paymentId) {
        PaymentFlowEntity p = paymentFlowRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("payment not found"));
        if (!p.getUserId().equals(userId)) {
            throw new SecurityException("payment does not belong to current user");
        }
        return toStatusResponse(p);
    }

    @Override
    @Transactional
    public void reconcileStalePayments() {
        Instant now = Instant.now();
        Instant pendingCutoff = now.minus(properties.getPendingGatewayStaleMinutes(), ChronoUnit.MINUTES);
        List<PaymentFlowEntity> stalePending = paymentFlowRepository.findStalePendingGateway(
                List.of(PaymentFlowStatus.PROCESSING, PaymentFlowStatus.PENDING_GATEWAY),
                pendingCutoff);
        for (PaymentFlowEntity p : stalePending) {
            PaymentFlowEntity locked = paymentFlowRepository.findByIdForUpdate(p.getId()).orElse(null);
            if (locked == null) {
                continue;
            }
            if (locked.getStatus() != PaymentFlowStatus.PROCESSING && locked.getStatus() != PaymentFlowStatus.PENDING_GATEWAY) {
                continue;
            }
            if (locked.getPendingGatewaySince() == null || !locked.getPendingGatewaySince().isBefore(now.minus(properties.getPendingGatewayStaleMinutes(), ChronoUnit.MINUTES))) {
                continue;
            }
            locked.setStatus(PaymentFlowStatus.FAILED);
            locked.setErrorCode("RECONCILE_TIMEOUT");
            locked.setErrorMessage("No definitive gateway outcome within configured window");
            locked.setPendingGatewaySince(null);
            paymentFlowRepository.save(locked);
            log.warn("payment reconciled to FAILED (timeout) id={}", locked.getId());
        }

        Instant abandonCutoff = now.minus(properties.getAbandonedFailAfterMinutes(), ChronoUnit.MINUTES);
        List<PaymentFlowEntity> staleAbandon = paymentFlowRepository.findStaleAbandoned(PaymentFlowStatus.ABANDONED, abandonCutoff);
        for (PaymentFlowEntity p : staleAbandon) {
            PaymentFlowEntity locked = paymentFlowRepository.findByIdForUpdate(p.getId()).orElse(null);
            if (locked == null || locked.getStatus() != PaymentFlowStatus.ABANDONED) {
                continue;
            }
            if (locked.getAbandonedAt() == null || !locked.getAbandonedAt().isBefore(now.minus(properties.getAbandonedFailAfterMinutes(), ChronoUnit.MINUTES))) {
                continue;
            }
            locked.setStatus(PaymentFlowStatus.FAILED);
            locked.setErrorCode("ABANDONED_EXPIRED");
            locked.setErrorMessage("Checkout abandoned and not completed");
            paymentFlowRepository.save(locked);
            log.warn("payment reconciled ABANDONED -> FAILED id={}", locked.getId());
        }
    }

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid webhook json");
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String requireIdempotency(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        return key.trim();
    }

    private static String trimCurrency(String c) {
        if (c == null || c.isBlank()) {
            return "INR";
        }
        return c.trim().toUpperCase(Locale.ROOT);
    }

    private CreatePaymentResponse toCreateResponse(PaymentFlowEntity e) {
        return CreatePaymentResponse.builder()
                .paymentId(e.getId())
                .status(e.getStatus())
                .abandonSecret(e.getAbandonSecret())
                .amount(e.getAmount())
                .currency(e.getCurrency())
                .orderRef(e.getOrderRef())
                .build();
    }

    private InitiatePaymentResponse buildInitiateResponseFromEntity(PaymentFlowEntity p) {
        return InitiatePaymentResponse.builder()
                .paymentId(p.getId())
                .status(p.getStatus())
                .gatewayClientPayloadJson(p.getGatewayClientPayloadJson())
                .errorCode(p.getErrorCode())
                .errorMessage(p.getErrorMessage())
                .build();
    }

    private PaymentStatusResponse toStatusResponse(PaymentFlowEntity p) {
        return PaymentStatusResponse.builder()
                .paymentId(p.getId())
                .status(p.getStatus())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .orderRef(p.getOrderRef())
                .gatewayTransactionId(p.getGatewayTransactionId())
                .errorCode(p.getErrorCode())
                .errorMessage(p.getErrorMessage())
                .abandonedAt(p.getAbandonedAt())
                .pendingGatewaySince(p.getPendingGatewaySince())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
