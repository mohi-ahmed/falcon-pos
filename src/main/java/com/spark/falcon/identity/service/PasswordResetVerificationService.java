package com.spark.falcon.identity.service;

import com.spark.falcon.identity.audit.SecurityAuditEventType;
import com.spark.falcon.identity.entity.Owner;
import com.spark.falcon.identity.entity.PasswordResetToken;
import com.spark.falcon.identity.exception.PasswordResetException;
import com.spark.falcon.identity.repository.OwnerRepository;
import com.spark.falcon.identity.repository.PasswordResetTokenRepository;
import com.spark.falcon.identity.security.VerificationCodeDigest;
import com.spark.falcon.identity.usecase.VerifyPasswordResetCodeUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PasswordResetVerificationService implements VerifyPasswordResetCodeUseCase {
    private final OwnerRepository ownerRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final VerificationCodeDigest codeDigest;
    private final PasswordResetAttemptService attemptService;
    private final PasswordResetAuditRecorder auditRecorder;
    private final Clock clock;

    @Override
    @Transactional
    public void verify(String email, String code) {
        Owner owner = ownerRepository.findByEmailIgnoreCase(email).orElseThrow(this::invalidCode);
        PasswordResetToken token = tokenRepository.findByOwner(owner).orElseThrow(this::invalidCode);
        Instant now = Instant.now(clock);

        if (token.getFailedAttempts() >= PasswordResetToken.MAX_ATTEMPTS) {
            fail(owner, PasswordResetException.Reason.ATTEMPT_LIMIT_REACHED,
                    "Too many incorrect attempts. Request a new verification code.", "Attempt limit reached");
        }
        if (!now.isBefore(token.getExpiresAt())) {
            fail(owner, PasswordResetException.Reason.EXPIRED_CODE,
                    "This verification code has expired. Request a new code.", "Code expired");
        }
        if (token.getConsumedAt() != null || token.getVerifiedAt() != null) {
            fail(owner, PasswordResetException.Reason.INVALID_CODE,
                    "This verification code is no longer valid.", "Unusable code");
        }
        if (!codeDigest.matches(code, token.getCodeHash())) {
            attemptService.recordFailure(token.getId());
            int attempts = token.getFailedAttempts() + 1;
            PasswordResetException.Reason reason = attempts >= PasswordResetToken.MAX_ATTEMPTS
                    ? PasswordResetException.Reason.ATTEMPT_LIMIT_REACHED
                    : PasswordResetException.Reason.INVALID_CODE;
            fail(owner, reason, attempts >= PasswordResetToken.MAX_ATTEMPTS
                    ? "Too many incorrect attempts. Request a new verification code."
                    : "The verification code is incorrect.", "Incorrect code");
        }

        token.verify(now);
        auditRecorder.recordAfterCommit(SecurityAuditEventType.PASSWORD_RESET_VERIFIED,
                owner.getEmail(), "Password reset verification succeeded");
    }

    private void fail(Owner owner, PasswordResetException.Reason reason, String message, String auditDetail) {
        auditRecorder.record(SecurityAuditEventType.PASSWORD_RESET_FAILED, owner.getEmail(), auditDetail);
        throw new PasswordResetException(reason, message);
    }

    private PasswordResetException invalidCode() {
        return new PasswordResetException(PasswordResetException.Reason.INVALID_CODE,
                "The verification code is incorrect or no longer valid.");
    }
}
