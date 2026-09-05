package com.spark.falcon.product.exception;

public class ProductVariantNotFoundException extends RuntimeException {
    public ProductVariantNotFoundException() {
        super("Product variant was not found");
    }
}
