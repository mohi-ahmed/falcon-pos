package com.spark.falcon.cashmanagement.exception;

public class CashLocationNotFoundException extends RuntimeException {
    public CashLocationNotFoundException() {
        super("Cash location not found");
    }
}
