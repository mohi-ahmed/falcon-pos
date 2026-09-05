package com.spark.falcon.purchase.dto.response;

import java.math.BigDecimal;

public record PurchaseReturnItemResponse(
        Long id,
        Long purchaseItemId,
        Long productVariantId,
        Long productBatchId,
        BigDecimal enteredReturnQuantity,
        Long returnUnitId,
        BigDecimal conversionFactor,
        Long baseInventoryUnitId,
        BigDecimal baseQuantity,
        BigDecimal supplierUnitCostSnapshot,
        BigDecimal allocatedLandedUnitCostSnapshot,
        BigDecimal returnAmount,
        Long stockMovementId) {
}
