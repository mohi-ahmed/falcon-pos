package com.spark.falcon.identity.usecase;

public interface VerifyEmailUseCase {
    void verify(String email, String rawCode);
}
