package com.spark.falcon.purchase.dto.response;

import com.spark.falcon.purchase.entity.enumtype.StockImportRowStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockImportRowResponse(
        Long id,
        int rowNumber,
        Long productVariantId,
        String variantSku,
        Long enteredUnitId,
        BigDecimal enteredQuantity,
        BigDecimal conversionFactor,
        Long baseInventoryUnitId,
        BigDecimal baseQuantity,
        BigDecimal currentSystemQuantity,
        BigDecimal quantityDifference,
        BigDecimal originalPurchaseUnitCost,
        BigDecimal landedBaseUnitCost,
        String batchNumber,
        LocalDate manufacturingDate,
        LocalDate expiryDate,
        StockImportRowStatus status,
        String validationMessage,
        Long postedMovementId,
        BigDecimal resultingQuantity,
        BigDecimal inventoryValueChange) {
}
