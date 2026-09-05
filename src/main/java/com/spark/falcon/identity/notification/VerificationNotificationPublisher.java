package com.spark.falcon.identity.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificationNotificationPublisher {
    private final ApplicationEventPublisher eventPublisher;

    public void publish(String email, String code, int expiryMinutes) {
        eventPublisher.publishEvent(new VerificationCodeIssuedEvent(email, code, expiryMinutes));
    }
}
