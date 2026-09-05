package com.spark.falcon.customer.exception;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException() {
        super("Customer was not found.");
    }
}
