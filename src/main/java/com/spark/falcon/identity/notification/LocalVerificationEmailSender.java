package com.spark.falcon.identity.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "falcon.mail.delivery", havingValue = "log", matchIfMissing = true)
@Slf4j
class LocalVerificationEmailSender implements VerificationEmailSender {
    @Override
    public void sendCode(String recipientEmail, String code, int expiryMinutes) {
        log.info("LOCAL ONLY — Falcon verification for {}: code={}, expiresIn={}m",
                recipientEmail, code, expiryMinutes);
    }
}
