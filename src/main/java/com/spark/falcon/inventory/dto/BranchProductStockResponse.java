package com.spark.falcon.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchProductStockResponse {
    private Long id;
    private Long businessId;
    private Long branchId;
    private Long productVariantId;
    private Long baseInventoryUnitId;
    private BigDecimal baseQuantity;
    private BigDecimal reservedBaseQuantity;
    private BigDecimal availableBaseQuantity;
    private BigDecimal weightedAverageCost;
    private BigDecimal inventoryValue;
    private Instant updatedAt;
}
