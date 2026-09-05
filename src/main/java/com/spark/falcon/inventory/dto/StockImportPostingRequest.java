package com.spark.falcon.inventory.dto;

import com.spark.falcon.inventory.entity.InventoryActorType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class StockImportPostingRequest {
    private Long businessId;
    private Long branchId;
    private Long productVariantId;

    private BigDecimal enteredQuantity;
    private Long enteredUnitId;
    private BigDecimal conversionFactorSnapshot;
    private Long baseInventoryUnitIdSnapshot;
    private BigDecimal baseQuantityChange;

    private BigDecimal landedBaseUnitCost;
    private BigDecimal originalPurchaseUnitCost;

    private Long existingProductBatchId;
    private String batchNumber;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
    private boolean nonSellableImport;

    private String sourceReferenceId;
    private String sourceLineReference;
    private String postingKey;

    private InventoryActorType actorType;
    private Long actorId;
    private String reason;
    private String notes;
}
