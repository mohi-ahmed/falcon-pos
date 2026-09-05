package com.spark.falcon.purchase.dto.response;

import java.math.BigDecimal;

public record SupplierReturnProjectionResponse(
        long expiryBatchPurchaseReturnCount,
        BigDecimal supplierRefundAmount,
        BigDecimal supplierCreditAmount
) {
    public BigDecimal refundAndCreditAmount() {
        return supplierRefundAmount.add(supplierCreditAmount);
    }
}
