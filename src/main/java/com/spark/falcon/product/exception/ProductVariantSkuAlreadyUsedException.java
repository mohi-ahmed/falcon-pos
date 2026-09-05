package com.spark.falcon.product.exception;

public class ProductVariantSkuAlreadyUsedException extends RuntimeException {
    public ProductVariantSkuAlreadyUsedException(String sku) {
        super("Product variant SKU is already used for this product: " + sku);
    }
}
