package com.spark.falcon.product.entity;

import com.spark.falcon.businesssetup.entity.Business;
import com.spark.falcon.settings.entity.Unit;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "product_barcodes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductBarcode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_barcode_business"))
    @Getter(AccessLevel.NONE)
    private Business businessReference;

    @Column(name = "product_variant_id", nullable = false, updatable = false)
    private Long productVariantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_barcode_variant"))
    @Getter(AccessLevel.NONE)
    private ProductVariant productVariantReference;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private Long unitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_barcode_unit"))
    @Getter(AccessLevel.NONE)
    private Unit unitReference;

    @Column(nullable = false, length = 120)
    private String barcode;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ProductBarcode create(Long businessId, Long productVariantId, Long unitId,
                                        String barcode, Instant now) {
        ProductBarcode value = new ProductBarcode();
        value.businessId = Objects.requireNonNull(businessId, "businessId is required");
        value.productVariantId = Objects.requireNonNull(productVariantId, "productVariantId is required");
        value.unitId = Objects.requireNonNull(unitId, "unitId is required");
        value.barcode = required(barcode, "barcode");
        value.active = true;
        value.createdAt = Objects.requireNonNull(now, "now is required");
        value.updatedAt = now;
        return value;
    }

    public void deactivate(Instant now) {
        active = false;
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
