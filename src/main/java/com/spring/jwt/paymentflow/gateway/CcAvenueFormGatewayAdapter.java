package com.spring.jwt.paymentflow.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.jwt.Payment.CcAvenuePaymentRequest;
import com.spring.jwt.Payment.CcAvenuePaymentService;
import com.spring.jwt.paymentflow.PaymentFlowEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Uses existing CCAvenue server-side HTML form generation. Deterministic failures (bad keys, encrypt)
 * must not enter {@link PaymentGatewayResult.PendingGateway}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CcAvenueFormGatewayAdapter implements PaymentGatewayAdapter {

    private final CcAvenuePaymentService ccAvenuePaymentService;
    private final ObjectMapper objectMapper;

    @Override
    public String kind() {
        return "CCAVENUE_FORM";
    }

    @Override
    public PaymentGatewayResult initiate(PaymentFlowEntity payment) {
        try {
            JsonNode meta = payment.getMetadataJson() == null || payment.getMetadataJson().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(payment.getMetadataJson());

            String billingName = text(meta, "billingName", "Customer");
            String billingAddress = text(meta, "billingAddress", "Address");
            String billingTel = text(meta, "billingTel", "0000000000");
            String merchantOrderId = text(meta, "merchantOrderId", "PFL-" + payment.getId());

            CcAvenuePaymentRequest req = CcAvenuePaymentRequest.builder()
                    .orderId(merchantOrderId)
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .billingName(billingName)
                    .billingAddress(billingAddress)
                    .billingTel(billingTel)
                    .build();

            String formHtml = ccAvenuePaymentService.generatePaymentForm(req);
            Map<String, Object> payload = new HashMap<>();
            payload.put("kind", kind());
            payload.put("paymentFormHtml", formHtml);
            String json = objectMapper.writeValueAsString(payload);
            String provisionalTxn = "CCA-FORM-" + payment.getId();
            return new PaymentGatewayResult.ProcessingPayload(provisionalTxn, json);
        } catch (RuntimeException ex) {
            log.warn("CCAvenue form initiate failed paymentId={}: {}", payment.getId(), ex.getMessage());
            if (PaymentFailureClassifier.isDeterministic(ex)) {
                return new PaymentGatewayResult.DeterministicFailure("DETERMINISTIC_GATEWAY", truncate(ex.getMessage(), 480));
            }
            if (PaymentFailureClassifier.isLikelyNetworkOrUnknown(ex)) {
                return new PaymentGatewayResult.PendingGateway(truncate(ex.getMessage(), 480));
            }
            // Default uncertain: webhook/reconcile may still arrive
            return new PaymentGatewayResult.PendingGateway(truncate(ex.getMessage(), 480));
        } catch (Exception ex) {
            log.error("CCAvenue form initiate unexpected paymentId={}", payment.getId(), ex);
            return new PaymentGatewayResult.PendingGateway("unexpected: " + truncate(ex.getMessage(), 480));
        }
    }

    private static String text(JsonNode meta, String field, String def) {
        JsonNode n = meta.get(field);
        if (n == null || n.isNull() || !n.isTextual()) {
            return def;
        }
        String v = n.asText().trim();
        return v.isEmpty() ? def : v;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
