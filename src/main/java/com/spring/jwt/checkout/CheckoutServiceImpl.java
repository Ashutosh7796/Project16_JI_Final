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
import com.spring.jwt.ledger.LedgerEntryStatus;
import com.spring.jwt.ledger.LedgerService;
import com.spring.jwt.ledger.LedgerSource;
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
    private final LedgerService ledgerService;

    @Override
    @Transactional
    public CheckoutOrderResponse createOrder(Long userId, CreateCheckoutOrderRequest request, String checkoutIdempotencyKey) {
        if (checkoutIdempotencyKey != null && !checkoutIdempotencyKey.isBlank()) {
            Optional<CheckoutOrder> existing = orderRepository.findByUserIdAndCheckoutIdempotencyKey(userId, checkoutIdempotencyKey.trim());
            // EC-22 Fix: Skip terminal-state orders so user can re-checkout with same cart
            if (existing.isPresent()) {
                CheckoutOrderStatus existingStatus = existing.get().getStatus();
                if (existingStatus != CheckoutOrderStatus.CANCELLED
                        && existingStatus != CheckoutOrderStatus.FAILED
                        && existingStatus != CheckoutOrderStatus.REFUNDED) {
                    return mapOrder(existing.get());
                }
                log.info("Idempotency key {} matched terminal order {} (status={}) — creating fresh order",
                        checkoutIdempotencyKey, existing.get().getId(), existingStatus);
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

        // PAYMENT_PENDING with different key or expired reservation:
        // This happens when user refreshes, opens a new tab, or CCAvenue session timed out.
        // Instead of crashing, treat like a retry — release old reservation and re-initiate.
        if (order.getStatus() == CheckoutOrderStatus.PAYMENT_PENDING) {
            boolean reservationExpired = order.getReservationExpiresAt() == null
                    || !order.getReservationExpiresAt().isAfter(LocalDateTime.now());

            if (reservationExpired) {
                // Reservation expired — release inventory and re-reserve fresh
                releaseActiveReservations(order);
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
                order.setAbandonToken(java.util.UUID.randomUUID().toString().replace("-", ""));
                order.setReservationExpiresAt(expires);
                order.setPaymentInitIdempotencyKey(payKey);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);
                log.info("checkout payment re-initiated after PAYMENT_PENDING (reservation expired) orderId={}", orderId);
                return response;
            } else {
                // Reservation still valid — just regenerate the CCAvenue form (idempotent)
                try {
                    InitiateCheckoutPaymentResponse response = buildInitiateResponse(order);
                    order.setPaymentInitIdempotencyKey(payKey);
                    order.setUpdatedAt(LocalDateTime.now());
                    orderRepository.save(order);
                    log.info("checkout payment form regenerated for PAYMENT_PENDING orderId={}", orderId);
                    return response;
                } catch (RuntimeException e) {
                    registerInitiateBuildFailureCallback(userId, order.getId(), e);
                    throw e;
                }
            }
        }

        if (order.getStatus() == CheckoutOrderStatus.FAILED) {
            // Defensive: release any orphan ACTIVE reservations from crash scenarios
            releaseActiveReservations(order);
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
            // Regenerate abandon token for the new payment session
            order.setAbandonToken(java.util.UUID.randomUUID().toString().replace("-", ""));
            order.setStatus(CheckoutOrderStatus.PAYMENT_PENDING);
            order.setReservationExpiresAt(expires);
            order.setPaymentInitIdempotencyKey(payKey);
            order.setFailReason(null);
            order.setFailureReason(null);
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
    @Transactional
    public void syncPaymentStatusIfPending(Long userId, Long orderId) {
        orderRepository.findById(orderId).ifPresent(o -> {
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
    @Transactional
    public org.springframework.data.domain.Page<CheckoutOrderResponse> listMyCheckoutOrders(Long userId, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<CheckoutOrder> orderPage = orderRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable);

        // Perform JIT sync for any pending orders before mapping them to output
        boolean synced = false;
        for (CheckoutOrder o : orderPage.getContent()) {
            if (o.getStatus() == CheckoutOrderStatus.PAYMENT_PENDING) {
                try {
                    this.self.syncPaymentStatusIfPending(userId, o.getId());
                    synced = true;
                } catch (Exception ignored) {}
            }
        }

        if (synced) {
            // Clear L1 cache so refetch sees writes from REQUIRES_NEW child transactions
            orderRepository.flush();
            // Refetch to get the updated status if any were modified via JIT sync
            orderPage = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        return orderPage.map(this::mapOrder);
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
            // No-cancel-after-shipped policy. Customer must use the refund flow once items left.
            for (CheckoutOrderLine line : order.getLines()) {
                if (line.getFulfillmentStatus() == CheckoutLineFulfillmentStatus.SHIPPED
                        || line.getFulfillmentStatus() == CheckoutLineFulfillmentStatus.DELIVERED) {
                    throw new IllegalStateException(
                            "Cannot cancel order with dispatched items. Line " + line.getId()
                            + " is " + line.getFulfillmentStatus().name() + ". Request a refund instead.");
                }
            }

            // P1.5 + P1.2 (customer path): block on unverified prior FAILED refund, then create
            // refund row FIRST inside the SAME transaction so a refund-creation failure rolls
            // back the status flip (no "CANCELLED with no money returned" silent loss).
            if (refundRepository.countUnverifiedFailedRefundsForOrder(orderId) > 0) {
                throw new IllegalStateException(
                        "A previous refund attempt failed and has not been verified by support. " +
                        "Please contact support to reconcile before cancelling this order.");
            }

            BigDecimal refunded = Optional.ofNullable(refundRepository.sumSuccessfulAmountByOrderId(orderId))
                    .orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal inFlightAmt = Optional.ofNullable(refundRepository.sumInFlightAmountByOrderId(orderId))
                    .orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal remaining = order.getTotalAmount().setScale(2, RoundingMode.HALF_UP)
                    .subtract(refunded).subtract(inFlightAmt);

            String refundReason = "Order cancelled by customer after payment";
            Long pendingRefundId = null;
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                CheckoutRefund refund = CheckoutRefund.builder()
                        .order(order)
                        .amount(remaining)
                        .reason(truncate(refundReason, 480))
                        .status(CheckoutRefundRecordStatus.PENDING_GATEWAY)
                        .ccaTrackingId(resolveCcaTrackingId(order))
                        .build();
                pendingRefundId = refundRepository.saveAndFlush(refund).getId();
                order.setRefundNote(truncate(refundReason, 480));
                order.setRefundTotalAmount(remaining);
            }

            restoreConsumedStock(order);
            cancelOrderLines(order);
            order.setStatus(CheckoutOrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            order.setFailReason(truncate("Customer cancelled paid order — refund initiated", 480));
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

            if (pendingRefundId != null) {
                final Long refundIdForEvent = pendingRefundId;
                final Long orderIdForLog = orderId;
                final BigDecimal amtForLog = remaining;
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            applicationEventPublisher.publishEvent(new RefundCreatedEvent(refundIdForEvent));
                            log.info("customer cancel-with-refund orderId={} refundId={} amount={}",
                                    orderIdForLog, refundIdForEvent, amtForLog);
                        }
                    });
                } else {
                    applicationEventPublisher.publishEvent(new RefundCreatedEvent(refundIdForEvent));
                }
            }
            log.info("checkout order cancelled id={} (was PAID) user={} refundQueued={}",
                    orderId, userId, pendingRefundId != null);
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
        // P1.5: Block when there are unverified FAILED refunds — gateway state is ambiguous,
        // and silently retrying could double-refund. Force support to verify the prior attempt.
        if (refundRepository.countUnverifiedFailedRefundsForOrder(orderId) > 0) {
            throw new IllegalStateException(
                    "A previous refund attempt failed and has not been verified by support. " +
                    "Please contact support to reconcile before requesting a new refund.");
        }
        // EC-17 Fix: Include both successful AND in-flight refunds to prevent over-refund
        BigDecimal refunded = Optional.ofNullable(refundRepository.sumSuccessfulAmountByOrderId(orderId))
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal inFlightAmount = Optional.ofNullable(refundRepository.sumInFlightAmountByOrderId(orderId))
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = order.getTotalAmount().setScale(2, RoundingMode.HALF_UP).subtract(refunded).subtract(inFlightAmount);
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

    /**
     * EC-25 Fix: Expire PENDING orders that were never initiated (no payment attempt).
     * These accumulate forever since no reservation TTL applies. Clean up after 24h.
     */
    @Override
    @Transactional
    public void expireOrphanPendingOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        List<CheckoutOrder> orphans = orderRepository.findByStatusAndCreatedAtBefore(CheckoutOrderStatus.PENDING, threshold);
        int count = 0;
        for (CheckoutOrder snapshot : orphans) {
            orderRepository.findByIdForUpdate(snapshot.getId()).ifPresent(order -> {
                if (order.getStatus() != CheckoutOrderStatus.PENDING) {
                    return; // status changed since snapshot query
                }
                cancelOrderLines(order);
                order.setStatus(CheckoutOrderStatus.CANCELLED);
                order.setCancelledAt(LocalDateTime.now());
                order.setFailReason("Orphan order expired — no payment initiated within 24h");
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);
            });
            count++;
        }
        if (count > 0) {
            log.info("EC-25: expired {} orphan PENDING orders older than 24h", count);
        }
    }

    @Override
    @Transactional
    public void failLatestPendingOrderOnGatewayCancel(String clientIp) {
        // P1.1: legacy "guess the latest PAYMENT_PENDING" path is unsafe under concurrency
        // (User B's order can be killed when User A bounces). The new contract requires the
        // caller to provide an HMAC-verified merchantOrderId via failPaymentPendingByMerchantOrderId.
        // This method is retained for binary compatibility but is now a structured no-op so the
        // reservation TTL safety net cleans up unattributed cancels.
        log.warn("LEGACY-CANCEL-NOOP: cancel callback with no encResp and no signed cmref hint (ip={}); " +
                "leaving PAYMENT_PENDING orders to TTL expiry", clientIp);
    }

    @Override
    @Transactional
    public void failPaymentPendingByMerchantOrderId(String merchantOrderId, String reason, String clientIp) {
        if (merchantOrderId == null || merchantOrderId.isBlank()) {
            log.warn("failPaymentPendingByMerchantOrderId: blank merchantOrderId (ip={})", clientIp);
            return;
        }
        Optional<CheckoutOrder> locked = orderRepository.findByMerchantOrderIdForUpdate(merchantOrderId);
        if (locked.isEmpty()) {
            log.warn("failPaymentPendingByMerchantOrderId: order not found merchantOrderId={} (ip={})",
                    merchantOrderId, clientIp);
            return;
        }
        CheckoutOrder order = locked.get();
        if (order.getStatus() != CheckoutOrderStatus.PAYMENT_PENDING) {
            log.info("failPaymentPendingByMerchantOrderId: order {} not PAYMENT_PENDING (status={}) — ignoring",
                    order.getId(), order.getStatus());
            return;
        }
        releaseActiveReservations(order);
        cancelOrderLines(order);
        order.setStatus(CheckoutOrderStatus.FAILED);
        order.setFailReason(reason != null && !reason.isBlank()
                ? reason
                : "Gateway cancel callback (no response data — likely merchant authentication failure)");
        order.setFailureReason(FailureReason.AUTH_FAILED);
        order.setReservationExpiresAt(null);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.warn("FAIL-SCOPED: checkout order {} (merchantOrderId={}) marked FAILED on signed cancel hint, ip={}",
                order.getId(), merchantOrderId, clientIp);
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
    public CheckoutOrderResponse adminUpdateFulfillment(Long orderId, Long adminId, CheckoutLineFulfillmentStatus targetStatus) {
        CheckoutOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout order not found"));

        if (order.getStatus() != CheckoutOrderStatus.PAID) {
            throw new IllegalStateException("Only PAID orders can have dispatch status updated. Current status: " + order.getStatus());
        }

        boolean changed = false;
        for (CheckoutOrderLine line : order.getLines()) {
            if (line.getFulfillmentStatus() != targetStatus && line.getFulfillmentStatus() != CheckoutLineFulfillmentStatus.CANCELLED) {
                line.setFulfillmentStatus(targetStatus);
                changed = true;
            }
        }

        if (changed) {
            order.setUpdatedAt(LocalDateTime.now());
            try {
                orderRepository.saveAndFlush(order);
            } catch (org.springframework.dao.DataAccessException dbEx) {
                String msg = dbEx.getMostSpecificCause() != null ? dbEx.getMostSpecificCause().getMessage() : dbEx.getMessage();
                if (msg != null && msg.contains("Data truncated") && msg.contains("fulfillment_status")) {
                    log.error("DB schema mismatch: fulfillment_status column does not accept '{}'. " +
                              "Run: ALTER TABLE checkout_order_lines MODIFY COLUMN fulfillment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING';",
                              targetStatus.name());
                    throw new IllegalStateException(
                            "Cannot update order status to " + targetStatus.name() +
                            ". The database needs a schema update. Please contact the administrator to run the migration.",
                            dbEx);
                }
                throw dbEx;
            }
            appendEvent(order.getId(), order.getMerchantOrderId(), "ADMIN_UPDATE_FULFILLMENT", "Fulfillment changed to " + targetStatus.name() + " by admin_id=" + adminId);
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
                // EC-23 Guard: Block cancel if any line is SHIPPED or DELIVERED
                for (CheckoutOrderLine line : order.getLines()) {
                    if (line.getFulfillmentStatus() == CheckoutLineFulfillmentStatus.SHIPPED
                            || line.getFulfillmentStatus() == CheckoutLineFulfillmentStatus.DELIVERED) {
                        throw new IllegalStateException(
                                "Cannot cancel order with dispatched items. Line " + line.getId()
                                + " is " + line.getFulfillmentStatus().name() + ". Request a refund instead.");
                    }
                }

                // P1.5: Block PAID-cancel auto-refund when there are unverified FAILED refunds.
                // Admin must first verify the prior attempt (existing admin refund endpoints
                // accept adminId + notes), otherwise we risk double-refund.
                if (refundRepository.countUnverifiedFailedRefundsForOrder(orderId) > 0) {
                    throw new IllegalStateException(
                            "A previous refund attempt is in FAILED state without admin verification. " +
                            "Resolve the failed refund (mark SUCCESS or definitively FAILED with notes) " +
                            "before cancelling this order to avoid double-refund.");
                }
                // P1.2: refund insert + status flip MUST be atomic. If anything below throws,
                // the @Transactional rolls back the order status flip too. The refund event is
                // published only AFTER_COMMIT so any consumer sees a consistent DB state.
                BigDecimal refunded = Optional.ofNullable(refundRepository.sumSuccessfulAmountByOrderId(orderId))
                        .orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                BigDecimal inFlightAmt = Optional.ofNullable(refundRepository.sumInFlightAmountByOrderId(orderId))
                        .orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                BigDecimal remaining = order.getTotalAmount().setScale(2, RoundingMode.HALF_UP)
                        .subtract(refunded).subtract(inFlightAmt);

                Long pendingRefundId = null;
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    // Insert refund FIRST so an event-publish or status-flip failure can't
                    // leave the order CANCELLED with no refund row.
                    CheckoutRefund refund = CheckoutRefund.builder()
                            .order(order)
                            .amount(remaining)
                            .reason(cancelReason)
                            .status(CheckoutRefundRecordStatus.PENDING_GATEWAY)
                            .ccaTrackingId(resolveCcaTrackingId(order))
                            .build();
                    pendingRefundId = refundRepository.saveAndFlush(refund).getId();
                    order.setRefundNote(cancelReason);
                    order.setRefundTotalAmount(remaining);
                }

                restoreConsumedStock(order);
                cancelOrderLines(order);
                order.setStatus(CheckoutOrderStatus.CANCELLED);
                order.setCancelledAt(LocalDateTime.now());
                order.setFailReason(cancelReason);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);

                if (pendingRefundId != null) {
                    final Long refundIdForEvent = pendingRefundId;
                    final BigDecimal refundAmtForLog = remaining;
                    final Long orderIdForLog = orderId;
                    if (TransactionSynchronizationManager.isSynchronizationActive()) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                applicationEventPublisher.publishEvent(new RefundCreatedEvent(refundIdForEvent));
                                log.info("admin refund initiated for order {} amount={} refundId={}",
                                        orderIdForLog, refundAmtForLog, refundIdForEvent);
                            }
                        });
                    } else {
                        // Should not happen inside @Transactional, but keep a fallback.
                        applicationEventPublisher.publishEvent(new RefundCreatedEvent(refundIdForEvent));
                    }
                }
                log.info("admin cancelled order {} (was PAID) admin={} refundQueued={}",
                        orderId, adminId, pendingRefundId != null);
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
            // EC-04/05/06 Fix: Late Success callback arrived after order left PAYMENT_PENDING.
            // Money IS captured by gateway — we must handle it properly.
            log.warn("Late success for checkout order {} in status {} — attempting recovery", order.getId(), order.getStatus());
            appendEvent(order.getId(), order.getMerchantOrderId(), "LATE_SUCCESS_RECOVERY_ATTEMPT", order.getStatus().name());

            // Step 1: Validate amount (single source of truth for tolerance: app.checkout.amount-tolerance)
            BigDecimal lateAmount = parseAmount(amountStr);
            BigDecimal amtTolerance = checkoutProperties.getAmountTolerance();
            if (lateAmount == null || order.getTotalAmount().setScale(2, RoundingMode.HALF_UP).subtract(lateAmount).abs().compareTo(amtTolerance) > 0) {
                log.error("Late success amount mismatch for order {}: expected {} got {}", order.getId(), order.getTotalAmount(), amountStr);
                persistGatewayPaymentOrIgnore(order, trackingId, amountStr, params, encResp, clientIp,
                        CheckoutGatewayPaymentRecordStatus.CAPTURED, paymentMode, statusMessage);
                recordGuaranteedRefund(order, amountStr, "Late success with amount mismatch");
                return;
            }

            // P1.4: Stop synthesizing tracking IDs. A synthetic ID lets a future REAL callback
            // create a duplicate gateway-payment row for the same captured charge — corrupts the
            // ledger. Money was captured but we cannot create a payment row without a real ID.
            // Path forward: log SEV-1, write a guaranteed refund (PENDING_GATEWAY) without the
            // ccaTrackingId so the lifecycle service / admin can reconcile manually, and DO NOT
            // promote to PAID. Reconciliation polling will surface the real tracking_id later
            // (it can then be backfilled by an admin endpoint, but never silently invented).
            if (trackingId == null || trackingId.isBlank()) {
                log.error("LATE-SUCCESS-MISSING-TRACKING orderId={} merchantOrderId={} amount={} — refund-only, awaiting reconcile",
                        order.getId(), order.getMerchantOrderId(), amountStr);
                appendEvent(order.getId(), order.getMerchantOrderId(),
                        "LATE_SUCCESS_MISSING_TRACKING_REFUND", amountStr);
                recordGuaranteedRefund(order, amountStr,
                        "Late payment success without tracking_id — refund pending; manual reconcile required");
                return;
            }

            // Step 3: Record the gateway payment
            CheckoutGatewayPayment payment = null;
            try {
                payment = persistGatewayPaymentOrThrow(order, trackingId, amountStr, params, encResp, clientIp,
                        CheckoutGatewayPaymentRecordStatus.CAPTURED, paymentMode, statusMessage);
            } catch (DataIntegrityViolationException dup) {
                log.info("Late success: duplicate tracking for order {} — already processed", order.getId());
                return;
            }

            // Step 4: Attempt inventory recovery (re-reserve + consume)
            boolean inventoryRecovered = tryRecoverInventoryForLateSuccess(order);

            if (inventoryRecovered) {
                // Promote to PAID — inventory is available and consumed
                order.setStatus(CheckoutOrderStatus.PAID);
                order.setPaidAt(LocalDateTime.now());
                order.setReservationExpiresAt(null);
                order.setFailReason(null);
                order.setFailureReason(null);
                order.setUpdatedAt(LocalDateTime.now());
                // Un-cancel lines that were successfully fulfilled
                orderRepository.save(order);
                log.info("Late success RECOVERY: checkout order {} promoted to PAID", order.getId());
                appendEvent(order.getId(), order.getMerchantOrderId(), "LATE_SUCCESS_RECOVERED_TO_PAID", null);

                // Phase 2: Ledger Integration (Late Success)
                ledgerService.recordPayment(order.getId(), payment != null ? payment.getId() : null, lateAmount, trackingId, LedgerSource.WEBHOOK, LedgerEntryStatus.SUCCESS, "Late success recovery");
            } else {
                // Inventory unavailable — money captured but can't fulfill. Refund is mandatory.
                log.warn("Late success: inventory unavailable for order {} — creating guaranteed refund", order.getId());
                recordGuaranteedRefund(order, amountStr, "Late payment success — inventory no longer available");
                appendEvent(order.getId(), order.getMerchantOrderId(), "LATE_SUCCESS_REFUND_CREATED", amountStr);
            }
            return;
        }

        BigDecimal amount = parseAmount(amountStr);
        // P3.3: tolerance pulled from CheckoutProperties so callback + late-success paths cannot drift.
        BigDecimal amountTolerance = checkoutProperties.getAmountTolerance();
        if (amount == null || order.getTotalAmount().setScale(2, RoundingMode.HALF_UP).subtract(amount).abs().compareTo(amountTolerance) > 0) {
            log.error("Amount mismatch for checkout order {}: expected {} got {} (tolerance={})", order.getId(), order.getTotalAmount(), amountStr, amountTolerance);
            appendEvent(order.getId(), order.getMerchantOrderId(), "CALLBACK_AMOUNT_MISMATCH", amountStr);
            return;
        }

        if (trackingId == null || trackingId.isBlank()) {
            // P1.4: Do NOT synthesize tracking_id (would let a real later callback create a
            // duplicate row for the same charge). Keep the order in PAYMENT_PENDING, append
            // an event, and extend the reservation TTL by a grace window so the reconciliation
            // poller (which calls CCAvenue's order-status API) can fetch the real tracking_id
            // and re-enter this method via applyReconciliationOutcome.
            log.error("MISSING-TRACKING-ID-AT-CALLBACK orderId={} merchantOrderId={} — staying PAYMENT_PENDING, awaiting reconcile",
                    order.getId(), order.getMerchantOrderId());
            appendEvent(order.getId(), order.getMerchantOrderId(), "CALLBACK_MISSING_TRACKING_ID", amountStr);
            int graceMin = Math.max(checkoutProperties.getReconcileOrderAgeMinutes() * 3, 30);
            LocalDateTime newExpiry = LocalDateTime.now().plusMinutes(graceMin);
            if (order.getReservationExpiresAt() == null || order.getReservationExpiresAt().isBefore(newExpiry)) {
                order.setReservationExpiresAt(newExpiry);
            }
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            return;
        }

        CheckoutGatewayPayment payment = null;
        try {
            payment = persistGatewayPaymentOrThrow(order, trackingId, amountStr, params, encResp, clientIp,
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

        // Phase 2: Ledger Integration (Normal Success)
        ledgerService.recordPayment(order.getId(), payment != null ? payment.getId() : null, amount, trackingId, LedgerSource.WEBHOOK, LedgerEntryStatus.SUCCESS, "Standard payment capture");

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

        CheckoutGatewayPayment payment = null;
        if (trackingId != null && !trackingId.isBlank()) {
            payment = persistGatewayPaymentOrIgnore(order, trackingId, amountStr, Map.of(), encResp, clientIp,
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

        // Phase 2: Ledger Integration (Payment Failure)
        if (amountStr != null && !amountStr.isBlank()) {
            BigDecimal failedAmt = parseAmount(amountStr);
            if (failedAmt != null) {
                ledgerService.recordPayment(order.getId(), payment != null ? payment.getId() : null, failedAmt, trackingId, LedgerSource.WEBHOOK, LedgerEntryStatus.FAILED, "Payment failure: " + statusMessage);
            }
        }

        log.info("checkout order {} marked FAILED ({})", order.getId(), normalizedStatus);
    }

    private CheckoutGatewayPayment persistGatewayPaymentOrThrow(CheckoutOrder order,
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
        return gatewayPaymentRepository.save(row);
    }

    private CheckoutGatewayPayment persistGatewayPaymentOrIgnore(CheckoutOrder order,
                                               String trackingId,
                                               String amountStr,
                                               Map<String, String> params,
                                               String encResp,
                                               String clientIp,
                                               CheckoutGatewayPaymentRecordStatus status,
                                               String paymentMode,
                                               String statusMessage) {
        if (trackingId == null || trackingId.isBlank()) {
            return null;
        }
        if (gatewayPaymentRepository.existsByTrackingId(trackingId)) {
            return gatewayPaymentRepository.findByTrackingId(trackingId).orElse(null);
        }
        try {
            return persistGatewayPaymentOrThrow(order, trackingId, amountStr, params, encResp, clientIp, status, paymentMode, statusMessage);
        } catch (DataIntegrityViolationException ignored) {
            return gatewayPaymentRepository.findByTrackingId(trackingId).orElse(null);
        }
    }

    /**
     * EC-04/05/06: Try to re-reserve and consume inventory for a late-success order.
     * Since reservations were already RELEASED, we attempt fresh atomic reserve+consume per line.
     * Returns true if ALL lines recovered, false if any line failed (partial recovery is rolled back).
     */
    private boolean tryRecoverInventoryForLateSuccess(CheckoutOrder order) {
        if (order.getLines() == null || order.getLines().isEmpty()) {
            return true;
        }
        List<Long> reservedPids = new ArrayList<>();
        try {
            for (CheckoutOrderLine line : order.getLines()) {
                if (line.getFulfillmentStatus() == CheckoutLineFulfillmentStatus.CANCELLED
                        || line.getFulfillmentStatus() == CheckoutLineFulfillmentStatus.PENDING) {
                    int reserved = inventoryRepository.increaseReservedStock(line.getProductId(), line.getQuantity());
                    if (reserved == 0) {
                        log.warn("Late success recovery: insufficient stock for product {} qty {}",
                                line.getProductId(), line.getQuantity());
                        // Rollback any reservations we already made
                        for (Long pid : reservedPids) {
                            // Find the matching line to get quantity
                            order.getLines().stream()
                                    .filter(l -> l.getProductId().equals(pid))
                                    .findFirst()
                                    .ifPresent(l -> inventoryRepository.decreaseReservedStock(pid, l.getQuantity()));
                        }
                        return false;
                    }
                    reservedPids.add(line.getProductId());
                }
            }
            // All reservations succeeded — now consume
            for (CheckoutOrderLine line : order.getLines()) {
                if (line.getFulfillmentStatus() == CheckoutLineFulfillmentStatus.CANCELLED
                        || line.getFulfillmentStatus() == CheckoutLineFulfillmentStatus.PENDING) {
                    int consumed = inventoryRepository.consumeReservedAndDecreaseOnHand(line.getProductId(), line.getQuantity());
                    if (consumed > 0) {
                        line.setFulfillmentStatus(CheckoutLineFulfillmentStatus.FULFILLED);
                    } else {
                        line.setFulfillmentStatus(CheckoutLineFulfillmentStatus.OUT_OF_STOCK);
                    }
                }
            }
            return true;
        } catch (Exception ex) {
            log.error("Late success inventory recovery failed for order {}: {}", order.getId(), ex.getMessage());
            // Best-effort rollback
            for (Long pid : reservedPids) {
                try {
                    order.getLines().stream()
                            .filter(l -> l.getProductId().equals(pid))
                            .findFirst()
                            .ifPresent(l -> inventoryRepository.decreaseReservedStock(pid, l.getQuantity()));
                } catch (Exception rollbackEx) {
                    log.error("Rollback failed for product {}: {}", pid, rollbackEx.getMessage());
                }
            }
            return false;
        }
    }

    /**
     * EC-04/05/06: Hardened refund creation for late-success scenarios.
     * Hardened refund creation that wraps the event publish in try-catch
     * and logs explicitly to ensure the refund record is ALWAYS persisted to DB
     * even if the Spring event bus fails.
     */
    private void recordGuaranteedRefund(CheckoutOrder order, String amountStr, String reason) {
        BigDecimal amt = parseAmount(amountStr);
        if (amt == null) {
            amt = order.getTotalAmount();
        }
        CheckoutRefund refund = CheckoutRefund.builder()
                .order(order)
                .amount(amt)
                .reason(truncate(reason, 480))
                .status(CheckoutRefundRecordStatus.PENDING_GATEWAY)
                .ccaTrackingId(resolveCcaTrackingId(order))
                .build();
        CheckoutRefund saved = refundRepository.save(refund);
        order.setRefundNote(truncate(reason, 480));
        order.setRefundTotalAmount(amt);
        orderRepository.save(order);
        log.info("Guaranteed refund created: orderId={} refundId={} amount={} reason={}",
                order.getId(), saved.getId(), amt, reason);
        try {
            applicationEventPublisher.publishEvent(new RefundCreatedEvent(saved.getId()));
        } catch (Exception ex) {
            // Refund record is saved — reconciliation scheduler will pick it up even if event fails
            log.error("RefundCreatedEvent publish failed for refund {} — reconciliation will handle it: {}",
                    saved.getId(), ex.getMessage());
        }
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

    private void restoreConsumedStock(CheckoutOrder order) {
        for (CheckoutReservation r : reservationRepository.findByOrderIdAndStatus(order.getId(), CheckoutReservationStatus.CONSUMED)) {
            Product p = productRepository.findById(r.getProductId()).orElse(null);
            if (p != null && p.getStockOnHand() != null) {
                inventoryRepository.restoreStockOnHand(r.getProductId(), r.getQuantity());
            }
            r.setStatus(CheckoutReservationStatus.RELEASED);
            reservationRepository.save(r);
        }
    }

    /** EC-26 Fix: Mark PENDING and FULFILLED line items as CANCELLED when the order dies.
     *  SHIPPED/DELIVERED lines are left as-is (physically dispatched — handled by refund flow). */
    private void cancelOrderLines(CheckoutOrder order) {
        if (order.getLines() == null) return;
        for (CheckoutOrderLine line : order.getLines()) {
            if (line.getFulfillmentStatus() == CheckoutLineFulfillmentStatus.PENDING
                    || line.getFulfillmentStatus() == CheckoutLineFulfillmentStatus.FULFILLED) {
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

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * P3.1: Pure BigDecimal arithmetic — never round-trips through {@code double} (which silently
     * loses the second decimal on values like 19.99 × 0.07). Discount is clamped to [0,100].
     */
    private BigDecimal unitSellingPrice(Product p) {
        BigDecimal price = p.getPrice() != null
                ? BigDecimal.valueOf(p.getPrice())
                : BigDecimal.ZERO;
        BigDecimal discountPct = p.getOffers() != null
                ? BigDecimal.valueOf(p.getOffers())
                : BigDecimal.ZERO;
        if (discountPct.compareTo(BigDecimal.ZERO) < 0) {
            discountPct = BigDecimal.ZERO;
        } else if (discountPct.compareTo(HUNDRED) > 0) {
            discountPct = HUNDRED;
        }
        BigDecimal discountAmount = price.multiply(discountPct).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        return price.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
    }

    private InitiateCheckoutPaymentResponse buildInitiateResponse(CheckoutOrder order) {
        String redirect = firstNonBlank(checkoutProperties.getCcavenueRedirectUrl(), ccAvenueConfig.getRedirectUrl());
        String cancel = firstNonBlank(checkoutProperties.getCcavenueCancelUrl(), ccAvenueConfig.getCancelUrl());
        cancel = appendSignedCancelHint(cancel, order.getMerchantOrderId());
        CcAvenuePaymentRequest paymentRequest = CcAvenuePaymentRequest.builder()
                .orderId(order.getMerchantOrderId())
                .amount(order.getTotalAmount())
                .redirectUrl(redirect)
                .cancelUrl(cancel)
                .billingName(order.getCustomerName())
                .billingAddress(order.getDeliveryAddress())
                .billingTel(order.getContactNumber())
                .build();
        com.spring.jwt.Payment.CcAvenuePaymentFormFields formFields =
                ccAvenuePaymentService.buildPaymentFormFields(paymentRequest);
        String legacyHtml = ccAvenuePaymentService.generatePaymentForm(paymentRequest);
        return InitiateCheckoutPaymentResponse.builder()
                .orderId(order.getId())
                .merchantOrderId(order.getMerchantOrderId())
                .paymentFormHtml(legacyHtml)
                .paymentForm(InitiateCheckoutPaymentResponse.PaymentForm.builder()
                        .actionUrl(formFields.getActionUrl())
                        .fields(formFields.getFields())
                        .build())
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

    /**
     * Per-JVM fallback secret used if {@code app.checkout.cancel-hint-secret} is not configured.
     * Hints stay valid only for this JVM lifetime, which still beats the legacy global-pick
     * behavior. Production deployments MUST configure the property to keep hints valid across
     * rolling restarts. Value is generated once on first use using {@link java.security.SecureRandom}.
     */
    private static final java.util.concurrent.atomic.AtomicReference<String> EPHEMERAL_CANCEL_HINT_SECRET =
            new java.util.concurrent.atomic.AtomicReference<>();

    /**
     * Append an HMAC-signed {@code cmref} hint to the cancel URL so that an empty cancel
     * callback (CCAvenue 10002) can deterministically identify the originating order. Falls back
     * to an in-process secret with a loud warning when {@code app.checkout.cancel-hint-secret}
     * is not configured — keeps existing deploys alive while making misconfiguration visible.
     */
    private String appendSignedCancelHint(String cancelUrl, String merchantOrderId) {
        if (cancelUrl == null || cancelUrl.isBlank() || merchantOrderId == null || merchantOrderId.isBlank()) {
            return cancelUrl;
        }
        String secret = resolveCancelHintSecret();
        String token = CheckoutCancelHint.issue(merchantOrderId, secret);
        char join = cancelUrl.indexOf('?') >= 0 ? '&' : '?';
        return cancelUrl + join + "cmref=" + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
    }

    String resolveCancelHintSecret() {
        String secret = checkoutProperties.getCancelHintSecret();
        if (secret != null && !secret.isBlank()) {
            return secret;
        }
        String existing = EPHEMERAL_CANCEL_HINT_SECRET.get();
        if (existing != null) {
            return existing;
        }
        byte[] buf = new byte[32];
        new java.security.SecureRandom().nextBytes(buf);
        String generated = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        if (EPHEMERAL_CANCEL_HINT_SECRET.compareAndSet(null, generated)) {
            log.error("CONFIG: app.checkout.cancel-hint-secret is not configured — using ephemeral per-JVM secret. " +
                    "Configure a stable secret in application properties for production (cancel hints become invalid across restarts otherwise).");
            return generated;
        }
        return EPHEMERAL_CANCEL_HINT_SECRET.get();
    }

    @Override
    public java.util.Optional<String> verifyCancelHint(String token) {
        if (token == null || token.isBlank()) {
            return java.util.Optional.empty();
        }
        return CheckoutCancelHint.verify(token, resolveCancelHintSecret());
    }
}
