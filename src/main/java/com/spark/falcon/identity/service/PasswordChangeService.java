package com.spark.falcon.identity.service;

import com.spark.falcon.identity.audit.SecurityAuditEventType;
import com.spark.falcon.identity.entity.Owner;
import com.spark.falcon.identity.entity.PasswordResetToken;
import com.spark.falcon.identity.exception.PasswordResetException;
import com.spark.falcon.identity.repository.OwnerRepository;
import com.spark.falcon.identity.repository.PasswordResetTokenRepository;
import com.spark.falcon.identity.security.AuthenticatedSessionRevocationService;
import com.spark.falcon.identity.security.PasswordHistoryService;
import com.spark.falcon.identity.security.PasswordPolicy;
import com.spark.falcon.identity.usecase.ResetPasswordUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PasswordChangeService implements ResetPasswordUseCase {
    private final OwnerRepository ownerRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHistoryService passwordHistoryService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetAuditRecorder auditRecorder;
    private final AuthenticatedSessionRevocationService sessionRevocationService;
    private final Clock clock;

    @Override
    @Transactional
    public void reset(String email, String newPassword) {
        Owner owner = ownerRepository.findByEmailIgnoreCase(email).orElseThrow(this::invalidSession);
        PasswordResetToken token = tokenRepository.findByOwner(owner).orElseThrow(this::invalidSession);
        Instant now = Instant.now(clock);
        if (!token.isResetAllowedAt(now)) throw invalidSession();

        passwordPolicy.validate(newPassword);
        passwordHistoryService.validateNotReused(owner, newPassword);
        passwordHistoryService.recordCurrentPassword(owner, now);
        owner.changePassword(passwordEncoder.encode(newPassword), now);
        token.consume(now);

        auditRecorder.recordAfterCommit(SecurityAuditEventType.PASSWORD_RESET_COMPLETED,
                owner.getEmail(), "Password reset completed");
        sessionRevocationService.revokeAllForOwner(owner.getId());
    }

    private PasswordResetException invalidSession() {
        return new PasswordResetException(PasswordResetException.Reason.INVALID_SESSION,
                "Password reset session is invalid or expired");
    }
}
