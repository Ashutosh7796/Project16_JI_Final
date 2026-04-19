package com.spring.jwt.checkout;

import com.spring.jwt.checkout.dto.RefundEvidenceRequest;
import com.spring.jwt.checkout.dto.RefundManualSuccessRequest;
import com.spring.jwt.checkout.dto.RefundMarkFailedRequest;
import com.spring.jwt.service.security.UserDetailsCustom;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/checkout/refunds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CheckoutRefundAdminController {

    private final CheckoutRefundLifecycleService refundLifecycleService;
    private final CheckoutRefundRepository refundRepository;

    @GetMapping
    public java.util.List<java.util.Map<String, Object>> getAllRefunds() {
        return refundRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(r -> {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", r.getId());
                m.put("orderId", r.getOrder() != null ? r.getOrder().getId() : null);
                m.put("merchantOrderId", r.getOrder() != null ? r.getOrder().getMerchantOrderId() : null);
                m.put("amount", r.getAmount());
                m.put("reason", r.getReason());
                m.put("status", r.getStatus() != null ? r.getStatus().name() : null);
                m.put("gatewayReference", r.getGatewayReference());
                m.put("ccaTrackingId", r.getCcaTrackingId());
                m.put("createdAt", r.getCreatedAt());
                m.put("isManual", r.getIsManual());
                m.put("adminNotes", r.getAdminNotes());
                return m;
            })
            .collect(java.util.stream.Collectors.toList());
    }

    @PostMapping("/{refundId}/manual-success")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void manualSuccess(
            @PathVariable Long refundId,
            @Valid @RequestBody RefundManualSuccessRequest body) {
        refundLifecycleService.adminManualSuccess(refundId, currentAdminId(), body.getNotes());
    }

    @PostMapping("/{refundId}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retry(@PathVariable Long refundId) {
        refundLifecycleService.adminRetryGatewayRefund(refundId, currentAdminId());
    }

    @PostMapping("/{refundId}/mark-failed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markFailed(
            @PathVariable Long refundId,
            @Valid @RequestBody RefundMarkFailedRequest body) {
        refundLifecycleService.adminMarkFailedFinal(refundId, currentAdminId(), body.getNotes());
    }

    @PatchMapping("/{refundId}/evidence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void evidence(
            @PathVariable Long refundId,
            @RequestBody RefundEvidenceRequest body) {
        refundLifecycleService.adminAddEvidence(
                refundId,
                currentAdminId(),
                body.getSupportTicketId(),
                body.getBankReference(),
                body.getAdminNotes());
    }

    private Long currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsCustom userDetails) {
            return userDetails.getUserId();
        }
        throw new AccessDeniedException("Admin user required");
    }
}
