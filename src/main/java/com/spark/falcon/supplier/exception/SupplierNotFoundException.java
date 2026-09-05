package com.spark.falcon.supplier.exception;

public class SupplierNotFoundException extends RuntimeException {
    public SupplierNotFoundException() {
        super("Supplier not found");
    }
}
