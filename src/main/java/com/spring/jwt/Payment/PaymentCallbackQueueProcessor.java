package com.spring.jwt.Payment;

import com.spring.jwt.FarmerPayment.FarmerPaymentResponseDTO;
import com.spring.jwt.FarmerPayment.FarmerPaymentService;
import com.spring.jwt.ProductBuyPending.PaymentVerifyDto;
import com.spring.jwt.ProductBuyPending.ProductBuyService;
import com.spring.jwt.entity.PaymentCallbackQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCallbackQueueProcessor {

    private static final List<String> PROCESSABLE_STATUSES = Arrays.asList("PENDING", "RETRY");
    private static final int MAX_RETRY_ATTEMPTS = 5;

    private final PaymentCallbackQueueRepository queueRepository;
    private final CcAvenueConfig ccAvenueConfig;
    private final ProductBuyService productBuyService;
    private final FarmerPaymentService farmerPaymentService;
    private final PaymentAuditService auditService;

    @Value("${payment.callback.queue.batch-size:20}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${payment.callback.queue.poll-delay-ms:3000}")
    @Transactional
    public void processQueue() {
        List<PaymentCallbackQueue> items = queueRepository
                .findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        PROCESSABLE_STATUSES, LocalDateTime.now(), PageRequest.of(0, batchSize));

        for (PaymentCallbackQueue item : items) {
            processSingle(item);
        }
    }

    private void processSingle(PaymentCallbackQueue item) {
        try {
            item.setStatus("PROCESSING");
            queueRepository.save(item);

            handleCallbackItem(item);

            item.setStatus("DONE");
            item.setLastError(null);
            queueRepository.save(item);
        } catch (Exception ex) {
            int attempts = item.getAttemptCount() + 1;
            item.setAttemptCount(attempts);
            item.setLastError(truncate(ex.getMessage(), 2000));

            if (attempts >= MAX_RETRY_ATTEMPTS) {
                item.setStatus("DEAD");
                auditService.logEvent(
                        item.getPaymentType(),
                        null,
                        null,
                        "CALLBACK_DEAD_LETTER",
                        "RETRY",
                        "DEAD",
                        null,
                        item.getClientIp(),
                        "queueId=" + item.getId() + ", error=" + truncate(ex.getMessage(), 500)
                );
            } else {
                item.setStatus("RETRY");
                item.setNextAttemptAt(LocalDateTime.now().plusMinutes(Math.min(15, attempts)));
            }
            queueRepository.save(item);
            log.error("Payment callback processing failed: queueId={}, attempts={}, status={}, error={}",
                    item.getId(), item.getAttemptCount(), item.getStatus(), ex.getMessage());
        }
    }

    private void handleCallbackItem(PaymentCallbackQueue item) {
        String callbackType = item.getCallbackType();
        switch (callbackType) {
            case "PRODUCT_RESPONSE" -> processProductResponse(item);
            case "PRODUCT_CANCEL" -> processProductCancel(item);
            case "FARMER_RESPONSE" -> processFarmerResponse(item);
            case "FARMER_CANCEL" -> processFarmerCancel(item);
            default -> throw new IllegalArgumentException("Unknown callback type: " + callbackType);
        }
    }

    private void processProductResponse(PaymentCallbackQueue item) {
        Map<String, String> params = decryptAndParse(item.getEncResp());
        if (params == null) {
            throw new IllegalArgumentException("Invalid encrypted callback payload");
        }

        String orderId = params.get("order_id");
        String orderStatus = params.get("order_status");
        String trackingId = params.get("tracking_id");
        String paymentMode = params.get("payment_mode");
        item.setOrderId(orderId);

        if ("Success".equalsIgnoreCase(orderStatus)) {
            Long pendingOrderId = extractPendingId(orderId);
            if (pendingOrderId == null) {
                throw new IllegalArgumentException("Invalid product order id: " + orderId);
            }
            PaymentVerifyDto verifyDto = new PaymentVerifyDto();
            verifyDto.setPendingOrderId(pendingOrderId);
            verifyDto.setPaymentId(trackingId);
            verifyDto.setPaymentMode(mapCcAvenuePaymentMode(paymentMode));
            verifyDto.setOrderId(orderId);
            verifyDto.setAmount(params.get("amount"));
            productBuyService.confirmPayment(verifyDto);

            auditService.logEvent("PRODUCT", pendingOrderId, orderId,
                    "VERIFIED_SUCCESS", "PENDING", "SUCCESS", null, item.getClientIp(), null);
            item.setResultStatus("SUCCESS");
        } else {
            Long pendingOrderId = extractPendingId(orderId);
            if (pendingOrderId != null) {
                productBuyService.markPaymentFailed(pendingOrderId);
                auditService.logEvent("PRODUCT", pendingOrderId, orderId,
                        "VERIFIED_FAILED", "PENDING", "FAILED", null, item.getClientIp(),
                        "status_message=" + params.get("status_message"));
            }
            item.setResultStatus("FAILED");
        }
    }

    private void processProductCancel(PaymentCallbackQueue item) {
        if (item.getEncResp() == null || item.getEncResp().isBlank()) {
            auditService.logEvent("PRODUCT", null, null,
                    "CANCELLED", null, "FAILED", null, item.getClientIp(), "No encResp");
            return;
        }
        Map<String, String> params = decryptAndParse(item.getEncResp());
        if (params == null) {
            throw new IllegalArgumentException("Invalid encrypted cancel payload");
        }
        String orderId = params.get("order_id");
        item.setOrderId(orderId);
        Long pendingOrderId = extractPendingId(orderId);
        if (pendingOrderId != null) {
            productBuyService.markPaymentFailed(pendingOrderId);
        }
        auditService.logEvent("PRODUCT", pendingOrderId, orderId,
                "CANCELLED", null, "FAILED", null, item.getClientIp(), null);
        item.setResultStatus("CANCELLED");
    }

    private void processFarmerResponse(PaymentCallbackQueue item) {
        Map<String, String> params = decryptAndParse(item.getEncResp());
        if (params == null) {
            throw new IllegalArgumentException("Invalid encrypted callback payload");
        }
        item.setOrderId(params.get("order_id"));
        FarmerPaymentResponseDTO result = farmerPaymentService.handleCallback(params, item.getClientIp());
        item.setResultStatus(result.getPaymentStatus());
        auditService.logEvent("FARMER", null, params.get("order_id"),
                "CALLBACK_PROCESSED", "PENDING", result.getPaymentStatus(), null, item.getClientIp(),
                "paymentId=" + result.getPaymentId());
    }

    private void processFarmerCancel(PaymentCallbackQueue item) {
        if (item.getEncResp() == null || item.getEncResp().isBlank()) {
            auditService.logEvent("FARMER", null, null,
                    "CANCELLED", null, "FAILED", null, item.getClientIp(), "No encResp");
            return;
        }
        Map<String, String> params = decryptAndParse(item.getEncResp());
        if (params == null) {
            throw new IllegalArgumentException("Invalid encrypted cancel payload");
        }
        params.put("order_status", "Failure");
        item.setOrderId(params.get("order_id"));
        farmerPaymentService.handleCallback(params, item.getClientIp());
        auditService.logEvent("FARMER", null, params.get("order_id"),
                "CANCELLED", null, "FAILED", null, item.getClientIp(), null);
        item.setResultStatus("CANCELLED");
    }

    private Map<String, String> decryptAndParse(String encResp) {
        try {
            String decrypted = CcAvenueUtil.decrypt(encResp, ccAvenueConfig.getWorkingKey());
            return parseResponseString(decrypted);
        } catch (Exception ex) {
            log.error("Failed to decrypt callback payload: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, String> parseResponseString(String response) {
        Map<String, String> map = new HashMap<>();
        StringTokenizer tokenizer = new StringTokenizer(response, "&");
        while (tokenizer.hasMoreTokens()) {
            String pair = tokenizer.nextToken();
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = pair.substring(0, idx).trim();
                String value = idx < pair.length() - 1 ? pair.substring(idx + 1).trim() : "";
                map.put(key, value);
            }
        }
        return map;
    }

    private Long extractPendingId(String orderId) {
        if (orderId == null) return null;
        try {
            if (orderId.startsWith("PROD-")) {
                return Long.parseLong(orderId.substring(5));
            }
            return Long.parseLong(orderId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String mapCcAvenuePaymentMode(String ccavenueMode) {
        if (ccavenueMode == null) return "CCAVENUE";
        return switch (ccavenueMode.toUpperCase()) {
            case "CREDIT CARD", "CC" -> "CARD";
            case "DEBIT CARD", "DC" -> "CARD";
            case "NET BANKING", "NB" -> "NETBANKING";
            case "WALLET", "WL" -> "WALLET";
            case "UPI" -> "UPI";
            default -> "CCAVENUE";
        };
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}
