package com.spark.falcon.sale.exception;

public class SaleReturnNotFoundException extends RuntimeException {
    public SaleReturnNotFoundException() {
        super("Sale Return was not found");
    }
}
