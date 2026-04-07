package com.spring.jwt.ProductBuyPending;

import com.spring.jwt.Enums.DeliveryStatus;
import com.spring.jwt.Payment.CcAvenuePaymentService;
import com.spring.jwt.Payment.OrderHistoryRepository;
import com.spring.jwt.Product.ProductRepository;
import com.spring.jwt.ProductBuyConfirmed.ProductBuyConfirmedDto;
import com.spring.jwt.ProductBuyConfirmed.ProductBuyConfirmedRepository;
import com.spring.jwt.entity.*;
import com.spring.jwt.Enums.PaymentMode;
import com.spring.jwt.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductBuyServiceImpl implements ProductBuyService {

    private final ProductRepository productRepo;
    private final ProductBuyPendingRepository pendingRepo;
    private final ProductBuyConfirmedRepository confirmedRepo;
    private final CcAvenuePaymentService ccAvenuePaymentService;
    private final OrderHistoryRepository orderHistoryRepository;

    // 🔥 CREATE ORDER + PAYMENT INIT
    @Override
    public ProductBuyPendingDto createPendingOrder(CreateOrderRequestDto dto) {

        Product product = productRepo.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new RuntimeException("Product is not active");
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
        pending.setPaymentStatus("PENDING");

        pending.setCustomerName(dto.getCustomerName());
        pending.setContactNumber(dto.getContactNumber());
        pending.setDeliveryAddress(dto.getDeliveryAddress());

        pendingRepo.save(pending);

        // 🔥 5. Generate CCAvenue Payment Form
        String paymentForm = ccAvenuePaymentService.generatePaymentForm(pending);

        ProductBuyPendingDto response = mapToPendingDto(pending);
        response.setPaymentGatewayOrderId(paymentForm); // HTML form

        return response;
    }

    // 🔥 AUTO CALLED AFTER PAYMENT SUCCESS
    @Override
    public ProductBuyConfirmedDto confirmPayment(PaymentVerifyDto dto) {

        ProductBuyPending pending = pendingRepo.findById(dto.getPendingOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Pending order not found"));

        if ("SUCCESS".equals(pending.getPaymentStatus())) {
            throw new RuntimeException("Payment already processed");
        }
        pending.setPaymentStatus("SUCCESS");
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

        return mapToConfirmedDto(confirmed);
    }

    @Override
    public void markPaymentFailed(Long pendingOrderId) {

        ProductBuyPending pending = pendingRepo.findById(pendingOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        pending.setPaymentStatus("FAILED");
        pendingRepo.save(pending);
    }
    private void createDelivery(ProductBuyConfirmed order) {

        DeliveryTracking tracking = new DeliveryTracking();
        tracking.setOrder(order);
        tracking.setStatus(DeliveryStatus.ORDER_PLACED);
        tracking.setCurrentLocation("Warehouse");

        // save delivery
        // deliveryRepo.save(tracking);

        saveHistory(order.getId(), "SHIPPED", "Order ready for shipment");
    }

    // 🔁 HISTORY METHOD
    private void saveHistory(Long orderId, String status, String msg) {
        OrderHistory history = new OrderHistory();
        history.setOrderId(orderId);
        history.setStatus(status);
        history.setDescription(msg);
        orderHistoryRepository.save(history);
    }
    // 📦 GET CONFIRMED
    @Override
    public ProductBuyConfirmedDto getConfirmedOrder(Long id) {

        ProductBuyConfirmed confirmed = confirmedRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        return mapToConfirmedDto(confirmed);
    }

    // 📦 GET PENDING
    @Override
    public ProductBuyPendingDto getPendingOrder(Long id) {

        ProductBuyPending pending = pendingRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pending order not found"));

        return mapToPendingDto(pending);
    }

    // 🔁 MAPPERS

    private ProductBuyPendingDto mapToPendingDto(ProductBuyPending e) {
        return new ProductBuyPendingDto(
                e.getProductBuyPendingId(),
                e.getUserId(),
                e.getProductId(),
                e.getQuantity(),
                e.getTotalAmount(),
                e.getPaymentStatus(),
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