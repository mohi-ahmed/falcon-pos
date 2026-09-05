package com.spark.falcon.inventory.dto;import java.math.BigDecimal;import java.time.LocalDate;
public record CreateInventoryLossCommand(Long ownerId,Long branchId,Long productVariantId,Long productBatchId,LocalDate disposalDate,BigDecimal enteredQuantity,Long enteredUnitId,BigDecimal conversionFactor,String reason,String notes,String attachmentReference,String idempotencyKey){}
