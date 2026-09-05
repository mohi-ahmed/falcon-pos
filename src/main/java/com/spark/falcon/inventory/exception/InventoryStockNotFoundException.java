package com.spark.falcon.inventory.exception;

public class InventoryStockNotFoundException extends RuntimeException {
    public InventoryStockNotFoundException() {
        super("Inventory stock not found");
    }
}
