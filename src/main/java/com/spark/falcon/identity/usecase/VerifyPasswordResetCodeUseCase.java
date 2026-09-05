package com.spark.falcon.identity.usecase;

public interface VerifyPasswordResetCodeUseCase {

    void verify(String email, String code);
}