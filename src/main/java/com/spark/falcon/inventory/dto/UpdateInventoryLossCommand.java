package com.spark.falcon.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateInventoryLossCommand(
        Long ownerId,
        Long lossId,
        Long productVariantId,
        Long productBatchId,
        LocalDate disposalDate,
        BigDecimal enteredQuantity,
        Long enteredUnitId,
        String reason,
        String notes,
        String attachmentReference
) {
}
