package com.spark.falcon.product.entity;

import com.spark.falcon.product.entity.enumtype.ProductStatus;
import com.spark.falcon.settings.entity.Unit;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "product_variants", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_variant_product_sku", columnNames = {"product_id", "sku"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_variant_product"))
    @Getter(AccessLevel.NONE)
    private Product productReference;

    @Column(name = "variant_name", nullable = false, length = 120)
    private String variantName;

    @Column(nullable = false, length = 60)
    private String sku;

    @Column(name = "base_inventory_unit_id", nullable = false)
    private Long baseInventoryUnitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_inventory_unit_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_variant_base_unit"))
    @Getter(AccessLevel.NONE)
    private Unit baseInventoryUnitReference;

    @Column(name = "purchase_unit_id")
    private Long purchaseUnitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_unit_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_variant_purchase_unit"))
    @Getter(AccessLevel.NONE)
    private Unit purchaseUnitReference;

    @Column(name = "selling_unit_id")
    private Long sellingUnitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selling_unit_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_variant_selling_unit"))
    @Getter(AccessLevel.NONE)
    private Unit sellingUnitReference;

    @Column(name = "selling_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal sellingPrice;

    @Column(name = "reorder_level", nullable = false, precision = 19, scale = 4)
    private BigDecimal reorderLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static ProductVariant create(Long productId, String variantName, String sku,
                                        Long baseInventoryUnitId, Long purchaseUnitId, Long sellingUnitId,
                                        BigDecimal sellingPrice, BigDecimal reorderLevel,
                                        ProductStatus status, Instant now) {
        ProductVariant variant = new ProductVariant();
        variant.productId = Objects.requireNonNull(productId, "productId is required");
        variant.apply(variantName, sku, baseInventoryUnitId, purchaseUnitId, sellingUnitId,
                sellingPrice, reorderLevel, status);
        variant.createdAt = Objects.requireNonNull(now, "now is required");
        variant.updatedAt = now;
        return variant;
    }

    public void update(String variantName, String sku, Long baseInventoryUnitId,
                       Long purchaseUnitId, Long sellingUnitId, BigDecimal sellingPrice,
                       BigDecimal reorderLevel, ProductStatus status, Instant now) {
        apply(variantName, sku, baseInventoryUnitId, purchaseUnitId, sellingUnitId,
                sellingPrice, reorderLevel, status);
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void changeStatus(ProductStatus status, Instant now) {
        this.status = Objects.requireNonNull(status, "status is required");
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    private void apply(String variantName, String sku, Long baseInventoryUnitId,
                       Long purchaseUnitId, Long sellingUnitId, BigDecimal sellingPrice,
                       BigDecimal reorderLevel, ProductStatus status) {
        this.variantName = required(variantName, "variantName");
        this.sku = required(sku, "sku").toUpperCase(Locale.ROOT);
        this.baseInventoryUnitId = Objects.requireNonNull(baseInventoryUnitId, "baseInventoryUnitId is required");
        this.purchaseUnitId = purchaseUnitId;
        this.sellingUnitId = sellingUnitId;
        this.sellingPrice = Objects.requireNonNull(sellingPrice, "sellingPrice is required");
        this.reorderLevel = Objects.requireNonNull(reorderLevel, "reorderLevel is required");
        this.status = Objects.requireNonNull(status, "status is required");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
