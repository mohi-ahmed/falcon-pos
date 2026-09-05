package com.spark.falcon.purchase.exception;

public class PurchaseNotFoundException extends RuntimeException {
    public PurchaseNotFoundException() {
        super("Purchase was not found");
    }
}
