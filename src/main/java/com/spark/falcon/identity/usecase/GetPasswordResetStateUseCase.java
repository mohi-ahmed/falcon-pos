package com.spark.falcon.identity.usecase;

public interface GetPasswordResetStateUseCase {

    PasswordResetState getState(String email);

    record PasswordResetState(
            int remainingAttempts,
            boolean locked,
            long resendRemainingSeconds
    ) {
    }
}