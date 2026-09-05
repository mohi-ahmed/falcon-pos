package com.spark.falcon.purchase.exception;

public class PurchaseReturnNotFoundException extends RuntimeException {
    public PurchaseReturnNotFoundException() {
        super("Purchase return was not found");
    }
}
