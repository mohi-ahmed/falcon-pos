package com.spark.falcon.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

public record SaleStockPostingResponse(
        StockMovementResponse movement,
        BigDecimal weightedAverageCostSnapshot,
        List<InventorySaleBatchAllocationResponse> batchAllocations) {
}
