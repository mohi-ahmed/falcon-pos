package com.spark.falcon.sale.notification;

public interface SaleReceiptEmailSender {
    void send(String recipientEmail, String subject, String body);
}
