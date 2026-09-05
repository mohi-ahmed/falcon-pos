package com.spark.falcon.identity.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "falcon.mail.delivery", havingValue = "smtp")
@RequiredArgsConstructor
class SmtpVerificationEmailSender implements VerificationEmailSender {
    private final JavaMailSender mailSender;
    @Value("${falcon.mail.from}") private String from;

    @Override
    public void sendCode(String recipientEmail, String code, int expiryMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipientEmail);
        message.setSubject("Verify your Falcon POS owner account");
        message.setText("Your Falcon POS verification code is " + code
                + ". It expires in " + expiryMinutes + " minutes. Never share this code.");
        mailSender.send(message);
    }
}
