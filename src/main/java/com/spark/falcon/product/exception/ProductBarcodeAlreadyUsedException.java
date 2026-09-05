package com.spark.falcon.product.exception;

public class ProductBarcodeAlreadyUsedException extends RuntimeException {
    public ProductBarcodeAlreadyUsedException(String barcode) {
        super("Active barcode is already assigned: " + barcode);
    }
}
