package com.spark.falcon.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SaleStockPostingRequest(
        Long businessId, Long branchId, Long productVariantId,
        BigDecimal enteredQuantity, Long enteredUnitId, BigDecimal conversionFactor,
        Long baseInventoryUnitId, BigDecimal baseQuantity, LocalDate saleDate,
        Long saleId, Long saleItemId, String postingKey, Long actorId, String notes) {
}
