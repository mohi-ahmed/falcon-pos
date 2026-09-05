package com.spark.falcon.inventory.dto;
import com.spark.falcon.inventory.entity.*;
import java.math.BigDecimal;

public record OperationalStockChangeRequest(Long businessId, Long branchId, Long productVariantId,
        Long productBatchId, BigDecimal enteredQuantity, Long enteredUnitId, BigDecimal conversionFactor,
        BigDecimal baseQuantity, boolean increase, BigDecimal preservedUnitCost, StockMovementType movementType,
        StockSourceType sourceType, String sourceReferenceId, String sourceLineReference, String postingKey,
        Long reversalOfMovementId, Long actorId, String reason, String notes) { }
