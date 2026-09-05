package com.spark.falcon.sale.exception;

public class SaleNotFoundException extends RuntimeException {
    public SaleNotFoundException() {
        super("Sale was not found");
    }
}
