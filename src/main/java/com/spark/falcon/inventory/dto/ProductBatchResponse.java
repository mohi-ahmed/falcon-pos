package com.spark.falcon.inventory.dto;

import com.spark.falcon.inventory.entity.ProductBatchStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatchResponse {
    private Long id;
    private Long businessId;
    private Long branchId;
    private Long productVariantId;
    private Long supplierId;
    private Long sourcePurchaseItemId;
    private String batchNumber;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
    private BigDecimal receivedBaseQuantity;
    private BigDecimal availableBaseQuantity;
    private BigDecimal reservedBaseQuantity;
    private BigDecimal sellableBaseQuantity;
    private BigDecimal originalPurchaseUnitCost;
    private BigDecimal allocatedLandedUnitCost;
    private ProductBatchStatus status;
    private Instant createdAt;
}
