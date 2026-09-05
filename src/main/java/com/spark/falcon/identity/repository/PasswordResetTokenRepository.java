package com.spark.falcon.identity.repository;

import com.spark.falcon.identity.entity.Owner;
import com.spark.falcon.identity.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByOwner(Owner owner);

    Optional<PasswordResetToken> findByOwnerId(Long ownerId);
}