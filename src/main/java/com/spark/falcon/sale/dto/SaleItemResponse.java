package com.spark.falcon.sale.dto;

import java.math.BigDecimal;
import java.util.List;

public record SaleItemResponse(
        Long id,
        Long productVariantId,
        String productName,
        String variantName,
        String productCode,
        BigDecimal enteredQuantity,
        Long enteredUnitId,
        BigDecimal conversionFactor,
        Long baseInventoryUnitId,
        BigDecimal baseQuantity,
        BigDecimal unitPrice,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal taxRate,
        String taxMethod,
        BigDecimal taxAmount,
        BigDecimal linePayable,
        Long stockMovementId,
        BigDecimal weightedAverageCostSnapshot,
        BigDecimal cogsAmount,
        BigDecimal returnedBaseQuantity,
        List<SaleBatchAllocationResponse> batchAllocations
) {
}
