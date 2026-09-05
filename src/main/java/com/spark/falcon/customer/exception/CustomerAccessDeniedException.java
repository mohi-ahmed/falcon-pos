package com.spark.falcon.customer.exception;

public class CustomerAccessDeniedException extends RuntimeException {
    public CustomerAccessDeniedException() {
        super("Customer access is not allowed for the current business context.");
    }
}
