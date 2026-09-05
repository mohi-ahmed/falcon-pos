package com.spark.falcon.identity.service;

import com.spark.falcon.identity.entity.EmailVerificationToken;
import com.spark.falcon.identity.entity.Owner;
import com.spark.falcon.identity.entity.OwnerStatus;
import com.spark.falcon.identity.exception.EmailVerificationException;
import com.spark.falcon.identity.notification.VerificationNotificationPublisher;
import com.spark.falcon.identity.repository.EmailVerificationTokenRepository;
import com.spark.falcon.identity.repository.OwnerRepository;
import com.spark.falcon.identity.security.VerificationCodeDigest;
import com.spark.falcon.identity.usecase.IssueEmailVerificationUseCase;
import com.spark.falcon.identity.usecase.VerifyEmailUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService implements IssueEmailVerificationUseCase, VerifyEmailUseCase {
    private static final Duration EXPIRY = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);

    private final OwnerRepository ownerRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final VerificationNotificationPublisher notificationPublisher;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationCodeDigest codeDigest;
    private final Clock clock;

    @Override
    @Transactional
    public void issue(String email) {
        Owner owner = pendingOwner(email);
        EmailVerificationToken existing = tokenRepository.findByOwnerId(owner.getId()).orElse(null);
        if (existing == null) {
            createToken(owner);
            return;
        }
        replaceToken(owner, existing);
    }

    @Override
    @Transactional
    public void resend(String email) {
        Owner owner = pendingOwner(email);
        EmailVerificationToken token = tokenRepository.findByOwnerId(owner.getId())
                .orElseThrow(() -> new EmailVerificationException("Request a new verification code."));
        replaceToken(owner, token);
    }

    @Override
    @Transactional
    public void verify(String email, String rawCode) {
        EmailVerificationToken token = tokenRepository.findByOwnerEmailIgnoreCase(email)
                .orElseThrow(() -> new EmailVerificationException("The verification request is invalid."));
        Instant now = Instant.now(clock);
        if (!token.isUsableAt(now)) {
            throw new EmailVerificationException("The code expired or the attempt limit was reached. Request a new code.");
        }
        if (!codeDigest.matches(rawCode, token.getCodeHash())) {
            token.recordFailure();
            throw new EmailVerificationException("The verification code is incorrect.");
        }
        token.getOwner().verifyEmail(now);
        token.consume(now);
        log.info("Owner email verified: ownerId={}", token.getOwner().getId());
    }

    private void createToken(Owner owner) {
        Instant now = Instant.now(clock);
        String rawCode = codeGenerator.generate();
        tokenRepository.save(EmailVerificationToken.issue(owner, codeDigest.hash(rawCode), now,
                now.plus(EXPIRY), now.plus(RESEND_COOLDOWN)));
        publish(owner, rawCode);
    }

    private void replaceToken(Owner owner, EmailVerificationToken token) {
        Instant now = Instant.now(clock);
        String rawCode = codeGenerator.generate();
        try {
            token.replace(codeDigest.hash(rawCode), now, now.plus(EXPIRY), now.plus(RESEND_COOLDOWN));
        } catch (IllegalStateException exception) {
            throw new EmailVerificationException("Please wait before requesting another code.");
        }
        publish(owner, rawCode);
    }

    private void publish(Owner owner, String rawCode) {
        notificationPublisher.publish(owner.getEmail(), rawCode, (int) EXPIRY.toMinutes());
        log.info("Email verification scheduled: ownerId={}", owner.getId());
    }

    private Owner pendingOwner(String email) {
        Owner owner = ownerRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new EmailVerificationException("The verification request is invalid."));
        if (owner.getStatus() != OwnerStatus.PENDING_EMAIL_VERIFICATION) {
            throw new EmailVerificationException("This email is already verified or unavailable.");
        }
        return owner;
    }
}
