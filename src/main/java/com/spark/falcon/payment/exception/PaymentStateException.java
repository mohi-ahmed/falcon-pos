package com.spark.falcon.payment.exception;

public class PaymentStateException extends RuntimeException {
    public PaymentStateException(String message) {
        super(message);
    }
}
