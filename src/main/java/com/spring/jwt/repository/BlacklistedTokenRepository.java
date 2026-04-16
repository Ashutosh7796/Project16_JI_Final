package com.spring.jwt.repository;

import com.spring.jwt.entity.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {

    /** Check if a token JTI is blacklisted */
    boolean existsByTokenId(String tokenId);

    /** Find blacklisted entry by JTI */
    Optional<BlacklistedToken> findByTokenId(String tokenId);

    /** Purge expired entries — safe to call on schedule */
    @Modifying
    @Transactional
    @Query("DELETE FROM BlacklistedToken t WHERE t.expiresAt < :now")
    int deleteExpiredTokens(Instant now);

    /** Increment reuse attempt counter */
    @Modifying
    @Transactional
    @Query("UPDATE BlacklistedToken t SET t.reuseAttempts = t.reuseAttempts + 1 WHERE t.tokenId = :tokenId")
    void incrementReuseAttempts(String tokenId);
}
