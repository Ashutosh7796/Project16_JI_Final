package com.spring.jwt.Payment;

import com.spring.jwt.FarmerPayment.FarmerPaymentResponseDTO;
import com.spring.jwt.FarmerPayment.FarmerPaymentService;
import com.spring.jwt.ProductBuyPending.PaymentVerifyDto;
import com.spring.jwt.ProductBuyPending.ProductBuyService;
import com.spring.jwt.checkout.CheckoutMerchantOrderIds;
import com.spring.jwt.checkout.CheckoutService;
import com.spring.jwt.entity.PaymentCallbackQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Runs callback handling in its own Spring-managed bean so {@link Propagation#REQUIRES_NEW}
 * applies reliably (no self-invocation from {@code @Component} listeners).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCallbackProcessingService {

    private static final int MAX_RETRY_ATTEMPTS = 5;

    private final PaymentCallbackQueueRepository queueRepository;
    private final CcAvenueConfig ccAvenueConfig;
    private final ProductBuyService productBuyService;
    private final FarmerPaymentService farmerPaymentService;
    private final CheckoutService checkoutService;
    private final PaymentAuditService auditService;

    /**
     * Rows stuck in PROCESSING (e.g. JVM died after claim) are moved back to RETRY for the scheduled poller.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int releaseStaleProcessingRows(int olderThanMinutes) {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(olderThanMinutes);
        int n = queueRepository.releaseStaleProcessing(staleBefore, LocalDateTime.now());
        if (n > 0) {
            log.warn("Released {} payment callback queue row(s) stuck in PROCESSING (no heartbeat for {} min)",
                    n, olderThanMinutes);
        }
        return n;
    }

    /**
     * One queue row per transaction: atomic claim, then business logic, then terminal status.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processByQueueId(Long queueId) {
        int claimed = queueRepository.claimForProcessing(queueId, LocalDateTime.now());
        if (claimed == 0) {
            log.debug("Skip queueId={} (not claimable as PENDING/RETRY)", queueId);
            return;
        }

        PaymentCallbackQueue item = queueRepository.findById(queueId).orElse(null);
        if (item == null) {
            return;
        }

        try {
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
            case "CHECKOUT_RESPONSE" -> processCheckoutResponse(item);
            case "CHECKOUT_CANCEL" -> processCheckoutCancel(item);
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

    private void processCheckoutResponse(PaymentCallbackQueue item) {
        Map<String, String> params = decryptAndParse(item.getEncResp());
        if (params == null) {
            throw new IllegalArgumentException("Invalid encrypted checkout callback payload");
        }
        String orderId = params.get("order_id");
        item.setOrderId(orderId);
        checkoutService.handleCcAvenueDecryptedCallback(params, item.getEncResp(), item.getClientIp());
        auditService.logEvent("CHECKOUT", null, orderId,
                "CALLBACK_PROCESSED", null, params.get("order_status"), null, item.getClientIp(),
                "tracking_id=" + params.get("tracking_id"));
        item.setResultStatus(normalizeCheckoutQueueResult(params.get("order_status")));
    }

    private void processCheckoutCancel(PaymentCallbackQueue item) {
        if (item.getEncResp() != null && !item.getEncResp().isBlank()) {
            // Normal cancel with data — decrypt and process as failure
            Map<String, String> params = decryptAndParse(item.getEncResp());
            if (params == null) {
                throw new IllegalArgumentException("Invalid encrypted checkout cancel payload");
            }
            params.putIfAbsent("order_status", "Aborted");
            String orderId = params.get("order_id");
            item.setOrderId(orderId);
            checkoutService.handleCcAvenueDecryptedCallback(params, item.getEncResp(), item.getClientIp());
            auditService.logEvent("CHECKOUT", null, orderId,
                    "CANCELLED", null, "FAILED", null, item.getClientIp(), null);
            item.setResultStatus("CANCELLED");
            return;
        }

        // No encResp — CCAvenue sent the user back without any data.
        // This is the 10002 (Merchant Authentication Failed) case.
        // We must still fail the order so it doesn't stay stuck in PAYMENT_PENDING.
        log.warn("Checkout cancel callback with NO encResp — synthesizing failure for latest pending order");
        auditService.logEvent("CHECKOUT", null, null,
                "CANCELLED_NO_DATA", null, "FAILED", null, item.getClientIp(),
                "No encResp from gateway — merchant auth may have failed");

        // Synthesize a minimal failure callback so handleCcAvenueDecryptedCallback can process it
        // We look up the latest PAYMENT_PENDING order and fail it directly.
        try {
            checkoutService.failLatestPendingOrderOnGatewayCancel(item.getClientIp());
        } catch (Exception ex) {
            log.warn("failLatestPendingOrderOnGatewayCancel error: {}", ex.getMessage());
        }
        item.setResultStatus("CANCELLED");
    }

    private static String normalizeCheckoutQueueResult(String orderStatus) {
        if (orderStatus == null) {
            return "UNKNOWN";
        }
        if ("Success".equalsIgnoreCase(orderStatus)) {
            return "SUCCESS";
        }
        if (orderStatus.toLowerCase().contains("pending") || orderStatus.toLowerCase().contains("initiated")) {
            return "PENDING";
        }
        return "FAILED";
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
