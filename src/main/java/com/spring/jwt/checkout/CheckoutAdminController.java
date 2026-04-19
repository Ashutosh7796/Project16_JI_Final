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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/checkout/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CheckoutAdminController {

    private final CheckoutService checkoutService;
    private final CheckoutGatewayPaymentRepository gatewayPaymentRepository;
    private final CheckoutOrderRepository checkoutOrderRepository;

    /** List ALL checkout orders (any user, any status) for admin dashboard. */
    @GetMapping
    public List<CheckoutOrderResponse> listAllOrders(
            @RequestParam(name = "limit", defaultValue = "200") int limit) {
        return checkoutService.adminListAllOrders(limit);
    }

    @GetMapping("/{orderId}")
    public CheckoutOrderResponse getOrderById(@PathVariable Long orderId) {
        return checkoutService.adminGetOrder(orderId);
    }

    @PostMapping("/{orderId}/fulfill")
    public CheckoutOrderResponse fulfillOrder(@PathVariable Long orderId) {
        return checkoutService.adminFulfillOrder(orderId, currentAdminId());
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

    /** Flat list of ALL gateway payment hits for the admin Financial Reconciliation panel. */
    @GetMapping("/payments/logs")
    public List<Map<String, Object>> getPaymentLogs() {
        return gatewayPaymentRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(gp -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", gp.getId());
                m.put("orderId", gp.getOrder() != null ? gp.getOrder().getId() : null);
                m.put("trackingId", gp.getTrackingId());
                m.put("gatewayOrderId", gp.getGatewayOrderId());
                m.put("amount", gp.getAmount());
                m.put("currency", gp.getCurrency());
                m.put("status", gp.getStatus() != null ? gp.getStatus().name() : null);
                m.put("paymentMode", gp.getPaymentMode());
                m.put("statusMessage", gp.getStatusMessage());
                m.put("clientIp", gp.getClientIp());
                m.put("createdAt", gp.getCreatedAt());
                return m;
            })
            .collect(Collectors.toList());
    }

    @GetMapping("/analytics/sales-trends")
    public List<Map<String, Object>> getSalesTrends() {
        return checkoutOrderRepository.findSalesTrends();
    }

    private Long currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsCustom userDetails) {
            return userDetails.getUserId();
        }
        throw new AccessDeniedException("Admin user required");
    }
}
