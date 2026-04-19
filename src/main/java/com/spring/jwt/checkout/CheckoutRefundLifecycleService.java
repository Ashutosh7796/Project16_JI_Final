package com.spring.jwt.checkout;

import com.fasterxml.jackson.databind.JsonNode;
import com.spring.jwt.checkout.CcAvenueRefundClient.RefundApiResult;
import com.spring.jwt.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutRefundLifecycleService {

    private CheckoutRefundLifecycleService self;

    @Autowired
    public void setSelf(@Lazy CheckoutRefundLifecycleService self) {
        this.self = self;
    }

    private final CheckoutRefundRepository refundRepository;
    private final CheckoutOrderRepository orderRepository;
    private final CcAvenueRefundClient ccAvenueRefundClient;
    private final CcAvenueOrderStatusClient ccAvenueOrderStatusClient;
    private final CheckoutRefundAuditService refundAuditService;
    private final CheckoutProperties checkoutProperties;

    /**
     * First refund API attempt (also used for admin retry with {@code forceRetry = true}).
     */
    @Transactional
    public void initiateRefundApi(Long refundId, boolean forceRetry) {
        CheckoutRefund refund = refundRepository.findByIdForUpdateWithOrder(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));

        if (refund.getStatus() == CheckoutRefundRecordStatus.SUCCESS
                || refund.getStatus() == CheckoutRefundRecordStatus.FAILED) {
            return;
        }
        if (!forceRetry && refund.getStatus() == CheckoutRefundRecordStatus.INITIATED) {
            return;
        }

        String tracking = refund.getCcaTrackingId();
        if (tracking == null || tracking.isBlank()) {
            refund.setStatus(CheckoutRefundRecordStatus.PENDING_GATEWAY);
            appendRawResponse(refund, "NO_CCA_TRACKING_ID");
            refundRepository.save(refund);
            refundAuditService.log(refundId, null, "REFUND_INIT_SKIPPED", "Missing CCAvenue tracking id");
            return;
        }

        String refundRefNo = refundRefNo(refund);
        RefundApiResult api = ccAvenueRefundClient.submitRefund(tracking, refund.getAmount(), refundRefNo);
        refund.setRequestPayload(api.requestJson());
        appendRawResponse(refund, api.rawCombined());

        if (api.acceptedByGateway()) {
            refund.setStatus(CheckoutRefundRecordStatus.INITIATED);
            refund.setGatewayReference(extractGatewayRef(api.innerJson()));
        } else {
            refund.setStatus(CheckoutRefundRecordStatus.PENDING_GATEWAY);
        }
        refundRepository.save(refund);
        refundAuditService.log(refundId, null,
                api.acceptedByGateway() ? "REFUND_API_INITIATED" : "REFUND_API_PENDING",
                truncate(api.rawCombined(), 4000));
    }

    /**
     * Scheduled reconciliation using CCAvenue order status (primary resolver after refund API).
     */
    public void reconcileOpenRefunds() {
        List<CheckoutRefund> open = refundRepository.findByStatusIn(List.of(
                CheckoutRefundRecordStatus.INITIATED,
                CheckoutRefundRecordStatus.PENDING_GATEWAY));
        for (CheckoutRefund r : open) {
            try {
                self.reconcileOneRefund(r.getId());
            } catch (Exception e) {
                log.warn("reconcile refund {} failed: {}", r.getId(), e.getMessage());
            }
        }
    }

    public void reconcileOneRefund(Long refundId) {
        // First read WITHOUT a lock to check eligibility and get the merchant ORder ID
        CheckoutRefund refundOpt = refundRepository.findById(refundId).orElse(null);
        if (refundOpt == null) return;
        
        if (refundOpt.getStatus() != CheckoutRefundRecordStatus.INITIATED
                && refundOpt.getStatus() != CheckoutRefundRecordStatus.PENDING_GATEWAY) {
            return;
        }
        int max = checkoutProperties.getRefund().getMaxAutoRetries();
        if (refundOpt.getRetryCount() != null && refundOpt.getRetryCount() >= max) {
            return;
        }
        if (!eligibleForReconcile(refundOpt)) {
            return;
        }

        // NO LOCK HELD: Make the slow gateway API call
        String merchantOrderId = refundOpt.getOrder().getMerchantOrderId();
        Optional<JsonNode> statusJson = Optional.empty();
        try {
            statusJson = ccAvenueOrderStatusClient.fetchDecryptedOrderStatusJson(merchantOrderId);
        } catch (Exception e) {
            log.warn("ccAvenueOrderStatusClient fetch failed for refund {}: {}", refundId, e.getMessage());
        }

        // Apply state transitions under lock
        self.applyReconciledStatus(refundId, statusJson);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyReconciledStatus(Long refundId, Optional<JsonNode> statusJson) {
        CheckoutRefund refund = refundRepository.findByIdForUpdateWithOrder(refundId)
                .orElse(null);
        if (refund == null) return;
        
        // Re-verify after gaining lock to avoid race conditions
        if (refund.getStatus() != CheckoutRefundRecordStatus.INITIATED
                && refund.getStatus() != CheckoutRefundRecordStatus.PENDING_GATEWAY) {
            return;
        }

        int nextRetry = Optional.ofNullable(refund.getRetryCount()).orElse(0) + 1;
        refund.setRetryCount(nextRetry);
        refund.setLastCheckedAt(LocalDateTime.now());

        if (statusJson.isEmpty()) {
            refundRepository.save(refund);
            refundAuditService.log(refundId, null, "REFUND_RECONCILE_EMPTY_STATUS", null);
            return;
        }

        RefundOrderStatusInterpreter.Hint hint = RefundOrderStatusInterpreter.infer(statusJson.get(), refundRefNo(refund));
        if (hint == RefundOrderStatusInterpreter.Hint.SUCCESS) {
            applySuccess(refund, false, null, null);
        } else if (hint == RefundOrderStatusInterpreter.Hint.FAILED) {
            refund.setStatus(CheckoutRefundRecordStatus.FAILED);
            refundRepository.save(refund);
            refundAuditService.log(refundId, null, "REFUND_RECONCILE_FAILED", statusJson.get().toString());
        } else {
            refundRepository.save(refund);
            refundAuditService.log(refundId, null, "REFUND_RECONCILE_UNKNOWN", statusJson.get().toString());
        }
    }

    @Transactional
    public void adminManualSuccess(Long refundId, Long adminId, String notes) {
        CheckoutRefund refund = refundRepository.findByIdForUpdateWithOrder(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));
        if (refund.getStatus() == CheckoutRefundRecordStatus.SUCCESS) {
            return;
        }
        applySuccess(refund, true, adminId, notes);
        refundAuditService.log(refundId, adminId, "REFUND_ADMIN_MANUAL_SUCCESS", notes);
    }

    @Transactional
    public void adminRetryGatewayRefund(Long refundId, Long adminId) {
        CheckoutRefund refund = refundRepository.findByIdForUpdateWithOrder(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));
        if (refund.getStatus() == CheckoutRefundRecordStatus.SUCCESS
                || refund.getStatus() == CheckoutRefundRecordStatus.FAILED) {
            throw new IllegalStateException("Cannot retry refund in status " + refund.getStatus());
        }
        initiateRefundApi(refundId, true);
        refundAuditService.log(refundId, adminId, "REFUND_ADMIN_RETRY_API", null);
    }

    @Transactional
    public void adminMarkFailedFinal(Long refundId, Long adminId, String notes) {
        CheckoutRefund refund = refundRepository.findByIdForUpdateWithOrder(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));
        if (refund.getStatus() == CheckoutRefundRecordStatus.SUCCESS) {
            throw new IllegalStateException("Cannot mark SUCCESS refund as FAILED");
        }
        refund.setStatus(CheckoutRefundRecordStatus.FAILED);
        refund.setAdminId(adminId);
        refund.setAdminNotes(notes);
        refund.setIsManual(Boolean.TRUE);
        refundRepository.save(refund);
        refundAuditService.log(refundId, adminId, "REFUND_ADMIN_MARK_FAILED", notes);
    }

    @Transactional
    public void adminAddEvidence(Long refundId, Long adminId, String supportTicketId, String bankReference, String adminNotes) {
        CheckoutRefund refund = refundRepository.findByIdForUpdateWithOrder(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found: " + refundId));
        if (supportTicketId != null && !supportTicketId.isBlank()) {
            refund.setSupportTicketId(supportTicketId.trim());
        }
        if (bankReference != null && !bankReference.isBlank()) {
            refund.setBankReference(bankReference.trim());
        }
        if (adminNotes != null && !adminNotes.isBlank()) {
            refund.setAdminNotes(adminNotes.trim());
        }
        refund.setAdminId(adminId);
        refundRepository.save(refund);
        refundAuditService.log(refundId, adminId, "REFUND_ADMIN_EVIDENCE", supportTicketId + " / " + bankReference);
    }

    @Transactional
    public void adminMarkOrderRefundedLegacy(Long orderId, Long adminId, String notes) {
        CheckoutRefund refund = refundRepository.findFirstByOrder_IdOrderByIdDesc(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No refund rows for order " + orderId));
        adminManualSuccess(refund.getId(), adminId, notes != null ? notes : "legacy order mark-refunded");
    }

    private void applySuccess(CheckoutRefund refund, boolean manual, Long adminId, String notes) {
        refund.setStatus(CheckoutRefundRecordStatus.SUCCESS);
        if (manual) {
            refund.setIsManual(true);
            refund.setAdminId(adminId);
            refund.setAdminNotes(notes);
        }
        refundRepository.save(refund);
        refundRepository.flush();
        maybeMarkOrderFullyRefunded(refund.getOrder().getId());
    }

    private void maybeMarkOrderFullyRefunded(Long orderId) {
        orderRepository.findByIdForUpdate(orderId).ifPresent(order -> {
            if (order.getStatus() != CheckoutOrderStatus.PAID) {
                return;
            }
            BigDecimal sum = refundRepository.sumSuccessfulAmountByOrderId(orderId);
            if (sum.compareTo(order.getTotalAmount()) >= 0) {
                order.setStatus(CheckoutOrderStatus.REFUNDED);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);
            }
        });
    }

    private boolean eligibleForReconcile(CheckoutRefund refund) {
        if (refund.getLastCheckedAt() == null) {
            return true;
        }
        long waitMinutes = backoffMinutes(Optional.ofNullable(refund.getRetryCount()).orElse(0));
        return !refund.getLastCheckedAt().plusMinutes(waitMinutes).isAfter(LocalDateTime.now());
    }

    private long backoffMinutes(int completedReconciliationsBeforeThisRun) {
        CheckoutProperties.RefundSettings r = checkoutProperties.getRefund();
        if (completedReconciliationsBeforeThisRun <= 0) {
            return 0;
        }
        long exp = (long) r.getBaseBackoffMinutes() * (1L << Math.min(completedReconciliationsBeforeThisRun, 10));
        return Math.min(r.getMaxBackoffMinutes(), exp);
    }

    private static String refundRefNo(CheckoutRefund refund) {
        return "REF-" + refund.getId();
    }

    private static void appendRawResponse(CheckoutRefund refund, String chunk) {
        String cur = refund.getRawResponse();
        String next = (cur == null || cur.isBlank()) ? chunk : cur + "\n---\n" + chunk;
        refund.setRawResponse(truncate(next, 12000));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String extractGatewayRef(JsonNode inner) {
        if (inner == null) {
            return null;
        }
        for (String k : new String[]{"reference_no", "refund_ref_no", "tracking_id", "nb_ref_no"}) {
            if (inner.has(k) && !inner.get(k).isNull()) {
                String v = inner.get(k).asText("");
                if (!v.isBlank()) {
                    return v;
                }
            }
        }
        return null;
    }
}
