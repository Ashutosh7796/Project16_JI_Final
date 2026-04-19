package com.spring.jwt.checkout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckoutGatewayPaymentRepository extends JpaRepository<CheckoutGatewayPayment, Long> {

    boolean existsByTrackingId(String trackingId);

    Optional<CheckoutGatewayPayment> findByTrackingId(String trackingId);

    List<CheckoutGatewayPayment> findByOrder_IdOrderByIdDesc(Long orderId);
}
