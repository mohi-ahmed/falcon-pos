package com.spark.falcon.identity.service;

import com.spark.falcon.identity.entity.Owner;
import com.spark.falcon.identity.entity.PasswordResetToken;
import com.spark.falcon.identity.repository.OwnerRepository;
import com.spark.falcon.identity.repository.PasswordResetTokenRepository;
import com.spark.falcon.identity.usecase.GetPasswordResetStateUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PasswordResetStateQueryService implements GetPasswordResetStateUseCase {
    private static final Duration INITIAL_RESEND_COOLDOWN = Duration.ofMinutes(4);
    private final OwnerRepository ownerRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PasswordResetState getState(String email) {
        Owner owner = ownerRepository.findByEmailIgnoreCase(email).orElse(null);
        if (owner == null) return initialState();
        PasswordResetToken token = tokenRepository.findByOwner(owner).orElse(null);
        if (token == null) return initialState();

        int remaining = Math.max(0, PasswordResetToken.MAX_ATTEMPTS - token.getFailedAttempts());
        long resendSeconds = Math.max(0,
                Duration.between(Instant.now(clock), token.getResendAvailableAt()).getSeconds());
        return new PasswordResetState(remaining, remaining == 0, resendSeconds);
    }

    private PasswordResetState initialState() {
        return new PasswordResetState(PasswordResetToken.MAX_ATTEMPTS, false,
                INITIAL_RESEND_COOLDOWN.toSeconds());
    }
}
