package com.spark.falcon.identity.service;

import com.spark.falcon.identity.dto.OwnerRegistrationResult;
import com.spark.falcon.identity.dto.RegisterOwnerCommand;
import com.spark.falcon.identity.entity.EmailVerificationToken;
import com.spark.falcon.identity.entity.Owner;
import com.spark.falcon.identity.exception.EmailAlreadyRegisteredException;
import com.spark.falcon.identity.notification.VerificationNotificationPublisher;
import com.spark.falcon.identity.repository.EmailVerificationTokenRepository;
import com.spark.falcon.identity.repository.OwnerRepository;
import com.spark.falcon.identity.security.PasswordPolicy;
import com.spark.falcon.identity.security.VerificationCodeDigest;
import com.spark.falcon.identity.usecase.RegisterOwnerUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerRegistrationService implements RegisterOwnerUseCase {
    private static final Duration CODE_EXPIRY = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);

    private final OwnerRepository ownerRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final VerificationCodeGenerator codeGenerator;
    private final VerificationCodeDigest codeDigest;
    private final VerificationNotificationPublisher notificationPublisher;
    private final Clock clock;

    @Override
    @Transactional
    public OwnerRegistrationResult register(RegisterOwnerCommand command) {
        Objects.requireNonNull(command, "command");
        String email = normalizeEmail(command.email());
        passwordPolicy.validate(command.rawPassword());
        rejectDuplicateEmail(email);

        Instant now = Instant.now(clock);
        Owner owner = Owner.pendingVerification(command.fullName(), email,
                passwordEncoder.encode(command.rawPassword()), command.mobileNumber(), now);
        Owner savedOwner = saveOwner(owner);

        String rawCode = codeGenerator.generate();
        tokenRepository.save(EmailVerificationToken.issue(savedOwner, codeDigest.hash(rawCode), now,
                now.plus(CODE_EXPIRY), now.plus(RESEND_COOLDOWN)));
        notificationPublisher.publish(savedOwner.getEmail(), rawCode, (int) CODE_EXPIRY.toMinutes());

        log.info("Owner registration created: ownerId={}, status={}", savedOwner.getId(), savedOwner.getStatus());
        return new OwnerRegistrationResult(savedOwner.getId(), savedOwner.getEmail(), savedOwner.getStatus());
    }

    private void rejectDuplicateEmail(String email) {
        if (ownerRepository.existsByEmailIgnoreCase(email)) throw new EmailAlreadyRegisteredException();
    }

    private Owner saveOwner(Owner owner) {
        try {
            return ownerRepository.saveAndFlush(owner);
        } catch (DataIntegrityViolationException exception) {
            log.warn("Owner registration rejected by a database uniqueness constraint");
            throw new EmailAlreadyRegisteredException();
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required");
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
