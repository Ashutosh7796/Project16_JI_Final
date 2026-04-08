package com.spring.jwt.ProductBuyPending;

import com.spring.jwt.Enums.PaymentStatus;
import com.spring.jwt.Payment.CcAvenuePaymentRequest;
import com.spring.jwt.Payment.CcAvenuePaymentService;
import com.spring.jwt.Product.ProductRepository;
import com.spring.jwt.ProductBuyConfirmed.ProductBuyConfirmedDto;
import com.spring.jwt.ProductBuyConfirmed.ProductBuyConfirmedRepository;
import com.spring.jwt.entity.*;
import com.spring.jwt.Enums.PaymentMode;
import com.spring.jwt.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductBuyServiceImpl implements ProductBuyService {

    private final ProductRepository productRepo;
    private final ProductBuyPendingRepository pendingRepo;
    private final ProductBuyConfirmedRepository confirmedRepo;
    private final CcAvenuePaymentService ccAvenuePaymentService;

    @Override
    @Transactional
    public ProductBuyPendingDto createPendingOrder(CreateOrderRequestDto dto) {
        Product product = productRepo.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalStateException("Product is not active");
        }

        double price = product.getPrice();
        double discount = product.getOffers() != null ? product.getOffers() : 0;
        double finalPrice = price - (price * discount / 100);
        double totalAmount = finalPrice * dto.getQuantity();

        ProductBuyPending pending = new ProductBuyPending();
        pending.setUserId(dto.getUserId());
        pending.setProductId(product.getProductId());
        pending.setQuantity(dto.getQuantity());
        pending.setTotalAmount(totalAmount);
        pending.setPaymentStatus(PaymentStatus.PENDING);
        pending.setCustomerName(dto.getCustomerName());
        pending.setContactNumber(dto.getContactNumber());
        pending.setDeliveryAddress(dto.getDeliveryAddress());

        pendingRepo.save(pending);

        CcAvenuePaymentRequest paymentRequest = CcAvenuePaymentRequest.builder()
                .orderId("PROD-" + pending.getProductBuyPendingId())
                .amount(BigDecimal.valueOf(totalAmount))
                .billingName(dto.getCustomerName())
                .billingAddress(dto.getDeliveryAddress())
                .billingTel(dto.getContactNumber())
                .build();

        String paymentForm = ccAvenuePaymentService.generatePaymentForm(paymentRequest);

        pending.setPaymentGatewayOrderId("PROD-" + pending.getProductBuyPendingId());
        pendingRepo.save(pending);

        log.info("Created pending order {} for product {}", pending.getProductBuyPendingId(), product.getProductId());

        ProductBuyPendingDto response = mapToPendingDto(pending);
        response.setPaymentGatewayOrderId(paymentForm);

        return response;
    }

    @Override
    @Transactional
    public ProductBuyConfirmedDto confirmPayment(PaymentVerifyDto dto) {
        ProductBuyPending pending = pendingRepo.findById(dto.getPendingOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Pending order not found"));

        if (PaymentStatus.SUCCESS.equals(pending.getPaymentStatus())) {
            throw new IllegalStateException("Payment already processed");
        }

        pending.setPaymentStatus(PaymentStatus.SUCCESS);
        pendingRepo.save(pending);

        ProductBuyConfirmed confirmed = new ProductBuyConfirmed();
        confirmed.setUserId(pending.getUserId());
        confirmed.setProductId(pending.getProductId());
        confirmed.setQuantity(pending.getQuantity());
        confirmed.setTotalAmount(pending.getTotalAmount());
        confirmed.setPaymentId(dto.getPaymentId());
        confirmed.setPaymentMode(PaymentMode.valueOf(dto.getPaymentMode()));
        confirmed.setPaymentDate(LocalDateTime.now());
        confirmed.setCustomerName(pending.getCustomerName());
        confirmed.setContactNumber(pending.getContactNumber());
        confirmed.setDeliveryAddress(pending.getDeliveryAddress());
        confirmedRepo.save(confirmed);

        log.info("Payment confirmed for order {}", pending.getProductBuyPendingId());

        return mapToConfirmedDto(confirmed);
    }

    @Override
    @Transactional
    public void markPaymentFailed(Long pendingOrderId) {
        ProductBuyPending pending = pendingRepo.findById(pendingOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        pending.setPaymentStatus(PaymentStatus.FAILED);
        pendingRepo.save(pending);

        log.info("Payment marked failed for order {}", pendingOrderId);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductBuyConfirmedDto getConfirmedOrder(Long id) {
        ProductBuyConfirmed confirmed = confirmedRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return mapToConfirmedDto(confirmed);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductBuyPendingDto getPendingOrder(Long id) {
        ProductBuyPending pending = pendingRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pending order not found"));
        return mapToPendingDto(pending);
    }

    private ProductBuyPendingDto mapToPendingDto(ProductBuyPending e) {
        return new ProductBuyPendingDto(
                e.getProductBuyPendingId(),
                e.getUserId(),
                e.getProductId(),
                e.getQuantity(),
                e.getTotalAmount(),
                e.getPaymentStatus() != null ? e.getPaymentStatus().name() : null,
                e.getPaymentGatewayOrderId(),
                e.getCreatedAt(),
                e.getDeliveryAddress(),
                e.getCustomerName(),
                e.getContactNumber()
        );
    }

    private ProductBuyConfirmedDto mapToConfirmedDto(ProductBuyConfirmed e) {
        ProductBuyConfirmedDto dto = new ProductBuyConfirmedDto();
        dto.setId(e.getId());
        dto.setUserId(e.getUserId());
        dto.setProductId(e.getProductId());
        dto.setQuantity(e.getQuantity());
        dto.setTotalAmount(e.getTotalAmount());
        dto.setPaymentId(e.getPaymentId());
        dto.setPaymentMode(e.getPaymentMode());
        dto.setPaymentDate(e.getPaymentDate());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setCustomerName(e.getCustomerName());
        dto.setContactNumber(e.getContactNumber());
        dto.setDeliveryAddress(e.getDeliveryAddress());
        dto.setDeliveryCreated(e.getDeliveryCreated());
        if (e.getProduct() != null) {
            dto.setProductName(e.getProduct().getProductName());
        }
        return dto;
    }
}
