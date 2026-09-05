package com.spark.falcon.identity.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "owners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "mobile_number", length = 32)
    private String mobileNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OwnerStatus status;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static Owner pendingVerification(
            String fullName,
            String normalizedEmail,
            String passwordHash,
            String mobileNumber,
            Instant now
    ) {
        Owner owner = new Owner();
        owner.fullName = requireText(fullName, "fullName").trim();
        owner.email = normalizeEmail(normalizedEmail);
        owner.passwordHash = requireText(passwordHash, "passwordHash");
        owner.mobileNumber = normalizeOptional(mobileNumber);
        owner.status = OwnerStatus.PENDING_EMAIL_VERIFICATION;
        owner.createdAt = Objects.requireNonNull(now, "now");
        owner.updatedAt = now;
        return owner;
    }

    public void verifyEmail(Instant now) {
        if (status != OwnerStatus.PENDING_EMAIL_VERIFICATION) {
            throw new IllegalStateException("Only a pending owner can verify an email");
        }
        emailVerifiedAt = Objects.requireNonNull(now, "now");
        status = OwnerStatus.ACTIVE;
        updatedAt = now;
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public void changePassword(String newPasswordHash, Instant now) {
        passwordHash = requireText(newPasswordHash, "newPasswordHash");
        updatedAt = Objects.requireNonNull(now, "now");

        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public boolean canAuthenticateAt(Instant now) {
        return status == OwnerStatus.ACTIVE
                && (lockedUntil == null || !lockedUntil.isAfter(now));
    }

    private static String normalizeEmail(String value) {
        return requireText(value, "email").trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

