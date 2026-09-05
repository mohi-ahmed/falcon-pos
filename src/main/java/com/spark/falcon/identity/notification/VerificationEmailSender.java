package com.spark.falcon.identity.notification;

public interface VerificationEmailSender {
    void sendCode(String recipientEmail, String code, int expiryMinutes);
}
