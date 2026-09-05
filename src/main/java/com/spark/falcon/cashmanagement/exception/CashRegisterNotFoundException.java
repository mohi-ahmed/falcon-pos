package com.spark.falcon.cashmanagement.exception;

public class CashRegisterNotFoundException extends RuntimeException {
    public CashRegisterNotFoundException() {
        super("Cash register not found");
    }
}
