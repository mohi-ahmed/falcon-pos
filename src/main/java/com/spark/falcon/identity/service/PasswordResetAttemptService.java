package com.spark.falcon.identity.service;

import com.spark.falcon.identity.entity.PasswordResetToken;
import com.spark.falcon.identity.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetAttemptService {

    private final PasswordResetTokenRepository tokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long tokenId) {
        PasswordResetToken token = tokenRepository.findById(tokenId)
                .orElseThrow();

        token.recordFailure();
    }
}