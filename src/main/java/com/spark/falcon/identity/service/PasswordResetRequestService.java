package com.spark.falcon.identity.service;

import com.spark.falcon.identity.audit.SecurityAuditEventType;
import com.spark.falcon.identity.entity.Owner;
import com.spark.falcon.identity.entity.PasswordResetToken;
import com.spark.falcon.identity.notification.VerificationNotificationPublisher;
import com.spark.falcon.identity.repository.OwnerRepository;
import com.spark.falcon.identity.repository.PasswordResetTokenRepository;
import com.spark.falcon.identity.security.VerificationCodeDigest;
import com.spark.falcon.identity.usecase.RequestPasswordResetUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PasswordResetRequestService implements RequestPasswordResetUseCase {
    private static final Duration EXPIRY = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(4);

    private final OwnerRepository ownerRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationCodeDigest codeDigest;
    private final VerificationNotificationPublisher notificationPublisher;
    private final PasswordResetAuditRecorder auditRecorder;
    private final Clock clock;

    @Override
    @Transactional
    public void request(String email) {
        ownerRepository.findByEmailIgnoreCase(email).ifPresent(this::issueWithoutLeakingAccountExistence);
    }

    @Override
    @Transactional
    public ResendResult resend(String email) {
        Owner owner = ownerRepository.findByEmailIgnoreCase(email).orElse(null);
        if (owner == null) return ResendResult.SENT;
        PasswordResetToken token = tokenRepository.findByOwner(owner).orElse(null);
        if (token == null) {
            issueNew(owner);
            return ResendResult.SENT;
        }
        return replace(owner, token) ? ResendResult.SENT : ResendResult.COOLDOWN;
    }

    private void issueWithoutLeakingAccountExistence(Owner owner) {
        PasswordResetToken token = tokenRepository.findByOwner(owner).orElse(null);
        if (token == null) issueNew(owner); else replace(owner, token);
    }

    private void issueNew(Owner owner) {
        Instant now = Instant.now(clock);
        String rawCode = codeGenerator.generate();
        tokenRepository.save(PasswordResetToken.issue(owner, codeDigest.hash(rawCode), now,
                now.plus(EXPIRY), now.plus(RESEND_COOLDOWN)));
        publishAndAudit(owner, rawCode);
    }

    private boolean replace(Owner owner, PasswordResetToken token) {
        Instant now = Instant.now(clock);
        String rawCode = codeGenerator.generate();
        try {
            token.replace(codeDigest.hash(rawCode), now, now.plus(EXPIRY), now.plus(RESEND_COOLDOWN));
        } catch (IllegalStateException exception) {
            return false;
        }
        publishAndAudit(owner, rawCode);
        return true;
    }

    private void publishAndAudit(Owner owner, String rawCode) {
        notificationPublisher.publish(owner.getEmail(), rawCode, (int) EXPIRY.toMinutes());
        auditRecorder.recordAfterCommit(SecurityAuditEventType.PASSWORD_RESET_REQUESTED,
                owner.getEmail(), "Password reset requested");
    }
}
