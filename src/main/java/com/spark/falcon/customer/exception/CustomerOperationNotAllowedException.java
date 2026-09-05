package com.spark.falcon.customer.exception;

public class CustomerOperationNotAllowedException extends RuntimeException {
    public CustomerOperationNotAllowedException(String message) {
        super(message);
    }
}
