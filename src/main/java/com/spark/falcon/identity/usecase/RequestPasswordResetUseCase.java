package com.spark.falcon.identity.usecase;

public interface RequestPasswordResetUseCase {

    void request(String email);

    ResendResult resend(String email);

    enum ResendResult {
        SENT,
        COOLDOWN
    }
}