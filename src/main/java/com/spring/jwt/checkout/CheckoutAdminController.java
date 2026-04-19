package com.spring.jwt.checkout;

import com.spring.jwt.checkout.dto.AdminCancelRequest;
import com.spring.jwt.checkout.dto.CheckoutOrderResponse;
import com.spring.jwt.checkout.dto.RefundManualSuccessRequest;
import com.spring.jwt.service.security.UserDetailsCustom;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/checkout/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CheckoutAdminController {

    private final CheckoutService checkoutService;

    /** List ALL checkout orders (any user, any status) for admin dashboard. */
    @GetMapping
    public List<CheckoutOrderResponse> listAllOrders(
            @RequestParam(name = "limit", defaultValue = "200") int limit) {
        return checkoutService.adminListAllOrders(limit);
    }

    @PostMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adminCancel(
            @PathVariable Long orderId,
            @RequestBody(required = false) AdminCancelRequest body) {
        String reason = body != null ? body.getReason() : null;
        checkoutService.adminCancelOrder(orderId, currentAdminId(), reason);
    }

    @PostMapping("/{orderId}/mark-refunded")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRefunded(
            @PathVariable Long orderId,
            @Valid @RequestBody RefundManualSuccessRequest body) {
        checkoutService.adminMarkRefunded(orderId, currentAdminId(), body.getNotes());
    }

    private Long currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsCustom userDetails) {
            return userDetails.getUserId();
        }
        throw new AccessDeniedException("Admin user required");
    }
}
