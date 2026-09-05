package com.spark.falcon.purchase.exception;

public class StockImportNotFoundException extends RuntimeException {
    public StockImportNotFoundException() {
        super("Stock Import Batch was not found");
    }
}
