package com.spark.falcon.inventory.dto;

import com.spark.falcon.inventory.entity.InventoryActorType;
import com.spark.falcon.inventory.entity.StockMovementType;
import com.spark.falcon.inventory.entity.StockSourceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponse {
    private Long id;
    private Long businessId;
    private Long branchId;
    private Long branchProductStockId;
    private Long productVariantId;
    private Long productBatchId;
    private StockMovementType movementType;
    private BigDecimal enteredQuantity;
    private Long enteredUnitId;
    private BigDecimal conversionFactor;
    private Long baseInventoryUnitId;
    private BigDecimal baseQuantityChange;
    private BigDecimal quantityBefore;
    private BigDecimal quantityAfter;
    private BigDecimal financialUnitCostSnapshot;
    private BigDecimal inventoryValueChange;
    private StockSourceType sourceType;
    private String sourceReferenceId;
    private String sourceLineReference;
    private String postingKey;
    private Long reversalOfMovementId;
    private String reason;
    private String notes;
    private InventoryActorType postedByActorType;
    private Long postedByActorId;
    private Instant postedAt;
}
