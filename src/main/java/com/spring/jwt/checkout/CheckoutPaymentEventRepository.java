package com.spring.jwt.checkout;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutPaymentEventRepository extends JpaRepository<CheckoutPaymentEvent, Long> {
}
