package com.spark.falcon.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryPostingResponse {
    private BranchProductStockResponse stock;
    private ProductBatchResponse batch;
    private StockMovementResponse movement;
}
