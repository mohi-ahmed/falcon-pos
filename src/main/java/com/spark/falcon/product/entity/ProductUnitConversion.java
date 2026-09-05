package com.spark.falcon.product.entity;

import com.spark.falcon.settings.entity.Unit;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "product_unit_conversions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductUnitConversion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_variant_id", nullable = false, updatable = false)
    private Long productVariantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_conversion_variant"))
    @Getter(AccessLevel.NONE)
    private ProductVariant productVariantReference;

    @Column(name = "source_unit_id", nullable = false, updatable = false)
    private Long sourceUnitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_unit_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_conversion_source_unit"))
    @Getter(AccessLevel.NONE)
    private Unit sourceUnitReference;

    @Column(name = "target_unit_id", nullable = false, updatable = false)
    private Long targetUnitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_unit_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_conversion_target_unit"))
    @Getter(AccessLevel.NONE)
    private Unit targetUnitReference;

    @Column(name = "conversion_factor", nullable = false, precision = 19, scale = 8)
    private BigDecimal conversionFactor;

    @Column(name = "decimal_precision", nullable = false)
    private int decimalPrecision;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ProductUnitConversion create(Long productVariantId, Long sourceUnitId, Long targetUnitId,
                                               BigDecimal conversionFactor, int decimalPrecision,
                                               LocalDate effectiveFrom, LocalDate effectiveTo, Instant now) {
        ProductUnitConversion value = new ProductUnitConversion();
        value.productVariantId = Objects.requireNonNull(productVariantId, "productVariantId is required");
        value.sourceUnitId = Objects.requireNonNull(sourceUnitId, "sourceUnitId is required");
        value.targetUnitId = Objects.requireNonNull(targetUnitId, "targetUnitId is required");
        value.conversionFactor = Objects.requireNonNull(conversionFactor, "conversionFactor is required");
        value.decimalPrecision = decimalPrecision;
        value.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        value.effectiveTo = effectiveTo;
        value.active = true;
        value.createdAt = Objects.requireNonNull(now, "now is required");
        value.updatedAt = now;
        return value;
    }

    public void deactivate(LocalDate effectiveTo, Instant now) {
        active = false;
        this.effectiveTo = effectiveTo;
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public boolean isEffectiveOn(LocalDate date) {
        if (!active || date == null || effectiveFrom.isAfter(date)) return false;
        return effectiveTo == null || !effectiveTo.isBefore(date);
    }
}
