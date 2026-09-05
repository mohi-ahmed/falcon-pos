package com.spark.falcon.inventory.dto;

import com.spark.falcon.inventory.entity.InventoryActorType;
import com.spark.falcon.inventory.entity.StockMovementType;
import com.spark.falcon.inventory.entity.StockSourceType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class StockReceiptRequest {
    private Long businessId;
    private Long branchId;
    private Long productVariantId;

    private BigDecimal enteredQuantity;
    private Long enteredUnitId;
    private LocalDate transactionDate;

    private BigDecimal landedBaseUnitCost;
    private BigDecimal originalPurchaseUnitCost;

    private Long supplierId;
    private Long sourcePurchaseItemId;
    private String batchNumber;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;

    private StockMovementType movementType;
    private StockSourceType sourceType;
    private String sourceReferenceId;
    private String sourceLineReference;
    private String postingKey;

    private InventoryActorType actorType;
    private Long actorId;
    private String reason;
    private String notes;
}
