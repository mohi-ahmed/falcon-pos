package com.spark.falcon.inventory.dto;

import com.spark.falcon.inventory.entity.InventoryActorType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StockMovementReversalRequest {
    private Long businessId;
    private Long branchId;
    private Long originalMovementId;
    private String sourceReferenceId;
    private String sourceLineReference;
    private String postingKey;
    private InventoryActorType actorType;
    private Long actorId;
    private String reason;
    private String notes;
}
