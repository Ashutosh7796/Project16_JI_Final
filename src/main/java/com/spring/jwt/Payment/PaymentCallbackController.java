package com.spring.jwt.Payment;

import com.spring.jwt.checkout.CheckoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentCallbackController {

    private final PaymentCallbackQueueService callbackQueueService;
    private final CheckoutService checkoutService;
    private final PaymentCallbackClientIpResolver clientIpResolver;
    private final PaymentCallbackRateLimiter rateLimiter;

    private static final String DEFAULT_FRONTEND_URL = "https://jiojigreenindia.org";

    /**
     * P3.3: validated on startup so a misconfigured deploy can't silently 302 users to a
     * placeholder hostname. Falls back to the production default to keep dev startups alive,
     * but logs an INFO so the team notices missing per-environment config.
     */
    @Value("${app.url.frontend:" + DEFAULT_FRONTEND_URL + "}")
    private String frontendUrl;

    @jakarta.annotation.PostConstruct
    void validateFrontendUrl() {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            throw new IllegalStateException(
                    "app.url.frontend cannot be blank — refusing to start with no callback redirect target");
        }
        if (!frontendUrl.startsWith("https://") && !frontendUrl.startsWith("http://")) {
            throw new IllegalStateException("app.url.frontend must be an absolute http(s) URL: " + frontendUrl);
        }
        if (DEFAULT_FRONTEND_URL.equals(frontendUrl)) {
            log.info("CONFIG: app.url.frontend not set — using default {}", DEFAULT_FRONTEND_URL);
        }
    }

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
                                     @RequestParam(value = "cmref", required = false) String cmref,
                                     HttpServletRequest request,
                                     HttpServletResponse response) throws IOException {
        String clientIp = getClientIp(request);
        log.info("Checkout payment cancelled from IP: {}", clientIp);
        // P1.1: only honour the merchantOrderId hint when its HMAC verifies. Anything malformed
        // / expired / spoofed is silently dropped so cancel resolution falls back to TTL expiry.
        String knownMerchantOrderId = null;
        if (cmref != null && !cmref.isBlank()) {
            Optional<String> verified = checkoutService.verifyCancelHint(cmref);
            if (verified.isPresent()) {
                knownMerchantOrderId = verified.get();
            } else {
                log.warn("checkout cancel: ignoring invalid/expired cmref hint from ip={}", clientIp);
            }
        }
        PaymentCallbackEnqueueResult enqueued =
                callbackQueueService.enqueue("CHECKOUT", "CHECKOUT_CANCEL", encResp, clientIp, knownMerchantOrderId);
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

    /** P2.4: dead-letter counter for ops dashboards. */
    @GetMapping("/queue/dead-letter/count")
    @PreAuthorize("hasRole('ADMIN')")
    public java.util.Map<String, Long> deadLetterCount() {
        return java.util.Map.of("total", PaymentCallbackProcessingService.deadLetterCount());
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * P2.1: trust X-Forwarded-For only when the immediate hop is a configured trusted proxy,
     * apply per-IP rate limit, and (when configured) gate by an IP allow-list. All defaults are
     * permissive so this can be turned on per environment via {@code app.payment.callback.*}.
     */
    private String getClientIp(HttpServletRequest request) {
        String resolved = clientIpResolver.resolve(request);
        if (!clientIpResolver.isAllowedCallbackSource(resolved)) {
            log.warn("PAYMENT-CALLBACK-IP-DENIED ip={} path={}", resolved, request.getRequestURI());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "callback source not allowed");
        }
        if (!rateLimiter.tryAcquire(resolved)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "callback rate limit exceeded");
        }
        return resolved;
    }

}
