package com.spark.falcon.identity.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "email_verification_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationToken {
    public static final int MAX_ATTEMPTS = 5;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    @Column(name = "consumed_at")
    private Instant consumedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Version private long version;

    public static EmailVerificationToken issue(Owner owner, String codeHash, Instant now,
                                                Instant expiresAt, Instant resendAvailableAt) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.owner = Objects.requireNonNull(owner);
        token.codeHash = requireText(codeHash);
        token.createdAt = Objects.requireNonNull(now);
        token.expiresAt = Objects.requireNonNull(expiresAt);
        token.resendAvailableAt = Objects.requireNonNull(resendAvailableAt);
        return token;
    }

    public void replace(String newHash, Instant now, Instant newExpiry, Instant nextResend) {
        if (now.isBefore(resendAvailableAt)) throw new IllegalStateException("Verification code resend is not available yet");
        codeHash = requireText(newHash);
        createdAt = now;
        expiresAt = newExpiry;
        resendAvailableAt = nextResend;
        failedAttempts = 0;
        consumedAt = null;
    }

    public boolean isUsableAt(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt) && failedAttempts < MAX_ATTEMPTS;
    }

    public void recordFailure() { failedAttempts++; }
    public void consume(Instant now) { consumedAt = Objects.requireNonNull(now); }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("value must not be blank");
        return value;
    }
}
