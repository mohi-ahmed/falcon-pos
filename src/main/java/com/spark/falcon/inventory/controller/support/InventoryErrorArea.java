package com.spark.falcon.inventory.controller.support;

public enum InventoryErrorArea {
    FORM("form"),
    ITEMS("items"),
    ROW("row"),
    RECEIPT("receipt"),
    RESOLUTION("resolution"),
    WORKFLOW("workflow"),
    FILTER("filter");

    private final String modelValue;

    InventoryErrorArea(String modelValue) {
        this.modelValue = modelValue;
    }

    public String modelValue() {
        return modelValue;
    }
}
