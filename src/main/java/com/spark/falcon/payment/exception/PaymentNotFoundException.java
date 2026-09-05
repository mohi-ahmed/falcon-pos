package com.spark.falcon.payment.exception;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException() {
        super("Payment was not found");
    }
}
