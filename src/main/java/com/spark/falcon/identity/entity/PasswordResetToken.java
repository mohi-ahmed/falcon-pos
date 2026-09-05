package com.spark.falcon.identity.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private Owner owner;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resend_available_at", nullable = false)
    private Instant resendAvailableAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    public static PasswordResetToken issue(
            Owner owner,
            String codeHash,
            Instant now,
            Instant expiresAt,
            Instant resendAvailableAt
    ) {
        PasswordResetToken token = new PasswordResetToken();

        token.owner = Objects.requireNonNull(owner);
        token.codeHash = requireText(codeHash);
        token.createdAt = Objects.requireNonNull(now);
        token.expiresAt = Objects.requireNonNull(expiresAt);
        token.resendAvailableAt = Objects.requireNonNull(resendAvailableAt);

        return token;
    }

    public void replace(
            String newHash,
            Instant now,
            Instant newExpiry,
            Instant nextResend
    ) {
        if (now.isBefore(resendAvailableAt)) {
            throw new IllegalStateException(
                    "Password reset code resend is not available yet"
            );
        }

        codeHash = requireText(newHash);
        createdAt = now;
        expiresAt = newExpiry;
        resendAvailableAt = nextResend;
        failedAttempts = 0;
        verifiedAt = null;
        consumedAt = null;
    }

    public boolean isUsableAt(Instant now) {
        return consumedAt == null
                && verifiedAt == null
                && now.isBefore(expiresAt)
                && failedAttempts < MAX_ATTEMPTS;
    }

    public boolean isResetAllowedAt(Instant now) {
        return verifiedAt != null
                && consumedAt == null
                && now.isBefore(expiresAt);
    }

    public void recordFailure() {
        failedAttempts++;
    }

    public void verify(Instant now) {
        verifiedAt = Objects.requireNonNull(now);
    }

    public void consume(Instant now) {
        consumedAt = Objects.requireNonNull(now);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }

        return value;
    }
}