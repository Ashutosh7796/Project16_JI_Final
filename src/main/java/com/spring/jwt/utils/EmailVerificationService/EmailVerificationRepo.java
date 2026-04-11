package com.spring.jwt.utils.EmailVerificationService;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailVerificationRepo extends JpaRepository<EmailVerification, Integer> {

    Optional<EmailVerification> findByEmail(String email);

    List<EmailVerification> findByCreationTimeBefore(LocalDateTime dateTime);

    @Transactional
    @Modifying
    @Query("DELETE FROM EmailVerification e WHERE e.status = :status AND e.creationTime < :expirationTime")
    int deleteByStatusAndCreationTimeBefore(@Param("status") String status, @Param("expirationTime") LocalDateTime expirationTime);

    List<EmailVerification> findByStatusAndCreationTimeBefore(String statusNotVerified, LocalDateTime expiryThreshold);
}