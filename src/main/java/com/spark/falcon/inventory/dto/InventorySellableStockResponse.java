package com.spark.falcon.inventory.dto;

import java.math.BigDecimal;

public record InventorySellableStockResponse(
        Long productVariantId, BigDecimal sellableBaseQuantity, BigDecimal weightedAverageCost) {
}
