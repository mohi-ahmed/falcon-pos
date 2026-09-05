package com.spark.falcon.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SaleReturnStockPostingRequest(
        Long businessId, Long branchId, Long productVariantId, Long productBatchId,
        BigDecimal enteredReturnQuantity, Long returnUnitId, BigDecimal conversionFactor,
        Long baseInventoryUnitId, BigDecimal baseQuantity, BigDecimal preservedFinancialCostSnapshot,
        LocalDate returnDate, Long saleReturnId, Long saleReturnItemId, boolean sellable,
        String postingKey, Long actorId, String reason, String notes) {
}
