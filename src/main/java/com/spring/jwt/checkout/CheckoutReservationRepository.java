package com.spring.jwt.checkout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CheckoutReservationRepository extends JpaRepository<CheckoutReservation, Long> {

    @Query("SELECT r FROM CheckoutReservation r JOIN FETCH r.orderLine l WHERE l.order.id = :orderId AND r.status = :status")
    List<CheckoutReservation> findByOrderIdAndStatus(@Param("orderId") Long orderId, @Param("status") CheckoutReservationStatus status);
}
