package com.spring.jwt.checkout;

import com.spring.jwt.checkout.dto.CheckoutOrderResponse;
import com.spring.jwt.checkout.dto.CreateCheckoutOrderRequest;
import com.spring.jwt.checkout.dto.CustomerRefundRequest;
import com.spring.jwt.checkout.dto.InitiateCheckoutPaymentResponse;
import com.spring.jwt.service.security.UserDetailsCustom;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutOrderResponse createOrder(
            @Valid @RequestBody CreateCheckoutOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return checkoutService.createOrder(currentUserId(), request, idempotencyKey);
    }

    @PostMapping("/orders/{orderId}/initiate-payment")
    public InitiateCheckoutPaymentResponse initiatePayment(
            @PathVariable Long orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return checkoutService.initiatePayment(currentUserId(), orderId, idempotencyKey);
    }

    @GetMapping("/orders")
    public List<CheckoutOrderResponse> listMyCheckoutOrders(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return checkoutService.listMyCheckoutOrders(currentUserId(), limit);
    }

    @GetMapping("/orders/{orderId}")
    public CheckoutOrderResponse getOrder(@PathVariable Long orderId) {
        checkoutService.syncPaymentStatusIfPending(currentUserId(), orderId);
        return checkoutService.getOrder(currentUserId(), orderId);
    }

    @PostMapping("/orders/{orderId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long orderId) {
        checkoutService.cancelOrder(currentUserId(), orderId);
    }

    /**
     * Called by navigator.sendBeacon when the user closes the tab or navigates away
     * during payment. Accepts text/plain (sendBeacon default) or JSON.
     * Idempotent: safe to call multiple times or on already-resolved orders.
     */
    @PostMapping(value = "/orders/{orderId}/abandon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abandon(@PathVariable Long orderId) {
        checkoutService.abandonOrder(currentUserId(), orderId);
    }

    /**
     * Unauthenticated abandon via {@code navigator.sendBeacon}. Validated by {@code abandonToken}
     * (random secret returned from {@code initiatePayment}) instead of JWT. Fire-and-forget safe: always returns 204.
     */
    @PostMapping(value = "/orders/abandon-beacon", consumes = {"application/json", "text/plain"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abandonBeacon(@RequestBody String rawBody) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(rawBody);
            Long orderId = node.has("orderId") ? node.get("orderId").asLong() : null;
            String token = node.has("abandonToken") ? node.get("abandonToken").asText() : null;
            if (orderId != null && token != null) {
                checkoutService.abandonOrderByToken(orderId, token);
            }
        } catch (Exception ignored) {
            // sendBeacon fire-and-forget — never fail
        }
    }

    @PostMapping("/orders/{orderId}/refunds")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestRefund(
            @PathVariable Long orderId,
            @RequestBody(required = false) CustomerRefundRequest body) {
        String reason = body != null ? body.getReason() : null;
        checkoutService.requestCustomerRefund(currentUserId(), orderId, reason);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsCustom userDetails) {
            return userDetails.getUserId();
        }
        throw new AccessDeniedException("Authenticated user is required");
    }
}
