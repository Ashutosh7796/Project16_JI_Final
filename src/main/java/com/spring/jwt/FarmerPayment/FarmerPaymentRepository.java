package com.spring.jwt.FarmerPayment;

import com.spring.jwt.Enums.PaymentStatus;
import com.spring.jwt.entity.FarmerPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FarmerPaymentRepository extends JpaRepository<FarmerPayment, Long> {

    Optional<FarmerPayment> findByIdempotencyKey(String idempotencyKey);

    Optional<FarmerPayment> findByCcavenueOrderId(String ccavenueOrderId);

    Optional<FarmerPayment> findBySurvey_SurveyIdAndPaymentStatus(Long surveyId, PaymentStatus status);

    Page<FarmerPayment> findBySurvey_SurveyIdOrderByCreatedAtDesc(Long surveyId, Pageable pageable);

    Page<FarmerPayment> findByUser_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<FarmerPayment> findBySurvey_SurveyIdInAndPaymentStatusOrderByCreatedAtDesc(
            Collection<Long> surveyIds,
            PaymentStatus paymentStatus
    );

    @Query("SELECT COUNT(fp) FROM FarmerPayment fp WHERE fp.survey.surveyId = :surveyId " +
            "AND fp.createdAt > :since")
    long countRecentBySurvey(@Param("surveyId") Long surveyId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(fp) FROM FarmerPayment fp WHERE fp.user.userId = :userId " +
            "AND fp.createdAt > :since")
    long countRecentByUser(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
