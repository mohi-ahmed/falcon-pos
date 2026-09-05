package com.spark.falcon.identity.repository;

import com.spark.falcon.identity.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByOwnerEmailIgnoreCase(String email);
    Optional<EmailVerificationToken> findByOwnerId(Long ownerId);
}
