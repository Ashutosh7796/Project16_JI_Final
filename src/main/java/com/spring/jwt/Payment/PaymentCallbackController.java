package com.spring.jwt.Payment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

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
        Long queueId = callbackQueueService.enqueue("PRODUCT", "PRODUCT_RESPONSE", encResp, clientIp);
        response.sendRedirect(frontendUrl + "/payment/processing?queueId=" + queueId);
    }


    @PostMapping("/product/cancel")
    public void handleProductCancel(@RequestParam(value = "encResp", required = false) String encResp,
                                    HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        String clientIp = getClientIp(request);
        log.info("Product payment cancelled from IP: {}", clientIp);
        Long queueId = callbackQueueService.enqueue("PRODUCT", "PRODUCT_CANCEL", encResp, clientIp);
        response.sendRedirect(frontendUrl + "/payment/cancelled?queueId=" + queueId);
    }

    @PostMapping("/farmer/response")
    public void handleFarmerResponse(@RequestParam("encResp") String encResp,
                                     HttpServletRequest request,
                                     HttpServletResponse response) throws IOException {
        String clientIp = getClientIp(request);
        log.info("Farmer payment callback received from IP: {}", clientIp);
        Long queueId = callbackQueueService.enqueue("FARMER", "FARMER_RESPONSE", encResp, clientIp);
        response.sendRedirect(frontendUrl + "/farmer-payment/processing?queueId=" + queueId);
    }

    @PostMapping("/farmer/cancel")
    public void handleFarmerCancel(@RequestParam(value = "encResp", required = false) String encResp,
                                   HttpServletRequest request,
                                   HttpServletResponse response) throws IOException {
        String clientIp = getClientIp(request);
        log.info("Farmer payment cancelled from IP: {}", clientIp);
        Long queueId = callbackQueueService.enqueue("FARMER", "FARMER_CANCEL", encResp, clientIp);
        response.sendRedirect(frontendUrl + "/farmer-payment/cancelled?queueId=" + queueId);
    }

    @GetMapping("/queue/{queueId}")
    public ResponseEntity<PaymentCallbackQueueStatusDTO> getQueueStatus(@PathVariable Long queueId) {
        return callbackQueueService.getStatus(queueId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
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
