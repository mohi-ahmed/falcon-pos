package com.spark.falcon.identity.usecase;

public interface IssueEmailVerificationUseCase {
    void issue(String email);
    void resend(String email);
}
