package com.spark.falcon.sale.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "falcon.mail.delivery", havingValue = "log", matchIfMissing = true)
@Slf4j
class LocalSaleReceiptEmailSender implements SaleReceiptEmailSender {

    @Override
    public void send(String recipientEmail, String subject, String body) {
        log.info("LOCAL ONLY — Falcon sale receipt email to={} subject={}\n{}", recipientEmail, subject, body);
    }
}
