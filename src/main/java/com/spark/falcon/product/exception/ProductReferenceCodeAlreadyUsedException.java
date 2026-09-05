package com.spark.falcon.product.exception;

public class ProductReferenceCodeAlreadyUsedException extends RuntimeException {
    public ProductReferenceCodeAlreadyUsedException(String code) {
        super("Product reference code is already used: " + code);
    }
}
