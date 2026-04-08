package com.spring.jwt.Payment;

import com.spring.jwt.FarmerPayment.FarmerPaymentService;
import com.spring.jwt.ProductBuyPending.PaymentVerifyDto;
import com.spring.jwt.ProductBuyPending.ProductBuyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentCallbackController {

    private final CcAvenueConfig ccAvenueConfig;
    private final ProductBuyService productBuyService;
    private final FarmerPaymentService farmerPaymentService;
    private final PaymentAuditService auditService;

    @Value("${app.url.frontend:http://localhost:5173}")
    private String frontendUrl;

    @PostMapping("/product/response")
    public void handleProductResponse(@RequestParam("encResp") String encResp,
                                      HttpServletRequest request,
                                      HttpServletResponse response) throws IOException {
        String clientIp = getClientIp(request);
        log.info("Product payment callback received from IP: {}", clientIp);

        Map<String, String> params = decryptAndParse(encResp);
        if (params == null) {
            auditService.logEvent("PRODUCT", null, null,
                    "CALLBACK_DECRYPT_FAILED", null, null, null, clientIp, "Failed to decrypt encResp");
            response.sendRedirect(frontendUrl + "/payment/failed?reason=invalid_response");
            return;
        }

        String orderId = params.get("order_id");
        String orderStatus = params.get("order_status");
        String trackingId = params.get("tracking_id");
        String bankRefNo = params.get("bank_ref_no");
        String paymentMode = params.get("payment_mode");

        auditService.logEvent("PRODUCT", null, orderId,
                "CALLBACK_RECEIVED", null, orderStatus, null, clientIp,
                "tracking_id=" + trackingId + ", bank_ref_no=" + bankRefNo);

        try {
            if ("Success".equalsIgnoreCase(orderStatus)) {
                Long pendingOrderId = extractPendingId(orderId);
                if (pendingOrderId != null) {
                    PaymentVerifyDto verifyDto = new PaymentVerifyDto();
                    verifyDto.setPendingOrderId(pendingOrderId);
                    verifyDto.setPaymentId(trackingId);
                    verifyDto.setPaymentMode(mapCcAvenuePaymentMode(paymentMode));
                    productBuyService.confirmPayment(verifyDto);

                    auditService.logEvent("PRODUCT", pendingOrderId, orderId,
                            "VERIFIED_SUCCESS", "PENDING", "SUCCESS", null, clientIp, null);
                }
                response.sendRedirect(frontendUrl + "/payment/success?order=" + orderId);
            } else {
                Long pendingOrderId = extractPendingId(orderId);
                if (pendingOrderId != null) {
                    productBuyService.markPaymentFailed(pendingOrderId);

                    auditService.logEvent("PRODUCT", pendingOrderId, orderId,
                            "VERIFIED_FAILED", "PENDING", "FAILED", null, clientIp,
                            "status_message=" + params.get("status_message"));
                }
                response.sendRedirect(frontendUrl + "/payment/failed?order=" + orderId);
            }
        } catch (Exception e) {
            log.error("Error processing product payment callback for order {}: {}", orderId, e.getMessage());
            response.sendRedirect(frontendUrl + "/payment/failed?order=" + orderId);
        }
    }

    @PostMapping("/product/cancel")
    public void handleProductCancel(@RequestParam(value = "encResp", required = false) String encResp,
                                    HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        String clientIp = getClientIp(request);
        log.info("Product payment cancelled from IP: {}", clientIp);

        if (encResp != null) {
            Map<String, String> params = decryptAndParse(encResp);
            if (params != null) {
                String orderId = params.get("order_id");
                Long pendingOrderId = extractPendingId(orderId);
                if (pendingOrderId != null) {
                    productBuyService.markPaymentFailed(pendingOrderId);
                    auditService.logEvent("PRODUCT", pendingOrderId, orderId,
                            "CANCELLED", "PENDING", "FAILED", null, clientIp, null);
                }
            }
        }

        response.sendRedirect(frontendUrl + "/payment/cancelled");
    }

    @PostMapping("/farmer/response")
    public void handleFarmerResponse(@RequestParam("encResp") String encResp,
                                     HttpServletRequest request,
                                     HttpServletResponse response) throws IOException {
        String clientIp = getClientIp(request);
        log.info("Farmer payment callback received from IP: {}", clientIp);

        Map<String, String> params = decryptAndParse(encResp);
        if (params == null) {
            auditService.logEvent("FARMER", null, null,
                    "CALLBACK_DECRYPT_FAILED", null, null, null, clientIp, "Failed to decrypt encResp");
            response.sendRedirect(frontendUrl + "/farmer-payment/failed?reason=invalid_response");
            return;
        }

        String orderId = params.get("order_id");
        String orderStatus = params.get("order_status");
        String trackingId = params.get("tracking_id");
        String bankRefNo = params.get("bank_ref_no");
        String paymentMode = params.get("payment_mode");
        String amount = params.get("amount");

        auditService.logEvent("FARMER", null, orderId,
                "CALLBACK_RECEIVED", null, orderStatus, null, clientIp,
                "tracking_id=" + trackingId + ", bank_ref_no=" + bankRefNo + ", amount=" + amount);

        try {
            farmerPaymentService.handleCallback(params, clientIp);

            if ("Success".equalsIgnoreCase(orderStatus)) {
                response.sendRedirect(frontendUrl + "/farmer-payment/success?order=" + orderId);
            } else {
                response.sendRedirect(frontendUrl + "/farmer-payment/failed?order=" + orderId);
            }
        } catch (Exception e) {
            log.error("Error processing farmer payment callback for order {}: {}", orderId, e.getMessage());
            response.sendRedirect(frontendUrl + "/farmer-payment/failed?order=" + orderId);
        }
    }

    @PostMapping("/farmer/cancel")
    public void handleFarmerCancel(@RequestParam(value = "encResp", required = false) String encResp,
                                   HttpServletRequest request,
                                   HttpServletResponse response) throws IOException {
        String clientIp = getClientIp(request);
        log.info("Farmer payment cancelled from IP: {}", clientIp);

        if (encResp != null) {
            Map<String, String> params = decryptAndParse(encResp);
            if (params != null) {
                String orderId = params.get("order_id");
                try {
                    params.put("order_status", "Failure");
                    farmerPaymentService.handleCallback(params, clientIp);
                } catch (Exception e) {
                    log.warn("Error processing farmer cancel callback for order {}: {}", orderId, e.getMessage());
                }
                auditService.logEvent("FARMER", null, orderId,
                        "CANCELLED", null, "FAILED", null, clientIp, null);
            }
        }

        response.sendRedirect(frontendUrl + "/farmer-payment/cancelled");
    }

    public Map<String, String> decryptAndParse(String encResp) {
        try {
            String decrypted = CcAvenueUtil.decrypt(encResp, ccAvenueConfig.getWorkingKey());
            return parseResponseString(decrypted);
        } catch (Exception e) {
            log.error("Failed to decrypt CCAvenue response: {}", e.getMessage());
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
            log.warn("Could not extract pending order ID from: {}", orderId);
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

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
