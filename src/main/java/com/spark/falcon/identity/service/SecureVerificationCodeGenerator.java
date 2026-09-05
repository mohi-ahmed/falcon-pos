package com.spark.falcon.identity.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
class SecureVerificationCodeGenerator implements VerificationCodeGenerator {
    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }
}
