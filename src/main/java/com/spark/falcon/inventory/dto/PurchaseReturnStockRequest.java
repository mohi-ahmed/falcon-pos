package com.spark.falcon.inventory.dto;

import com.spark.falcon.inventory.entity.InventoryActorType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class PurchaseReturnStockRequest {
    private Long businessId;
    private Long branchId;
    private Long productVariantId;
    private Long productBatchId;

    private BigDecimal enteredReturnQuantity;
    private Long returnUnitId;
    private BigDecimal conversionFactorSnapshot;
    private Long baseInventoryUnitIdSnapshot;
    private BigDecimal baseQuantity;

    private BigDecimal preservedAllocatedLandedUnitCost;

    private String sourceReferenceId;
    private String sourceLineReference;
    private String postingKey;

    private InventoryActorType actorType;
    private Long actorId;
    private String reason;
    private String notes;
}
