package com.spark.falcon.purchase.entity;

import com.spark.falcon.purchase.entity.enumtype.StockImportRowStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "stock_import_rows", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_import_row_number", columnNames = {"stock_import_batch_id", "row_number"})
}, indexes = {
        @Index(name = "idx_stock_import_row_batch", columnList = "stock_import_batch_id"),
        @Index(name = "idx_stock_import_row_variant", columnList = "product_variant_id"),
        @Index(name = "idx_stock_import_row_batch_number", columnList = "batch_number")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockImportRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_import_batch_id", nullable = false, updatable = false)
    private Long stockImportBatchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_import_batch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_stock_import_row_batch"))
    @Getter(AccessLevel.NONE)
    private StockImportBatch stockImportBatchReference;

    @Column(name = "row_number", nullable = false, updatable = false)
    private int rowNumber;

    @Column(name = "product_variant_id")
    private Long productVariantId;

    @Column(name = "variant_sku", length = 60)
    private String variantSku;

    @Column(name = "entered_unit_id")
    private Long enteredUnitId;

    @Column(name = "entered_quantity", precision = 19, scale = 8)
    private BigDecimal enteredQuantity;

    @Column(name = "conversion_factor", precision = 19, scale = 8)
    private BigDecimal conversionFactor;

    @Column(name = "base_inventory_unit_id")
    private Long baseInventoryUnitId;

    @Column(name = "base_quantity", precision = 19, scale = 8)
    private BigDecimal baseQuantity;

    @Column(name = "current_system_quantity", precision = 19, scale = 8)
    private BigDecimal currentSystemQuantity;

    @Column(name = "quantity_difference", precision = 19, scale = 8)
    private BigDecimal quantityDifference;

    @Column(name = "original_purchase_unit_cost", precision = 19, scale = 4)
    private BigDecimal originalPurchaseUnitCost;

    @Column(name = "landed_base_unit_cost", precision = 19, scale = 4)
    private BigDecimal landedBaseUnitCost;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockImportRowStatus status;

    @Column(name = "validation_message", length = 1000)
    private String validationMessage;

    @Column(name = "posted_movement_id")
    private Long postedMovementId;

    @Column(name = "resulting_quantity", precision = 19, scale = 8)
    private BigDecimal resultingQuantity;

    @Column(name = "inventory_value_change", precision = 19, scale = 4)
    private BigDecimal inventoryValueChange;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static StockImportRow parsed(Long batchId, int rowNumber, Long productVariantId, String variantSku,
                                        Long enteredUnitId, BigDecimal enteredQuantity,
                                        BigDecimal originalPurchaseUnitCost, BigDecimal landedBaseUnitCost,
                                        String batchNumber, LocalDate manufacturingDate, LocalDate expiryDate,
                                        Instant now) {
        StockImportRow row = new StockImportRow();
        row.stockImportBatchId = Objects.requireNonNull(batchId, "batchId is required");
        row.rowNumber = rowNumber;
        row.productVariantId = productVariantId;
        row.variantSku = optional(variantSku);
        row.enteredUnitId = enteredUnitId;
        row.enteredQuantity = enteredQuantity;
        row.originalPurchaseUnitCost = originalPurchaseUnitCost;
        row.landedBaseUnitCost = landedBaseUnitCost;
        row.batchNumber = optional(batchNumber);
        row.manufacturingDate = manufacturingDate;
        row.expiryDate = expiryDate;
        row.status = StockImportRowStatus.INVALID;
        row.createdAt = Objects.requireNonNull(now, "now is required");
        return row;
    }

    public void resolveVariant(Long productVariantId, String variantSku) {
        if (status == StockImportRowStatus.POSTED || status == StockImportRowStatus.REVERSED) {
            throw new IllegalStateException("Posted Stock Import row is read-only");
        }
        this.productVariantId = Objects.requireNonNull(productVariantId, "productVariantId is required");
        this.variantSku = optional(variantSku);
    }

    public void resolveEnteredUnit(Long enteredUnitId) {
        if (status == StockImportRowStatus.POSTED || status == StockImportRowStatus.REVERSED) {
            throw new IllegalStateException("Posted Stock Import row is read-only");
        }
        this.enteredUnitId = Objects.requireNonNull(enteredUnitId, "enteredUnitId is required");
    }

    public void markValid(BigDecimal conversionFactor, Long baseInventoryUnitId, BigDecimal baseQuantity,
                          BigDecimal currentSystemQuantity, BigDecimal quantityDifference) {
        this.conversionFactor = Objects.requireNonNull(conversionFactor, "conversionFactor is required");
        this.baseInventoryUnitId = Objects.requireNonNull(baseInventoryUnitId, "baseInventoryUnitId is required");
        this.baseQuantity = Objects.requireNonNull(baseQuantity, "baseQuantity is required");
        this.currentSystemQuantity = Objects.requireNonNull(currentSystemQuantity, "currentSystemQuantity is required");
        this.quantityDifference = Objects.requireNonNull(quantityDifference, "quantityDifference is required");
        this.validationMessage = null;
        this.status = StockImportRowStatus.VALID;
    }

    public void markInvalid(String message) {
        this.validationMessage = optional(message);
        this.status = StockImportRowStatus.INVALID;
    }

    public void markPosted(Long movementId, BigDecimal resultingQuantity, BigDecimal inventoryValueChange) {
        if (status != StockImportRowStatus.VALID) throw new IllegalStateException("Only valid Stock Import row can be posted");
        this.postedMovementId = Objects.requireNonNull(movementId, "movementId is required");
        this.resultingQuantity = Objects.requireNonNull(resultingQuantity, "resultingQuantity is required");
        this.inventoryValueChange = Objects.requireNonNull(inventoryValueChange, "inventoryValueChange is required");
        this.status = StockImportRowStatus.POSTED;
    }

    public void markReversed() {
        if (status != StockImportRowStatus.POSTED) throw new IllegalStateException("Only posted Stock Import row can be reversed");
        this.status = StockImportRowStatus.REVERSED;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
