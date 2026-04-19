package com.spring.jwt.paymentflow;

import com.spring.jwt.paymentflow.dto.*;
import com.spring.jwt.service.security.UserDetailsCustom;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * REST surface for the payment state machine. Paths are versioned to avoid clashing with legacy
 * {@code /api/payment/**} routes.
 */
@RestController
@RequestMapping("/api/v1/payment-engine")
@RequiredArgsConstructor
public class PaymentFlowController {

    private final PaymentFlowService paymentFlowService;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatePaymentResponse create(
            @Valid @RequestBody CreatePaymentRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return paymentFlowService.create(currentUserId(), body, idempotencyKey);
    }

    @PostMapping("/initiate")
    public InitiatePaymentResponse initiate(
            @Valid @RequestBody InitiatePaymentRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return paymentFlowService.initiate(currentUserId(), body, idempotencyKey);
    }

    /**
     * Unauthenticated abandon: validated with {@code abandonSecret} from {@code create} (safe for
     * {@code navigator.sendBeacon}).
     */
    @PostMapping("/abandon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abandon(@Valid @RequestBody AbandonPaymentRequest body) {
        paymentFlowService.abandon(body);
    }

    /**
     * Gateway callback — signature required. Idempotent by {@code gatewayTransactionId}.
     */
    @PostMapping("/webhook")
    public String webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Payment-Flow-Signature", required = false) String signatureHex) {
        paymentFlowService.handleWebhook(rawBody, signatureHex);
        return "ok";
    }

    @GetMapping("/status/{paymentId}")
    public PaymentStatusResponse status(@PathVariable Long paymentId) {
        return paymentFlowService.status(currentUserId(), paymentId);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsCustom userDetails) {
            return userDetails.getUserId();
        }
        throw new AccessDeniedException("Authenticated user is required");
    }
}
