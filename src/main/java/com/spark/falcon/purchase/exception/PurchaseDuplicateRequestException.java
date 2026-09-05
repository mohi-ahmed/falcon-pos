package com.spark.falcon.purchase.exception;

public class PurchaseDuplicateRequestException extends RuntimeException {
    public PurchaseDuplicateRequestException() {
        super("This purchase request has already been processed");
    }
}
