package com.spring.jwt.checkout;

import com.spring.jwt.checkout.dto.RefundEscalateRequest;
import com.spring.jwt.checkout.dto.RefundEvidenceRequest;
import com.spring.jwt.checkout.dto.RefundManualSuccessRequest;
import com.spring.jwt.checkout.dto.RefundMarkFailedRequest;
import com.spring.jwt.entity.User;
import com.spring.jwt.repository.UserRepository;
import com.spring.jwt.service.security.UserDetailsCustom;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin refund-management surface used by the SPA "Refunds" tab. Returns a fully enriched view
 * (lifecycle status label, partial-refund detection, gateway response excerpt, retry count,
 * actor attribution, audit trail) and exposes the workflow actions: retry, manual success,
 * mark-failed, escalate.
 */
@RestController
@RequestMapping("/api/v1/admin/checkout/refunds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CheckoutRefundAdminController {

    private static final int MAX_LIST_PAGE_SIZE = 200;
    private static final int RAW_RESPONSE_PREVIEW_CHARS = 600;

    private final CheckoutRefundLifecycleService refundLifecycleService;
    private final CheckoutRefundRepository refundRepository;
    private final CheckoutRefundAuditLogRepository refundAuditRepository;
    private final CheckoutGatewayPaymentRepository gatewayPaymentRepository;
    private final UserRepository userRepository;

    /**
     * Enriched listing for the admin Refunds tab. Supports lightweight filtering and paging
     * without requiring a Spring Data {@code Pageable} on every request.
     *
     * @param status            filter by raw {@link CheckoutRefundRecordStatus} (optional)
     * @param failedOnly        when true, returns only FAILED rows (overrides {@code status})
     * @param needsAttention    when true, returns FAILED + retry-stalled rows
     * @param orderId           exact internal order id filter
     * @param merchantOrderId   exact merchant order id filter (case-insensitive contains)
     * @param page / size       paging (size capped at {@link #MAX_LIST_PAGE_SIZE})
     */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean failedOnly,
            @RequestParam(required = false, defaultValue = "false") boolean needsAttention,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String merchantOrderId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {

        int safeSize = Math.max(1, Math.min(MAX_LIST_PAGE_SIZE, size));
        int safePage = Math.max(0, page);

        List<CheckoutRefund> all = refundRepository.findAllByOrderByCreatedAtDesc();

        // P1.5 + UI: pre-resolve per-order summed refund amounts so we can flag PARTIAL accurately.
        Map<Long, BigDecimal> successByOrder = new HashMap<>();
        Map<Long, BigDecimal> totalByOrder = new HashMap<>();
        for (CheckoutRefund r : all) {
            if (r.getOrder() == null) continue;
            Long oid = r.getOrder().getId();
            totalByOrder.computeIfAbsent(oid, k -> r.getOrder().getTotalAmount());
            if (r.getStatus() == CheckoutRefundRecordStatus.SUCCESS) {
                successByOrder.merge(oid, nz(r.getAmount()), BigDecimal::add);
            }
        }

        // Resolve user details per order in one go to avoid N+1 lookups.
        Set<Long> userIds = new HashSet<>();
        for (CheckoutRefund r : all) {
            if (r.getOrder() != null && r.getOrder().getUserId() != null) {
                userIds.add(r.getOrder().getUserId());
            }
        }
        Map<Long, User> usersById = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User u : userRepository.findAllById(userIds)) {
                usersById.put(u.getUserId(), u);
            }
        }

        List<Map<String, Object>> filtered = all.stream()
                .filter(r -> matchesFilters(r, status, failedOnly, needsAttention, orderId, merchantOrderId))
                .map(r -> toListRow(r, successByOrder, totalByOrder, usersById))
                .collect(Collectors.toList());

        int total = filtered.size();
        int from = Math.min(total, safePage * safeSize);
        int to = Math.min(total, from + safeSize);
        List<Map<String, Object>> pageSlice = filtered.subList(from, to);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", pageSlice);
        body.put("page", safePage);
        body.put("size", safeSize);
        body.put("totalElements", total);
        body.put("totalPages", (int) Math.ceil((double) total / safeSize));
        return body;
    }

    /** Full detail for the admin refund detail drawer — includes audit trail + raw gateway preview. */
    @GetMapping("/{refundId}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long refundId) {
        Optional<CheckoutRefund> refundOpt = refundRepository.findByIdWithOrder(refundId);
        if (refundOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CheckoutRefund r = refundOpt.get();

        Map<Long, BigDecimal> successByOrder = new HashMap<>();
        Map<Long, BigDecimal> totalByOrder = new HashMap<>();
        if (r.getOrder() != null) {
            Long oid = r.getOrder().getId();
            totalByOrder.put(oid, r.getOrder().getTotalAmount());
            successByOrder.put(oid, nz(refundRepository.sumSuccessfulAmountByOrderId(oid)));
        }
        Map<Long, User> usersById = new HashMap<>();
        if (r.getOrder() != null && r.getOrder().getUserId() != null) {
            userRepository.findById(r.getOrder().getUserId()).ifPresent(u -> usersById.put(u.getUserId(), u));
        }
        Map<String, Object> base = toListRow(r, successByOrder, totalByOrder, usersById);

        // Full raw payloads (truncated for safety) — only returned in the detail view, not in list.
        base.put("rawResponse", truncate(r.getRawResponse(), 16_000));
        base.put("requestPayload", truncate(r.getRequestPayload(), 8_000));

        // Audit trail
        List<Map<String, Object>> audit = refundAuditRepository.findByRefundIdOrderByCreatedAtAsc(refundId).stream()
                .map(a -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", a.getId());
                    row.put("action", a.getAction());
                    row.put("adminId", a.getAdminId());
                    row.put("details", truncate(a.getDetails(), 4_000));
                    row.put("createdAt", a.getCreatedAt());
                    return row;
                })
                .collect(Collectors.toList());
        base.put("auditTrail", audit);

        // All gateway-payment rows for the same order help admins correlate trackingId / paymentId.
        if (r.getOrder() != null) {
            List<Map<String, Object>> payments = gatewayPaymentRepository
                    .findByOrder_IdOrderByIdDesc(r.getOrder().getId())
                    .stream()
                    .map(p -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("paymentId", p.getId());
                        row.put("trackingId", p.getTrackingId());
                        row.put("amount", p.getAmount());
                        row.put("status", p.getStatus() != null ? p.getStatus().name() : null);
                        row.put("paymentMode", p.getPaymentMode());
                        row.put("createdAt", p.getCreatedAt());
                        return row;
                    })
                    .collect(Collectors.toList());
            base.put("relatedPayments", payments);
        }

        return ResponseEntity.ok(base);
    }

    @PostMapping("/{refundId}/manual-success")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void manualSuccess(@PathVariable Long refundId,
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
    public void markFailed(@PathVariable Long refundId,
                           @Valid @RequestBody RefundMarkFailedRequest body) {
        refundLifecycleService.adminMarkFailedFinal(refundId, currentAdminId(), body.getNotes());
    }

    @PatchMapping("/{refundId}/evidence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void evidence(@PathVariable Long refundId,
                         @RequestBody RefundEvidenceRequest body) {
        refundLifecycleService.adminAddEvidence(
                refundId,
                currentAdminId(),
                body.getSupportTicketId(),
                body.getBankReference(),
                body.getAdminNotes());
    }

    /** Escalate a refund: stamps support ticket id + ESCALATED note + audit row. */
    @PostMapping("/{refundId}/escalate")
    public Map<String, Object> escalate(@PathVariable Long refundId,
                                        @Valid @RequestBody(required = false) RefundEscalateRequest body) {
        String supportTicketId = body != null ? body.getSupportTicketId() : null;
        String notes = body != null ? body.getNotes() : null;
        String ticketId = refundLifecycleService.adminEscalate(refundId, currentAdminId(), supportTicketId, notes);
        return Map.of("supportTicketId", ticketId);
    }

    private boolean matchesFilters(CheckoutRefund r,
                                   String status,
                                   boolean failedOnly,
                                   boolean needsAttention,
                                   Long orderId,
                                   String merchantOrderId) {
        CheckoutRefundRecordStatus rs = r.getStatus();
        if (failedOnly && rs != CheckoutRefundRecordStatus.FAILED) return false;
        if (needsAttention) {
            boolean failedUnverified = rs == CheckoutRefundRecordStatus.FAILED && r.getAdminId() == null;
            boolean stalled = (rs == CheckoutRefundRecordStatus.INITIATED
                    || rs == CheckoutRefundRecordStatus.PENDING_GATEWAY)
                    && r.getRetryCount() != null && r.getRetryCount() >= 3;
            if (!failedUnverified && !stalled) return false;
        }
        if (status != null && !status.isBlank()) {
            try {
                if (rs != CheckoutRefundRecordStatus.valueOf(status.trim().toUpperCase())) return false;
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        if (orderId != null && (r.getOrder() == null || !orderId.equals(r.getOrder().getId()))) {
            return false;
        }
        if (merchantOrderId != null && !merchantOrderId.isBlank()) {
            if (r.getOrder() == null || r.getOrder().getMerchantOrderId() == null) return false;
            if (!r.getOrder().getMerchantOrderId().toLowerCase().contains(merchantOrderId.trim().toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> toListRow(CheckoutRefund r,
                                          Map<Long, BigDecimal> successByOrder,
                                          Map<Long, BigDecimal> totalByOrder,
                                          Map<Long, User> usersById) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        Long oid = r.getOrder() != null ? r.getOrder().getId() : null;
        m.put("orderId", oid);
        m.put("merchantOrderId", r.getOrder() != null ? r.getOrder().getMerchantOrderId() : null);
        m.put("orderTotalAmount", r.getOrder() != null ? r.getOrder().getTotalAmount() : null);
        m.put("amount", r.getAmount());
        m.put("reason", r.getReason());

        String rawStatus = r.getStatus() != null ? r.getStatus().name() : "UNKNOWN";
        boolean partial = false;
        if (oid != null && r.getOrder() != null && r.getOrder().getTotalAmount() != null) {
            BigDecimal succ = nz(successByOrder.get(oid));
            BigDecimal tot = nz(totalByOrder.get(oid));
            if (succ.compareTo(BigDecimal.ZERO) > 0 && succ.compareTo(tot) < 0) {
                partial = true;
            }
        }
        m.put("status", rawStatus);
        m.put("lifecycleStatus", lifecycleLabel(r, partial));
        m.put("isPartial", partial);
        m.put("gatewayReference", r.getGatewayReference());
        m.put("ccaTrackingId", r.getCcaTrackingId());
        m.put("supportTicketId", r.getSupportTicketId());
        m.put("bankReference", r.getBankReference());
        m.put("retryCount", r.getRetryCount() == null ? 0 : r.getRetryCount());
        m.put("requestedAt", r.getCreatedAt());
        m.put("processedAt", r.getStatus() == CheckoutRefundRecordStatus.SUCCESS
                ? Optional.<LocalDateTime>ofNullable(r.getUpdatedAt()).orElse(r.getCreatedAt())
                : null);
        m.put("lastCheckedAt", r.getLastCheckedAt());
        m.put("isManual", Boolean.TRUE.equals(r.getIsManual()));
        m.put("initiatedBy", initiatedByLabel(r));
        m.put("adminId", r.getAdminId());
        m.put("adminNotes", r.getAdminNotes());
        m.put("failureReason", r.getStatus() == CheckoutRefundRecordStatus.FAILED
                ? deriveFailureReason(r) : null);
        m.put("gatewayResponseExcerpt", truncate(r.getRawResponse(), RAW_RESPONSE_PREVIEW_CHARS));
        m.put("escalated", isEscalated(r));

        if (r.getOrder() != null && r.getOrder().getUserId() != null) {
            User u = usersById.get(r.getOrder().getUserId());
            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("userId", r.getOrder().getUserId());
            if (u != null) {
                userInfo.put("email", u.getEmail());
                userInfo.put("name", trimName(u.getFirstName(), u.getLastName()));
            }
            m.put("user", userInfo);
        }
        return m;
    }

    /**
     * Maps the 4-state DB enum + signals (retryCount, partial, escalated) to the richer lifecycle
     * vocabulary the UI displays.
     */
    private static String lifecycleLabel(CheckoutRefund r, boolean partial) {
        CheckoutRefundRecordStatus s = r.getStatus();
        if (s == null) return "REFUND_UNKNOWN";
        int retries = r.getRetryCount() == null ? 0 : r.getRetryCount();
        return switch (s) {
            case SUCCESS -> partial ? "PARTIAL_REFUND" : "REFUND_SUCCESS";
            case FAILED -> "REFUND_FAILED";
            case INITIATED -> retries > 0 ? "REFUND_RETRYING" : "REFUND_PROCESSING";
            case PENDING_GATEWAY -> retries > 0 ? "REFUND_RETRYING" : "REFUND_PENDING";
        };
    }

    /** {@code system} = automated lifecycle; {@code admin} = manual flag set; {@code user} reserved for self-serve. */
    private static String initiatedByLabel(CheckoutRefund r) {
        if (Boolean.TRUE.equals(r.getIsManual()) || r.getAdminId() != null) return "admin";
        return "system";
    }

    private static boolean isEscalated(CheckoutRefund r) {
        if (r.getSupportTicketId() != null && !r.getSupportTicketId().isBlank()) return true;
        return r.getAdminNotes() != null && r.getAdminNotes().contains("[ESCALATED");
    }

    private static String deriveFailureReason(CheckoutRefund r) {
        if (r.getAdminNotes() != null && !r.getAdminNotes().isBlank()) return r.getAdminNotes();
        if (r.getReason() != null && !r.getReason().isBlank()) return r.getReason();
        return truncate(r.getRawResponse(), 280);
    }

    private static String trimName(String first, String last) {
        StringBuilder sb = new StringBuilder();
        if (first != null && !first.isBlank()) sb.append(first.trim());
        if (last != null && !last.isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(last.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private Long currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsCustom userDetails) {
            return userDetails.getUserId();
        }
        throw new AccessDeniedException("Admin user required");
    }
}
