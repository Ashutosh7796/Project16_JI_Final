package com.spring.jwt.checkout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.jwt.Payment.CcAvenueConfig;
import com.spring.jwt.Payment.CcAvenuePaymentRequest;
import com.spring.jwt.Payment.CcAvenuePaymentService;
import com.spring.jwt.Product.ProductRepository;
import com.spring.jwt.checkout.dto.*;
import com.spring.jwt.entity.Product;
import com.spring.jwt.exception.ResourceNotFoundException;
import com.spring.jwt.useraddress.UserSavedAddress;
import com.spring.jwt.useraddress.UserSavedAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutServiceImpl implements CheckoutService {

    private static final BigDecimal PRICE_TOLERANCE = new BigDecimal("0.02");

    private CheckoutService self;

    @Autowired
    public void setSelf(@Lazy CheckoutService self) {
        this.self = self;
    }

    private final CheckoutOrderRepository orderRepository;
    private final CheckoutReservationRepository reservationRepository;
    private final CheckoutGatewayPaymentRepository gatewayPaymentRepository;
    private final CheckoutPaymentEventRepository paymentEventRepository;
    private final CheckoutRefundRepository refundRepository;
    private final ProductRepository productRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final CcAvenuePaymentService ccAvenuePaymentService;
    private final CcAvenueConfig ccAvenueConfig;
    private final CheckoutProperties checkoutProperties;
    private final CcAvenueOrderStatusClient ccAvenueOrderStatusClient;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final CheckoutRefundLifecycleService checkoutRefundLifecycleService;
    private final UserSavedAddressRepository userSavedAddressRepository;

    @Override
    @Transactional
    public CheckoutOrderResponse createOrder(Long userId, CreateCheckoutOrderRequest request, String checkoutIdempotencyKey) {
        if (checkoutIdempotencyKey != null && !checkoutIdempotencyKey.isBlank()) {
            Optional<CheckoutOrder> existing = orderRepository.findByUserIdAndCheckoutIdempotencyKey(userId, checkoutIdempotencyKey.trim());
            if (existing.isPresent()) {
                return mapOrder(existing.get());
            }
        }

        Map<Long, Product> productsById = loadProducts(request);
        List<CheckoutOrderLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        StringBuilder hashInput = new StringBuilder();

        for (CheckoutLineRequest lineReq : request.getLines()) {
            Product p = productsById.get(lineReq.getProductId());
            BigDecimal unit = unitSellingPrice(p);
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(lineReq.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            total = total.add(lineTotal);
            hashInput.append(p.getProductId()).append(':').append(unit).append(':').append(lineReq.getQuantity()).append('|');

            validateAvailabilitySoft(p, lineReq.getQuantity());

            CheckoutOrderLine line = CheckoutOrderLine.builder()
                    .productId(p.getProductId())
                    .quantity(lineReq.getQuantity())
                    .unitPriceSnapshot(unit)
                    .lineTotal(lineTotal)
                    .fulfillmentStatus(CheckoutLineFulfillmentStatus.PENDING)
                    .build();
            lines.add(line);
        }

        final String customerName;
        final String contactNumber;
        final String deliveryAddress;
        if (request.getSavedAddressId() != null) {
            UserSavedAddress sa = userSavedAddressRepository.findByIdAndUserId(request.getSavedAddressId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Saved address not found"));
            customerName = sa.getFullName().trim();
            contactNumber = sa.getPhone().trim();
            deliveryAddress = sa.toDeliveryLine();
        } else {
            customerName = request.getCustomerName().trim();
            contactNumber = request.getContactNumber().trim();
            deliveryAddress = request.getDeliveryAddress().trim();
        }

        CheckoutOrder order = CheckoutOrder.builder()
                .userId(userId)
                .status(CheckoutOrderStatus.PENDING)
                .merchantOrderId(tempMerchantId())
                .currency("INR")
                .totalAmount(total)
                .pricingSnapshotHash(sha256Hex(hashInput.toString()))
                .customerName(customerName)
                .contactNumber(contactNumber)
                .deliveryAddress(deliveryAddress)
                .checkoutIdempotencyKey(checkoutIdempotencyKey != null ? checkoutIdempotencyKey.trim() : null)
                .build();

        for (CheckoutOrderLine line : lines) {
            order.addLine(line);
        }

        orderRepository.saveAndFlush(order);
        order.setMerchantOrderId(CheckoutMerchantOrderIds.forOrderId(order.getId()));
        orderRepository.save(order);

        log.info("checkout order created id={} user={} total={}", order.getId(), userId, total);
        return mapOrder(order);
    }

    @Override
    @Transactional
    public InitiateCheckoutPaymentResponse initiatePayment(Long userId, Long orderId, String paymentInitIdempotencyKey) {
        String payKey = (paymentInitIdempotencyKey == null || paymentInitIdempotencyKey.isBlank())
                ? "order-init-" + orderId
                : paymentInitIdempotencyKey.trim();

        Optional<CheckoutOrder> idem = orderRepository.findByUserIdAndPaymentInitIdempotencyKey(userId, payKey);
        if (idem.isPresent() && !idem.get().getId().equals(orderId)) {
            throw new IllegalStateException("Payment idempotency key already used for another order");
        }

        CheckoutOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout order not found"));

        if (!order.getUserId().equals(userId)) {
            throw new SecurityException("Order does not belong to current user");
        }

        if (order.getStatus() == CheckoutOrderStatus.PAYMENT_PENDING
                && payKey.equals(Optional.ofNullable(order.getPaymentInitIdempotencyKey()).orElse(""))
                && order.getReservationExpiresAt() != null
                && order.getReservationExpiresAt().isAfter(LocalDateTime.now())) {
            try {
                return buildInitiateResponse(order);
            } catch (RuntimeException e) {
                registerInitiateBuildFailureCallback(userId, order.getId(), e);
                throw e;
            }
        }

        if (order.getStatus() == CheckoutOrderStatus.FAILED) {
            validateLivePricing(order);
            LocalDateTime expires = LocalDateTime.now().plusMinutes(checkoutProperties.getReservationTtlMinutes());
            reserveInventoryForOrder(order, expires);
            InitiateCheckoutPaymentResponse response;
            try {
                response = buildInitiateResponse(order);
            } catch (RuntimeException e) {
                registerInitiateBuildFailureCallback(userId, order.getId(), e);
                throw e;
            }
            order.setStatus(CheckoutOrderStatus.PAYMENT_PENDING);
            order.setReservationExpiresAt(expires);
            order.setPaymentInitIdempotencyKey(payKey);
            order.setFailReason(null);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("checkout payment re-initiated after FAILED orderId={}", orderId);
            return response;
        }

        if (order.getStatus() != CheckoutOrderStatus.PENDING) {
            throw new IllegalStateException("Order cannot start payment in status " + order.getStatus());
        }

        validateLivePricing(order);
        LocalDateTime expires = LocalDateTime.now().plusMinutes(checkoutProperties.getReservationTtlMinutes());
        reserveInventoryForOrder(order, expires);
        InitiateCheckoutPaymentResponse response;
        try {
            response = buildInitiateResponse(order);
        } catch (RuntimeException e) {
            registerInitiateBuildFailureCallback(userId, order.getId(), e);
            throw e;
        }
        // Generate abandon token for sendBeacon-based tab-close abandon (no auth required)
        if (order.getAbandonToken() == null || order.getAbandonToken().isBlank()) {
            order.setAbandonToken(java.util.UUID.randomUUID().toString().replace("-", ""));
        }
        order.setStatus(CheckoutOrderStatus.PAYMENT_PENDING);
        order.setReservationExpiresAt(expires);
        order.setPaymentInitIdempotencyKey(payKey);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("checkout payment initiated orderId={} merchantOrderId={}", order.getId(), order.getMerchantOrderId());
        return response;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCheckoutPaymentPreparationFailure(Long userId, Long orderId, String reason) {
        CheckoutOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);
        if (order == null || !order.getUserId().equals(userId)) {
            return;
        }
        String msg = truncate(
                reason != null && !reason.isBlank() ? reason.trim() : "Payment could not be started",
                500);
        if (order.getStatus() == CheckoutOrderStatus.PENDING) {
            releaseActiveReservations(order);
            cancelOrderLines(order);
            order.setStatus(CheckoutOrderStatus.FAILED);
            order.setFailReason(msg);
            order.setReservationExpiresAt(null);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.warn("checkout payment form preparation failed — order marked FAILED id={} user={}", orderId, userId);
        } else if (order.getStatus() == CheckoutOrderStatus.PAYMENT_PENDING) {
            releaseActiveReservations(order);
            cancelOrderLines(order);
            order.setStatus(CheckoutOrderStatus.FAILED);
            order.setFailReason(msg);
            order.setReservationExpiresAt(null);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.warn("checkout payment form preparation failed — PAYMENT_PENDING marked FAILED id={} user={}", orderId, userId);
        } else if (order.getStatus() == CheckoutOrderStatus.FAILED) {
            order.setFailReason(msg);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        }
    }

    private void registerInitiateBuildFailureCallback(Long userId, Long orderId, RuntimeException e) {
        String msg = truncate(
                e.getMessage() != null && !e.getMessage().isBlank()
                        ? e.getMessage()
                        : "Payment form could not be prepared",
                500);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            self.recordCheckoutPaymentPreparationFailure(userId, orderId, msg);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    try {
                        self.recordCheckoutPaymentPreparationFailure(userId, orderId, msg);
                    } catch (Exception ex) {
                        log.error("recordCheckoutPaymentPreparationFailure failed orderId={}", orderId, ex);
                    }
                }
            }
        });
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void syncPaymentStatusIfPending(Long userId, Long orderId) {
        orderRepository.findByIdForUpdate(orderId).ifPresent(o -> {
            if (!o.getUserId().equals(userId)) return;
            if (o.getStatus() == CheckoutOrderStatus.PAYMENT_PENDING) {
                // To avoid rate-limiting, only perform explicit sync if it hasn't been updated in the last 15 seconds
                if (o.getUpdatedAt() != null && o.getUpdatedAt().isAfter(LocalDateTime.now().minusSeconds(15))) {
                    return;
                }
                ccAvenueOrderStatusClient.fetchStatus(o.getMerchantOrderId()).ifPresent(
                        this::applyReconciliationOutcome
                );
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutOrderResponse getOrder(Long userId, Long orderId) {
        CheckoutOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout order not found"));
        if (!order.getUserId().equals(userId)) {
            throw new SecurityException("Order does not belong to current user");
        }
        return mapOrder(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutOrderResponse> listMyCheckoutOrders(Long userId, int limit) {
        int size = Math.min(limit <= 0 ? 50 : limit, 100);
        List<CheckoutOrder> orders = orderRepository
                .findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")))
                .getContent();

        // Perform JIT sync for any pending orders before mapping them to output
        for (CheckoutOrder o : orders) {
            if (o.getStatus() == CheckoutOrderStatus.PAYMENT_PENDING) {
                // Ignore failure if sync errors out. 
                try {
                    this.self.syncPaymentStatusIfPending(userId, o.getId());
                } catch (Exception ignored) {}
            }
        }
        
        // Refetch to get the updated status if any were modified via JIT sync
        return orderRepository
                .findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")))
                .getContent()
                .stream()
                .map(this::mapOrder)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutOrderResponse> adminListAllOrders(int limit) {
        int size = Math.min(Math.max(limit, 1), 500);
        return orderRepository
                .findAllByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, size))
                .getContent()
                .stream()
                .map(this::mapOrder)
                .toList();
    }

    @Override
    @Transactional
    public void abandonOrder(Long userId, Long orderId) {
        CheckoutOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !order.getUserId().equals(userId)) {
            return; // silently ignore — sendBeacon fire-and-forget
        }
        // Only abandon if still PAYMENT_PENDING (not already resolved by callback)
        if (order.getStatus() == CheckoutOrderStatus.PAYMENT_PENDING) {
            releaseActiveReservations(order);
            cancelOrderLines(order);
            order.setStatus(CheckoutOrderStatus.FAILED);
            order.setFailReason("Payment abandoned by user (browser closed or navigated away)");
            order.setReservationExpiresAt(null);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("checkout order abandoned id={} user={}", orderId, userId);
        }
        // If PAID, FAILED, CANCELLED — no-op (idempotent)
    }

    @Override
    @Transactional
    public void abandonOrderByToken(Long orderId, String abandonToken) {
        if (orderId == null || abandonToken == null || abandonToken.isBlank()) {
            return; // sendBeacon fire-and-forget — silently ignore malformed
        }
        CheckoutOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null) {
            return;
        }
        if (order.getAbandonToken() == null || !order.getAbandonToken().equals(abandonToken.trim())) {
            return; // invalid token — silently ignore
        }
        if (order.getStatus() == CheckoutOrderStatus.PAYMENT_PENDING) {
            releaseActiveReservations(order);
            cancelOrderLines(order);
            order.setStatus(CheckoutOrderStatus.FAILED);
            order.setFailReason("Payment abandoned by user (browser closed or navigated away)");
            order.setReservationExpiresAt(null);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("checkout order abandoned via beacon id={}", orderId);
        }
    }

    @Override
    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        CheckoutOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout order not found"));
        if (!order.getUserId().equals(userId)) {
            throw new SecurityException("Order does not belong to current user");
        }
        if (order.getStatus() == CheckoutOrderStatus.PENDING) {
            cancelOrderLines(order);
            order.setStatus(CheckoutOrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("checkout order cancelled id={} (was PENDING)", orderId);
            return;
        }
        if (order.getStatus() == CheckoutOrderStatus.PAYMENT_PENDING) {
            releaseActiveReservations(order);
            cancelOrderLines(order);
            order.setStatus(CheckoutOrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            order.setReservationExpiresAt(null);
            order.setFailReason(truncate("Checkout cancelled before payment completed (e.g. closed gateway or abandoned)", 480));
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("checkout order cancelled id={} (was PAYMENT_PENDING) user={}", orderId, userId);
            return;
        }
        if (order.getStatus() == CheckoutOrderStatus.PAID) {
            cancelOrderLines(order);
            order.setStatus(CheckoutOrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            order.setFailReason(truncate("Customer cancelled paid order — refund initiated", 480));
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("checkout order cancelled id={} (was PAID) user={} — initiating refund", orderId, userId);
            // Auto-trigger refund to original payment method
            try {
                requestCustomerRefund(userId, orderId, "Order cancelled by customer after payment");
            } catch (Exception e) {
                log.warn("Auto-refund initiation failed for order {}: {}", orderId, e.getMessage());
            }
            return;
        }
        throw new IllegalStateException("Order in status " + order.getStatus() + " cannot be cancelled");
    }

    @Override
    @Transactional
    public void requestCustomerRefund(Long userId, Long orderId, String reason) {
        CheckoutOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout order not found"));
        if (!order.getUserId().equals(userId)) {
            throw new SecurityException("Order does not belong to current user");
        }
        if (order.getStatus() != CheckoutOrderStatus.PAID && order.getStatus() != CheckoutOrderStatus.CANCELLED) {
            throw new IllegalStateException("Refunds are only available for paid or cancelled (post-payment) orders");
        }
        List<CheckoutRefund> inFlight = refundRepository.findByOrder_IdAndStatusIn(orderId, List.of(
                CheckoutRefundRecordStatus.INITIATED,
                CheckoutRefundRecordStatus.PENDING_GATEWAY));
        if (!inFlight.isEmpty()) {
            throw new IllegalStateException("A refund is already in progress for this order");
        }
        BigDecimal refunded = Optional.ofNullable(refundRepository.sumSuccessfulAmountByOrderId(orderId))
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = order.getTotalAmount().setScale(2, RoundingMode.HALF_UP).subtract(refunded);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Nothing left to refund");
        }
        String refundReason = (reason != null && !reason.isBlank())
                ? truncate(reason.trim(), 480)
                : "Customer requested refund";
        CheckoutRefund refund = CheckoutRefund.builder()
                .order(order)
                .amount(remaining)
                .reason(refundReason)
                .status(CheckoutRefundRecordStatus.PENDING_GATEWAY)
                .ccaTrackingId(resolveCcaTrackingId(order))
                .build();
        CheckoutRefund saved = refundRepository.save(refund);
        order.setRefundNote(refundReason);
        order.setRefundTotalAmount(remaining);
        orderRepository.save(order);
        applicationEventPublisher.publishEvent(new RefundCreatedEvent(saved.getId()));
        log.info("customer refund requested orderId={} refundId={} amount={}", orderId, saved.getId(), remaining);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleCcAvenueDecryptedCallback(Map<String, String> params, String encResp, String clientIp) {
        String merchantOrderId = params.get("order_id");
        String orderStatus = params.get("order_status");
        String trackingId = params.get("tracking_id");
        String amountStr = params.get("amount");
        String paymentMode = params.get("payment_mode");
        String statusMessage = params.get("status_message");

        appendEvent(null, merchantOrderId, "CALLBACK_DECRYPTED",
                safeJsonPreview(params));

        CheckoutOrder order = orderRepository.findByMerchantOrderIdForUpdate(merchantOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout order not found for callback"));

        appendEvent(order.getId(), merchantOrderId, "CALLBACK_ORDER_LOCKED",
                "status=" + orderStatus + ",tracking=" + trackingId);

        if (trackingId != null && !trackingId.isBlank() && gatewayPaymentRepository.existsByTrackingId(trackingId)) {
            log.info("Duplicate CCAvenue callback ignored for trackingId={} orderId={}", trackingId, order.getId());
            return;
        }

        if (orderStatus == null || orderStatus.isBlank()) {
            log.warn("checkout callback missing order_status for order {}", order.getId());
            return;
        }

        String normalized = orderStatus.trim();
        FailureReason reason = PaymentErrorClassifier.classify(statusMessage);

        if (isSuccessStatus(normalized)) {
            applyPaymentSuccess(order, params, encResp, clientIp, trackingId, amountStr, paymentMode, statusMessage);
            return;
        } 
        
        // Fail Fast Principle: Immediate terminal status for deterministic errors even if gateway reports pending
        if (reason == FailureReason.AUTH_FAILED || reason == FailureReason.INVALID_REQUEST || reason == FailureReason.SIGNATURE_MISMATCH) {
            applyPaymentFailure(order, encResp, clientIp, trackingId, amountStr, paymentMode, statusMessage, normalized, reason);
            return;
        }

        if (isPendingStatus(normalized)) {
            extendReservationIfNeeded(order);
            appendEvent(order.getId(), merchantOrderId, "CALLBACK_PENDING_GATEWAY_STATE", normalized);
        } else {
            applyPaymentFailure(order, encResp, clientIp, trackingId, amountStr, paymentMode, statusMessage, normalized, reason);
        }
    }

    @Override
    @Transactional
    public void expireDuePaymentPendingOrders() {
        List<CheckoutOrder> due = orderRepository.findPaymentPendingExpired(CheckoutOrderStatus.PAYMENT_PENDING, LocalDateTime.now());
        for (CheckoutOrder snapshot : due) {
            orderRepository.findByIdForUpdate(snapshot.getId()).ifPresent(order -> {
                if (order.getStatus() != CheckoutOrderStatus.PAYMENT_PENDING) {
                    return;
                }
                if (order.getReservationExpiresAt() == null || !order.getReservationExpiresAt().isBefore(LocalDateTime.now())) {
                    return;
                }
                releaseActiveReservations(order);
                cancelOrderLines(order);
                order.setStatus(CheckoutOrderStatus.FAILED);
                order.setFailReason("Payment reservation TTL expired");
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);
                log.info("checkout order expired reservations id={}", order.getId());
            });
        }
    }

    @Override
    @Transactional
    public void failLatestPendingOrderOnGatewayCancel(String clientIp) {
        // Find the most recent PAYMENT_PENDING order and fail it.
        // This is the fail-fast path for when CCAvenue redirects to the cancel URL
        // without any encrypted response (e.g. 10002 Merchant Authentication Failed).
        orderRepository.findTopByStatusOrderByCreatedAtDesc(CheckoutOrderStatus.PAYMENT_PENDING)
                .ifPresent(snapshot -> {
                    orderRepository.findByIdForUpdate(snapshot.getId()).ifPresent(order -> {
                        if (order.getStatus() != CheckoutOrderStatus.PAYMENT_PENDING) {
                            return; // already resolved by another thread
                        }
                        releaseActiveReservations(order);
                        cancelOrderLines(order);
                        order.setStatus(CheckoutOrderStatus.FAILED);
                        order.setFailReason("Gateway cancel callback (no response data — likely merchant authentication failure)");
                        order.setFailureReason(FailureReason.AUTH_FAILED);
                        order.setReservationExpiresAt(null);
                        order.setUpdatedAt(LocalDateTime.now());
                        orderRepository.save(order);
                        log.warn("FAIL-FAST: checkout order {} marked FAILED on cancel callback with no encResp (10002?), ip={}",
                                order.getId(), clientIp);
                    });
                });
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutOrderResponse adminGetOrder(Long orderId) {
        CheckoutOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout order not found"));
        return mapOrder(order);
    }
    
    @Override
    @Transactional
    public CheckoutOrderResponse adminFulfillOrder(Long orderId, Long adminId) {
        CheckoutOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout order not found"));

        if (order.getStatus() != CheckoutOrderStatus.PAID) {
            throw new IllegalStateException("Only PAID orders can be fulfilled. Current status: " + order.getStatus());
        }

        boolean changed = false;
        for (CheckoutOrderLine line : order.getLines()) {
            if (line.getFulfillmentStatus() == CheckoutLineFulfillmentStatus.PENDING) {
                line.setFulfillmentStatus(CheckoutLineFulfillmentStatus.FULFILLED);
                changed = true;
            }
        }

        if (changed) {
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            appendEvent(order.getId(), order.getMerchantOrderId(), "ADMIN_FULFILL", "Order manually fulfilled by admin_id=" + adminId);
        }

        return mapOrder(order);
    }

    @Override
    @Transactional
    public void adminMarkRefunded(Long orderId, Long adminId, String notes) {
        checkoutRefundLifecycleService.adminMarkOrderRefundedLegacy(orderId, adminId, notes);
    }

    @Override
    @Transactional
    public void adminCancelOrder(Long orderId, Long adminId, String reason) {
        CheckoutOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout order not found"));

        String cancelReason = (reason != null && !reason.isBlank())
                ? truncate(reason.trim(), 480)
                : "Cancelled by admin";

        switch (order.getStatus()) {
            case PENDING -> {
                cancelOrderLines(order);
                order.setStatus(CheckoutOrderStatus.CANCELLED);
                order.setCancelledAt(LocalDateTime.now());
                order.setFailReason(cancelReason);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);
                log.info("admin cancelled order {} (was PENDING) admin={}", orderId, adminId);
            }
            case PAYMENT_PENDING -> {
                releaseActiveReservations(order);
                cancelOrderLines(order);
                order.setStatus(CheckoutOrderStatus.CANCELLED);
                order.setCancelledAt(LocalDateTime.now());
                order.setReservationExpiresAt(null);
                order.setFailReason(cancelReason);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);
                log.info("admin cancelled order {} (was PAYMENT_PENDING) admin={}", orderId, adminId);
            }
            case PAID -> {
                cancelOrderLines(order);
                order.setStatus(CheckoutOrderStatus.CANCELLED);
                order.setCancelledAt(LocalDateTime.now());
                order.setFailReason(cancelReason);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);
                log.info("admin cancelled order {} (was PAID) admin={} — initiating refund", orderId, adminId);
                try {
                    // Create refund record; the refund lifecycle service will handle gateway submission
                    BigDecimal refunded = Optional.ofNullable(refundRepository.sumSuccessfulAmountByOrderId(orderId))
                            .orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal remaining = order.getTotalAmount().setScale(2, RoundingMode.HALF_UP).subtract(refunded);
                    if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                        CheckoutRefund refund = CheckoutRefund.builder()
                                .order(order)
                                .amount(remaining)
                                .reason(cancelReason)
                                .status(CheckoutRefundRecordStatus.PENDING_GATEWAY)
                                .ccaTrackingId(resolveCcaTrackingId(order))
                                .build();
                        CheckoutRefund saved = refundRepository.save(refund);
                        order.setRefundNote(cancelReason);
                        order.setRefundTotalAmount(remaining);
                        orderRepository.save(order);
                        applicationEventPublisher.publishEvent(new RefundCreatedEvent(saved.getId()));
                        log.info("admin refund initiated for order {} amount={}", orderId, remaining);
                    }
                } catch (Exception e) {
                    log.warn("Admin refund initiation failed for order {}: {}", orderId, e.getMessage());
                }
            }
            default -> throw new IllegalStateException(
                    "Order in status " + order.getStatus() + " cannot be cancelled by admin");
        }
    }

    @Override
    public void reconcileStalePaymentPendingOrders() {
        if (!checkoutProperties.isReconciliationEnabled()) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(checkoutProperties.getReconcileOrderAgeMinutes());
        List<CheckoutOrder> stale = orderRepository.findByStatusAndUpdatedAtBefore(CheckoutOrderStatus.PAYMENT_PENDING, threshold);
        for (CheckoutOrder o : stale) {
            try {
                ccAvenueOrderStatusClient.fetchStatus(o.getMerchantOrderId()).ifPresentOrElse(
                        status -> applyReconciliationOutcome(status),
                        () -> log.debug("No status from CCAvenue for order {}", o.getMerchantOrderId())
                );
            } catch (Exception ex) {
                log.warn("checkout reconciliation failed for order {}: {}", o.getId(), ex.getMessage());
            }
        }
    }

    private void applyReconciliationOutcome(CcAvenueOrderStatusClient.CcAvenueOrderStatusResult status) {
        Map<String, String> synthetic = new HashMap<>();
        synthetic.put("order_id", status.merchantOrderId());
        synthetic.put("order_status", status.orderStatus());
        synthetic.put("amount", status.amount());
        synthetic.put("tracking_id", status.trackingId() != null ? status.trackingId() : "");
        synthetic.put("payment_mode", status.paymentMode() != null ? status.paymentMode() : "");
        synthetic.put("status_message", status.statusMessage() != null ? status.statusMessage() : "");
        self.handleCcAvenueDecryptedCallback(synthetic, null, "reconciliation-job");
    }

    private void applyPaymentSuccess(CheckoutOrder order,
                                     Map<String, String> params,
                                     String encResp,
                                     String clientIp,
                                     String trackingId,
                                     String amountStr,
                                     String paymentMode,
                                     String statusMessage) {
        if (order.getStatus() == CheckoutOrderStatus.PAID) {
            log.info("checkout order {} already PAID (idempotent)", order.getId());
            return;
        }

        if (order.getStatus() != CheckoutOrderStatus.PAYMENT_PENDING) {
            log.warn("Late or orphan success for checkout order {} in status {} — recording payment only", order.getId(), order.getStatus());
            persistGatewayPaymentOrIgnore(order, trackingId, amountStr, params, encResp, clientIp,
                    CheckoutGatewayPaymentRecordStatus.CAPTURED, paymentMode, statusMessage);
            recordOrphanRefundSuggestion(order, amountStr);
            return;
        }

        BigDecimal amount = parseAmount(amountStr);
        if (amount == null || order.getTotalAmount().setScale(2, RoundingMode.HALF_UP).compareTo(amount) != 0) {
            log.error("Amount mismatch for checkout order {}: expected {} got {}", order.getId(), order.getTotalAmount(), amountStr);
            appendEvent(order.getId(), order.getMerchantOrderId(), "CALLBACK_AMOUNT_MISMATCH", amountStr);
            return;
        }

        if (trackingId == null || trackingId.isBlank()) {
            log.error("Missing tracking_id for successful checkout order {}", order.getId());
            return;
        }

        try {
            persistGatewayPaymentOrThrow(order, trackingId, amountStr, params, encResp, clientIp,
                    CheckoutGatewayPaymentRecordStatus.CAPTURED, paymentMode, statusMessage);
        } catch (DataIntegrityViolationException dup) {
            log.info("Duplicate tracking insert for order {} — treating as idempotent", order.getId());
            return;
        }

        fulfillInventoryAfterPayment(order);

        order.setStatus(CheckoutOrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setReservationExpiresAt(null);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("checkout order {} marked PAID", order.getId());
    }

    private void applyPaymentFailure(CheckoutOrder order,
                                     String encResp,
                                     String clientIp,
                                     String trackingId,
                                     String amountStr,
                                     String paymentMode,
                                     String statusMessage,
                                     String normalizedStatus,
                                     FailureReason failureReason) {
        if (order.getStatus() == CheckoutOrderStatus.PAID) {
            log.warn("Ignoring failure callback for already PAID checkout order {}", order.getId());
            return;
        }
        if (order.getStatus() != CheckoutOrderStatus.PAYMENT_PENDING) {
            appendEvent(order.getId(), order.getMerchantOrderId(), "CALLBACK_FAILURE_IGNORED_STATE", order.getStatus().name());
            return;
        }

        if (trackingId != null && !trackingId.isBlank()) {
            persistGatewayPaymentOrIgnore(order, trackingId, amountStr, Map.of(), encResp, clientIp,
                    CheckoutGatewayPaymentRecordStatus.DECLINED, paymentMode, statusMessage);
        }

        releaseActiveReservations(order);
        cancelOrderLines(order);
        order.setStatus(CheckoutOrderStatus.FAILED);
        order.setFailReason(truncate("Gateway: " + normalizedStatus + " / " + statusMessage, 480));
        order.setFailureReason(failureReason != null ? failureReason : FailureReason.UNKNOWN);
        order.setReservationExpiresAt(null);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("checkout order {} marked FAILED ({})", order.getId(), normalizedStatus);
    }

    private void persistGatewayPaymentOrThrow(CheckoutOrder order,
                                              String trackingId,
                                              String amountStr,
                                              Map<String, String> params,
                                              String encResp,
                                              String clientIp,
                                              CheckoutGatewayPaymentRecordStatus status,
                                              String paymentMode,
                                              String statusMessage) {
        CheckoutGatewayPayment row = CheckoutGatewayPayment.builder()
                .order(order)
                .trackingId(trackingId)
                .gatewayOrderId(params.get("order_id"))
                .amount(parseAmount(amountStr))
                .currency(order.getCurrency())
                .status(status)
                .paymentMode(paymentMode)
                .statusMessage(statusMessage)
                .rawResponseEnc(encResp)
                .rawResponseDec(safeJsonPreview(params))
                .clientIp(clientIp)
                .build();
        gatewayPaymentRepository.save(row);
    }

    private void persistGatewayPaymentOrIgnore(CheckoutOrder order,
                                               String trackingId,
                                               String amountStr,
                                               Map<String, String> params,
                                               String encResp,
                                               String clientIp,
                                               CheckoutGatewayPaymentRecordStatus status,
                                               String paymentMode,
                                               String statusMessage) {
        if (trackingId == null || trackingId.isBlank()) {
            return;
        }
        if (gatewayPaymentRepository.existsByTrackingId(trackingId)) {
            return;
        }
        try {
            persistGatewayPaymentOrThrow(order, trackingId, amountStr, params, encResp, clientIp, status, paymentMode, statusMessage);
        } catch (DataIntegrityViolationException ignored) {
            // duplicate
        }
    }

    private void recordOrphanRefundSuggestion(CheckoutOrder order, String amountStr) {
        String reason = "Payment succeeded after order left PAYMENT_PENDING";
        BigDecimal amt = parseAmount(amountStr);
        if (amt == null) {
            amt = order.getTotalAmount();
        }
        CheckoutRefund refund = CheckoutRefund.builder()
                .order(order)
                .amount(amt)
                .reason(reason)
                .status(CheckoutRefundRecordStatus.PENDING_GATEWAY)
                .ccaTrackingId(resolveCcaTrackingId(order))
                .build();
        CheckoutRefund saved = refundRepository.save(refund);
        order.setRefundNote(truncate(reason, 480));
        order.setRefundTotalAmount(amt);
        orderRepository.save(order);
        applicationEventPublisher.publishEvent(new RefundCreatedEvent(saved.getId()));
    }

    private void fulfillInventoryAfterPayment(CheckoutOrder order) {
        List<CheckoutReservation> active = reservationRepository.findByOrderIdAndStatus(order.getId(), CheckoutReservationStatus.ACTIVE);
        for (CheckoutReservation r : active) {
            Product p = productRepository.findById(r.getProductId()).orElse(null);
            if (p == null || p.getStockOnHand() == null) {
                r.getOrderLine().setFulfillmentStatus(CheckoutLineFulfillmentStatus.FULFILLED);
                r.setStatus(CheckoutReservationStatus.CONSUMED);
                reservationRepository.save(r);
                continue;
            }
            int n = inventoryRepository.consumeReservedAndDecreaseOnHand(r.getProductId(), r.getQuantity());
            if (n == 0) {
                inventoryRepository.decreaseReservedStock(r.getProductId(), r.getQuantity());
                r.getOrderLine().setFulfillmentStatus(CheckoutLineFulfillmentStatus.OUT_OF_STOCK);
                BigDecimal refundAmt = r.getOrderLine().getLineTotal();
                CheckoutRefund lineRefund = CheckoutRefund.builder()
                        .order(order)
                        .amount(refundAmt)
                        .reason("Paid but inventory consume failed for product " + r.getProductId())
                        .status(CheckoutRefundRecordStatus.PENDING_GATEWAY)
                        .ccaTrackingId(resolveCcaTrackingId(order))
                        .build();
                CheckoutRefund savedLineRefund = refundRepository.save(lineRefund);
                applicationEventPublisher.publishEvent(new RefundCreatedEvent(savedLineRefund.getId()));
                r.setStatus(CheckoutReservationStatus.RELEASED);
                reservationRepository.save(r);
            } else {
                r.getOrderLine().setFulfillmentStatus(CheckoutLineFulfillmentStatus.FULFILLED);
                r.setStatus(CheckoutReservationStatus.CONSUMED);
                reservationRepository.save(r);
            }
        }
    }

    private void releaseActiveReservations(CheckoutOrder order) {
        for (CheckoutReservation r : reservationRepository.findByOrderIdAndStatus(order.getId(), CheckoutReservationStatus.ACTIVE)) {
            Product p = productRepository.findById(r.getProductId()).orElse(null);
            if (p != null && p.getStockOnHand() != null) {
                inventoryRepository.decreaseReservedStock(r.getProductId(), r.getQuantity());
            }
            r.setStatus(CheckoutReservationStatus.RELEASED);
            reservationRepository.save(r);
        }
    }

    /** Mark all PENDING line items as CANCELLED when the order dies. */
    private void cancelOrderLines(CheckoutOrder order) {
        if (order.getLines() == null) return;
        for (CheckoutOrderLine line : order.getLines()) {
            if (line.getFulfillmentStatus() == CheckoutLineFulfillmentStatus.PENDING) {
                line.setFulfillmentStatus(CheckoutLineFulfillmentStatus.CANCELLED);
            }
        }
    }

    private void reserveInventoryForOrder(CheckoutOrder order, LocalDateTime reservationExpiresAt) {
        List<Long> rollbackPids = new ArrayList<>();
        List<Integer> rollbackQty = new ArrayList<>();
        try {
            for (CheckoutOrderLine line : order.getLines()) {
                Product p = productRepository.findById(line.getProductId()).orElseThrow();
                if (p.getStockOnHand() == null) {
                    CheckoutReservation res = CheckoutReservation.builder()
                            .orderLine(line)
                            .productId(line.getProductId())
                            .quantity(line.getQuantity())
                            .status(CheckoutReservationStatus.ACTIVE)
                            .expiresAt(reservationExpiresAt)
                            .build();
                    reservationRepository.save(res);
                    continue;
                }
                int updated = inventoryRepository.increaseReservedStock(line.getProductId(), line.getQuantity());
                if (updated == 0) {
                    throw new IllegalStateException("Insufficient stock for product " + line.getProductId());
                }
                rollbackPids.add(line.getProductId());
                rollbackQty.add(line.getQuantity());
                CheckoutReservation res = CheckoutReservation.builder()
                        .orderLine(line)
                        .productId(line.getProductId())
                        .quantity(line.getQuantity())
                        .status(CheckoutReservationStatus.ACTIVE)
                        .expiresAt(reservationExpiresAt)
                        .build();
                reservationRepository.save(res);
            }
        } catch (RuntimeException ex) {
            for (int i = rollbackPids.size() - 1; i >= 0; i--) {
                inventoryRepository.decreaseReservedStock(rollbackPids.get(i), rollbackQty.get(i));
            }
            throw ex;
        }
    }

    private void validateLivePricing(CheckoutOrder order) {
        for (CheckoutOrderLine line : order.getLines()) {
            Product p = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found " + line.getProductId()));
            BigDecimal live = unitSellingPrice(p);
            if (live.subtract(line.getUnitPriceSnapshot()).abs().compareTo(PRICE_TOLERANCE) > 0) {
                throw new IllegalStateException("Price changed for product " + line.getProductId()
                        + " during checkout; create a new order.");
            }
            if (!Boolean.TRUE.equals(p.getActive())) {
                throw new IllegalStateException("Product inactive: " + line.getProductId());
            }
        }
    }

    private void validateAvailabilitySoft(Product p, int quantity) {
        if (p.getStockOnHand() == null) {
            return;
        }
        int available = p.getStockOnHand() - Optional.ofNullable(p.getStockReserved()).orElse(0);
        if (available < quantity) {
            throw new IllegalStateException("Insufficient stock for product " + p.getProductId());
        }
    }

    private Map<Long, Product> loadProducts(CreateCheckoutOrderRequest request) {
        Set<Long> ids = new HashSet<>();
        for (CheckoutLineRequest lr : request.getLines()) {
            if (!ids.add(lr.getProductId())) {
                throw new IllegalArgumentException("Duplicate product in cart: " + lr.getProductId());
            }
        }
        Map<Long, Product> map = new HashMap<>();
        for (Long id : ids) {
            Product p = productRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found " + id));
            if (!Boolean.TRUE.equals(p.getActive())) {
                throw new IllegalStateException("Product inactive: " + id);
            }
            map.put(id, p);
        }
        return map;
    }

    private BigDecimal unitSellingPrice(Product p) {
        double price = p.getPrice() != null ? p.getPrice() : 0.0;
        double discount = p.getOffers() != null ? p.getOffers() : 0.0;
        double finalPrice = price - (price * discount / 100.0);
        return BigDecimal.valueOf(finalPrice).setScale(2, RoundingMode.HALF_UP);
    }

    private InitiateCheckoutPaymentResponse buildInitiateResponse(CheckoutOrder order) {
        String redirect = firstNonBlank(checkoutProperties.getCcavenueRedirectUrl(), ccAvenueConfig.getRedirectUrl());
        String cancel = firstNonBlank(checkoutProperties.getCcavenueCancelUrl(), ccAvenueConfig.getCancelUrl());
        CcAvenuePaymentRequest paymentRequest = CcAvenuePaymentRequest.builder()
                .orderId(order.getMerchantOrderId())
                .amount(order.getTotalAmount())
                .redirectUrl(redirect)
                .cancelUrl(cancel)
                .billingName(order.getCustomerName())
                .billingAddress(order.getDeliveryAddress())
                .billingTel(order.getContactNumber())
                .build();
        String form = ccAvenuePaymentService.generatePaymentForm(paymentRequest);
        return InitiateCheckoutPaymentResponse.builder()
                .orderId(order.getId())
                .merchantOrderId(order.getMerchantOrderId())
                .paymentFormHtml(form)
                .abandonToken(order.getAbandonToken())
                .build();
    }

    private CheckoutOrderResponse mapOrder(CheckoutOrder order) {
        Map<Long, String> productNames = loadProductNamesForLines(order.getLines());
        List<CheckoutOrderLineResponse> lines = new ArrayList<>();
        for (CheckoutOrderLine line : order.getLines()) {
            lines.add(CheckoutOrderLineResponse.builder()
                    .lineId(line.getId())
                    .productId(line.getProductId())
                    .productName(productNames.get(line.getProductId()))
                    .quantity(line.getQuantity())
                    .unitPriceSnapshot(line.getUnitPriceSnapshot())
                    .lineTotal(line.getLineTotal())
                    .fulfillmentStatus(line.getFulfillmentStatus().name())
                    .build());
        }
        String refundStatus = refundRepository.findFirstByOrder_IdOrderByIdDesc(order.getId())
                .map(r -> r.getStatus().name())
                .orElse(null);

        return CheckoutOrderResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus().name())
                .merchantOrderId(order.getMerchantOrderId())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .reservationExpiresAt(order.getReservationExpiresAt())
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .lines(lines)
                .failReason(order.getFailReason())
                .refundStatus(refundStatus)
                .customerName(order.getCustomerName())
                .contactNumber(order.getContactNumber())
                .deliveryAddress(order.getDeliveryAddress())
                .build();
    }

    private Map<Long, String> loadProductNamesForLines(List<CheckoutOrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = new HashSet<>();
        for (CheckoutOrderLine line : lines) {
            ids.add(line.getProductId());
        }
        Map<Long, String> out = new HashMap<>();
        for (Long id : ids) {
            productRepository.findById(id).ifPresent(p -> {
                String n = p.getProductName();
                out.put(id, n != null && !n.isBlank() ? n : ("Product " + id));
            });
            out.putIfAbsent(id, "Product " + id);
        }
        return out;
    }

    private void appendEvent(Long orderId, String merchantOrderId, String type, String payload) {
        try {
            paymentEventRepository.save(CheckoutPaymentEvent.builder()
                    .orderId(orderId)
                    .merchantOrderId(merchantOrderId)
                    .eventType(type)
                    .payload(truncate(payload, 8000))
                    .build());
        } catch (Exception e) {
            log.warn("checkout payment event log failed: {}", e.getMessage());
        }
    }

    private String safeJsonPreview(Map<String, String> params) {
        try {
            return truncate(objectMapper.writeValueAsString(params), 8000);
        } catch (Exception e) {
            return String.valueOf(params);
        }
    }

    private static BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(amountStr.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isSuccessStatus(String orderStatus) {
        return "Success".equalsIgnoreCase(orderStatus) || "SUCCESS".equalsIgnoreCase(orderStatus);
    }

    private static boolean isPendingStatus(String orderStatus) {
        String s = orderStatus.toLowerCase(Locale.ROOT);
        return s.contains("initiated") || s.contains("await") || s.contains("pending") || s.contains("bounced");
    }

    private void extendReservationIfNeeded(CheckoutOrder order) {
        if (order.getStatus() != CheckoutOrderStatus.PAYMENT_PENDING) {
            return;
        }
        LocalDateTime ext = LocalDateTime.now().plusMinutes(Math.max(checkoutProperties.getReservationTtlMinutes(), 30));
        if (order.getReservationExpiresAt() == null || ext.isAfter(order.getReservationExpiresAt())) {
            order.setReservationExpiresAt(ext);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("hash failed", e);
        }
    }

    private static String tempMerchantId() {
        return "CHX" + UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String resolveCcaTrackingId(CheckoutOrder order) {
        List<CheckoutGatewayPayment> payments = gatewayPaymentRepository.findByOrder_IdOrderByIdDesc(order.getId());
        for (CheckoutGatewayPayment p : payments) {
            if (p.getStatus() == CheckoutGatewayPaymentRecordStatus.CAPTURED
                    && p.getTrackingId() != null && !p.getTrackingId().isBlank()) {
                return p.getTrackingId();
            }
        }
        if (!payments.isEmpty()) {
            String t = payments.get(0).getTrackingId();
            return t != null && !t.isBlank() ? t : null;
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }
}
