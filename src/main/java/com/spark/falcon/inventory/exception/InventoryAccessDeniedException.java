package com.spark.falcon.inventory.exception;

public class InventoryAccessDeniedException extends RuntimeException {
    public InventoryAccessDeniedException() {
        super("Inventory access denied");
    }
}
