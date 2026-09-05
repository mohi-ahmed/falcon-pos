package com.spark.falcon.identity.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class VerificationEmailEventListener {
    private final VerificationEmailSender emailSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationCodeIssued(VerificationCodeIssuedEvent event) {
        emailSender.sendCode(event.recipientEmail(), event.code(), event.expiryMinutes());
    }
}
