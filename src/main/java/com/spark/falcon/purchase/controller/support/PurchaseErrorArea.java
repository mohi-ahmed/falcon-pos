package com.spark.falcon.purchase.controller.support;

public enum PurchaseErrorArea {
    FORM("form"),
    ITEMS("items"),
    PAYMENT("payment"),
    SETTLEMENT("settlement");

    private final String modelValue;

    PurchaseErrorArea(String modelValue) {
        this.modelValue = modelValue;
    }

    public String modelValue() {
        return modelValue;
    }
}
