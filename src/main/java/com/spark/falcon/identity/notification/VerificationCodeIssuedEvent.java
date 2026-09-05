package com.spark.falcon.identity.notification;

public record VerificationCodeIssuedEvent(
        String recipientEmail,
        String code,
        int expiryMinutes
) {
}
