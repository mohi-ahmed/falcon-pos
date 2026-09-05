package com.spark.falcon.identity.usecase;

public interface ResetPasswordUseCase {

    void reset(String email, String newPassword);
}