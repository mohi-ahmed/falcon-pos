package com.spark.falcon.purchase.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseItemResponse(
        Long id,
        Long purchaseId,
        Long productVariantId,
        BigDecimal enteredQuantity,
        Long enteredUnitId,
        BigDecimal conversionFactor,
        Long baseInventoryUnitId,
        BigDecimal baseQuantity,
        BigDecimal unitCost,
        BigDecimal intendedSellingPrice,
        BigDecimal itemTax,
        BigDecimal itemDiscount,
        BigDecimal lineAmount,
        BigDecimal allocatedOrderTax,
        BigDecimal allocatedPurchaseDiscount,
        BigDecimal allocatedShipping,
        BigDecimal allocatedOtherCharges,
        BigDecimal landedInventoryAmount,
        BigDecimal baseUnitLandedCost,
        String batchNumber,
        LocalDate manufacturingDate,
        LocalDate expiryDate,
        Long productBatchId,
        Long stockMovementId,
        BigDecimal weightedAverageCostAfterConfirmation) {
}
