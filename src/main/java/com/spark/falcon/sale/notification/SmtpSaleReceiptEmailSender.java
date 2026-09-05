package com.spark.falcon.sale.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "falcon.mail.delivery", havingValue = "smtp")
@RequiredArgsConstructor
class SmtpSaleReceiptEmailSender implements SaleReceiptEmailSender {

    private final JavaMailSender mailSender;

    @Value("${falcon.mail.from}")
    private String from;

    @Override
    public void send(String recipientEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
