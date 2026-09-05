package com.spark.falcon.inventory.entity;

public enum StockMovementType {
    PURCHASE_RECEIPT,
    PURCHASE_RETURN,
    STOCK_IMPORT,
    SALE,
    SALE_RETURN,
    STOCK_ADJUSTMENT_INCREASE,
    STOCK_ADJUSTMENT_DECREASE,
    TRANSFER_OUT,
    TRANSFER_IN,
    INVENTORY_LOSS,
    REVERSAL
}
