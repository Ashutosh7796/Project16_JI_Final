package com.spring.jwt.checkout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckoutOrderLineRepository extends JpaRepository<CheckoutOrderLine, Long> {

    List<CheckoutOrderLine> findByOrder_Id(Long orderId);
}
