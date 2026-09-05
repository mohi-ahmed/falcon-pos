package com.spark.falcon.payment.exception;

public class PaymentAccessDeniedException extends RuntimeException {
    public PaymentAccessDeniedException() {
        super("Payment access is not allowed for this business or branch");
    }
}
