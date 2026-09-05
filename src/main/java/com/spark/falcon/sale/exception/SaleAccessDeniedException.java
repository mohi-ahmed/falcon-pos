package com.spark.falcon.sale.exception;

public class SaleAccessDeniedException extends RuntimeException {
    public SaleAccessDeniedException() {
        super("You do not have access to this Sale or Branch");
    }
}
