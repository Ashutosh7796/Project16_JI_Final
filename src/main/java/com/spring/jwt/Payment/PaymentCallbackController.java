package com.spring.jwt.Payment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentCallbackController {

    private final PaymentCallbackQueueService callbackQueueService;

    @Value("${app.url.frontend:https://jiojigreenindia.org}")
    private String frontendUrl;

    @PostMapping("/product/response")
    public void handleProductResponse(@RequestParam("encResp") String encResp,
                                      HttpServletRequest request,
                                      HttpServletResponse response) throws IOException {
        String clientIp = getClientIp(request);
        log.info("Product payment callback received from IP: {}", clientIp);
        PaymentCallbackEnqueueResult enqueued = callbackQueueService.enqueue("PRODUCT", "PRODUCT_RESPONSE", encResp, clientIp);
        response.sendRedirect(frontendUrl + "/payment/processing?cbToken=" + urlEncode(enqueued.statusToken()));
    }


    @RequestMapping(value = "/product/cancel", method = {RequestMethod.GET, RequestMethod.POST})
    public void handleProductCancel(@RequestParam(value = "encResp", required = false) String encResp,
                                    HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        String clientIp = getClientIp(request);
        log.info("Product payment cancelled from IP: {}", clientIp);
        PaymentCallbackEnqueueResult enqueued = callbackQueueService.enqueue("PRODUCT", "PRODUCT_CANCEL", encResp, clientIp);
        response.sendRedirect(frontendUrl + "/payment/cancelled?cbToken=" + urlEncode(enqueued.statusToken()));
    }

    @PostMapping("/checkout/response")
    public void handleCheckoutResponse(@RequestParam("encResp") String encResp,
                                       HttpServletRequest request,
                                       HttpServletResponse response) throws IOException {
        String clientIp = getClientIp(request);
        log.info("Checkout payment callback received from IP: {}", clientIp);
        PaymentCallbackEnqueueResult enqueued = callbackQueueService.enqueue("CHECKOUT", "CHECKOUT_RESPONSE", encResp, clientIp);
        response.sendRedirect(frontendUrl + "/dashboard/payment/return?cbToken=" + urlEncode(enqueued.statusToken()));
    }

    @RequestMapping(value = "/checkout/cancel", method = {RequestMethod.GET, RequestMethod.POST})
    public void handleCheckoutCancel(@RequestParam(value = "encResp", required = false) String encResp,
                                     HttpServletRequest request,
                                     HttpServletResponse response) throws IOException {
        String clientIp = getClientIp(request);
        log.info("Checkout payment cancelled from IP: {}", clientIp);
        PaymentCallbackEnqueueResult enqueued = callbackQueueService.enqueue("CHECKOUT", "CHECKOUT_CANCEL", encResp, clientIp);
        response.sendRedirect(frontendUrl + "/dashboard/payment/return?cbToken=" + urlEncode(enqueued.statusToken()));
    }

    @PostMapping("/farmer/response")
    public void handleFarmerResponse(@RequestParam("encResp") String encResp,
                                     HttpServletRequest request,
                                     HttpServletResponse response) throws IOException {
        String clientIp = getClientIp(request);
        log.info("Farmer payment callback received from IP: {}", clientIp);
        PaymentCallbackEnqueueResult enqueued = callbackQueueService.enqueue("FARMER", "FARMER_RESPONSE", encResp, clientIp);
        response.sendRedirect(frontendUrl + "/farmer-payment/processing?cbToken=" + urlEncode(enqueued.statusToken()));
    }

    @RequestMapping(value = "/farmer/cancel", method = {RequestMethod.GET, RequestMethod.POST})
    public void handleFarmerCancel(@RequestParam(value = "encResp", required = false) String encResp,
                                   HttpServletRequest request,
                                   HttpServletResponse response) throws IOException {
        String clientIp = getClientIp(request);
        log.info("Farmer payment cancelled from IP: {}", clientIp);
        PaymentCallbackEnqueueResult enqueued = callbackQueueService.enqueue("FARMER", "FARMER_CANCEL", encResp, clientIp);
        response.sendRedirect(frontendUrl + "/farmer-payment/cancelled?cbToken=" + urlEncode(enqueued.statusToken()));
    }

    /**
     * Poll callback processing status using an opaque token (returned as {@code cbToken} on redirect from CCAvenue).
     */
    @GetMapping("/queue/status")
    public ResponseEntity<PaymentCallbackQueueStatusDTO> getQueueStatusByToken(
            @RequestParam("cbToken") String cbToken) {
        return callbackQueueService.getStatusByPublicToken(cbToken)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/queue/{queueId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentCallbackQueueStatusDTO> getQueueStatus(@PathVariable Long queueId) {
        return callbackQueueService.getStatus(queueId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String getClientIp(HttpServletRequest request)
    {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
