package com.spark.falcon.supplier.exception;

public class SupplierAccessDeniedException extends RuntimeException {
    public SupplierAccessDeniedException() {
        super("Supplier access denied");
    }
}
