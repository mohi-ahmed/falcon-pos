package com.spark.falcon.sale.dto;

import com.spark.falcon.sale.entity.SaleReturnItemCondition;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SaleReturnItemResponse(
        Long id,
        Long saleItemId,
        Long saleItemBatchAllocationId,
        Long productVariantId,
        Long productBatchId,
        String batchNumber,
        LocalDate expiryDate,
        BigDecimal enteredReturnQuantity,
        Long returnUnitId,
        BigDecimal conversionFactor,
        Long baseInventoryUnitId,
        BigDecimal baseQuantity,
        BigDecimal returnAmount,
        BigDecimal preservedFinancialCostSnapshot,
        SaleReturnItemCondition condition,
        String reason,
        Long stockMovementId
) {
}
