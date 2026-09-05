package com.spark.falcon.purchase.exception;

public class PurchaseAccessDeniedException extends RuntimeException {
    public PurchaseAccessDeniedException() {
        super("Purchase access is denied");
    }
}
