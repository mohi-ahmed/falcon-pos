package com.spark.falcon.product.exception;

public class ProductConfigurationConflictException extends RuntimeException {
    public ProductConfigurationConflictException(String message) {
        super(message);
    }
}
