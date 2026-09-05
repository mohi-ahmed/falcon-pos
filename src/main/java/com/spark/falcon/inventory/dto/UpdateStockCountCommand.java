package com.spark.falcon.inventory.dto;

import com.spark.falcon.inventory.entity.StockCountScope;

import java.time.LocalDate;
import java.util.List;

public record UpdateStockCountCommand(
        Long ownerId,
        Long countId,
        LocalDate countDate,
        StockCountScope scope,
        Long assignedCounterId,
        String notes,
        String attachmentReference,
        List<CreateStockCountCommand.StockCountSelection> selections
) {
}
