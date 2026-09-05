package com.spark.falcon.product.exception;

public class ProductImageStorageException extends RuntimeException {
    public ProductImageStorageException(String message) { super(message); }
    public ProductImageStorageException(String message, Throwable cause) { super(message, cause); }
}
