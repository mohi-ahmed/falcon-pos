package com.spark.falcon.product.entity;

import com.spark.falcon.businesssetup.entity.Business;
import com.spark.falcon.product.entity.enumtype.ExpiryType;
import com.spark.falcon.product.entity.enumtype.ProductStatus;
import com.spark.falcon.settings.entity.TaxRate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "products", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_business_reference_code", columnNames = {"business_id", "reference_code"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_business"))
    @Getter(AccessLevel.NONE)
    private Business businessReference;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "reference_code", nullable = false, length = 40)
    private String referenceCode;

    @Column(name = "product_type", length = 60)
    private String productType;

    @Column(name = "category_id")
    private Long categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_category"))
    @Getter(AccessLevel.NONE)
    private Category categoryReference;

    @Column(length = 100)
    private String brand;

    @Column(name = "barcode_format", length = 40)
    private String barcodeFormat;

    @Column(name = "packaging_type", length = 60)
    private String packagingType;

    @Column(name = "tax_rate_id")
    private Long taxRateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_rate_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_tax_rate"))
    @Getter(AccessLevel.NONE)
    private TaxRate taxRateReference;

    @Column(name = "tax_calculation_method", length = 40)
    private String taxCalculationMethod;

    @Column(length = 1000)
    private String description;

    @Column(name = "thumbnail_reference", length = 500)
    private String thumbnailReference;

    @Column(name = "track_expiry", nullable = false)
    private boolean trackExpiry;

    @Enumerated(EnumType.STRING)
    @Column(name = "expiry_type", length = 20)
    private ExpiryType expiryType;

    @Column(name = "batch_tracking_required", nullable = false)
    private boolean batchTrackingRequired;

    @Column(name = "default_shelf_life_days")
    private Integer defaultShelfLifeDays;

    @Column(name = "expiry_alert_before_days")
    private Integer expiryAlertBeforeDays;

    @Column(name = "block_sale_after_expiry", nullable = false)
    private boolean blockSaleAfterExpiry;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static Product create(Long businessId, String name, String referenceCode, String productType, Long categoryId, String brand,
                                 String barcodeFormat, String packagingType, Long taxRateId,
                                 String taxCalculationMethod, String description, String thumbnailReference,
                                 boolean trackExpiry, ExpiryType expiryType, boolean batchTrackingRequired,
                                 Integer defaultShelfLifeDays, Integer expiryAlertBeforeDays,
                                 boolean blockSaleAfterExpiry, int displayOrder, ProductStatus status, Instant now) {
        Product product = new Product();
        product.businessId = Objects.requireNonNull(businessId, "businessId is required");
        product.apply(name, referenceCode, productType, categoryId, brand, barcodeFormat, packagingType, taxRateId,
                taxCalculationMethod, description, thumbnailReference, trackExpiry, expiryType,
                batchTrackingRequired, defaultShelfLifeDays, expiryAlertBeforeDays, blockSaleAfterExpiry,
                displayOrder, status);
        product.createdAt = Objects.requireNonNull(now, "now is required");
        product.updatedAt = now;
        return product;
    }

    public void update(String name, String referenceCode, String productType, Long categoryId, String brand,
                       String barcodeFormat, String packagingType, Long taxRateId,
                       String taxCalculationMethod, String description, String thumbnailReference,
                       boolean trackExpiry, ExpiryType expiryType, boolean batchTrackingRequired,
                       Integer defaultShelfLifeDays, Integer expiryAlertBeforeDays,
                       boolean blockSaleAfterExpiry, int displayOrder, ProductStatus status, Instant now) {
        ensureNotArchived();
        apply(name, referenceCode, productType, categoryId, brand, barcodeFormat, packagingType, taxRateId,
                taxCalculationMethod, description, thumbnailReference, trackExpiry, expiryType,
                batchTrackingRequired, defaultShelfLifeDays, expiryAlertBeforeDays, blockSaleAfterExpiry,
                displayOrder, status);
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void changeStatus(ProductStatus status, Instant now) {
        ensureNotArchived();
        this.status = Objects.requireNonNull(status, "status is required");
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void archive(Instant now) {
        if (archivedAt != null) return;
        status = ProductStatus.INACTIVE;
        archivedAt = Objects.requireNonNull(now, "now is required");
        updatedAt = now;
    }

    public void restore(Instant now) {
        if (archivedAt == null) return;
        archivedAt = null;
        status = ProductStatus.ACTIVE;
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean isOperationallyActive() {
        return status == ProductStatus.ACTIVE && archivedAt == null;
    }

    private void apply(String name, String referenceCode, String productType, Long categoryId, String brand,
                       String barcodeFormat, String packagingType, Long taxRateId,
                       String taxCalculationMethod, String description, String thumbnailReference,
                       boolean trackExpiry, ExpiryType expiryType, boolean batchTrackingRequired,
                       Integer defaultShelfLifeDays, Integer expiryAlertBeforeDays,
                       boolean blockSaleAfterExpiry, int displayOrder, ProductStatus status) {
        this.name = required(name, "name");
        this.referenceCode = required(referenceCode, "referenceCode").toUpperCase(Locale.ROOT);
        this.productType = optional(productType);
        this.categoryId = categoryId;
        this.brand = optional(brand);
        this.barcodeFormat = optional(barcodeFormat);
        this.packagingType = optional(packagingType);
        this.taxRateId = taxRateId;
        this.taxCalculationMethod = optional(taxCalculationMethod);
        this.description = optional(description);
        this.thumbnailReference = optional(thumbnailReference);
        this.trackExpiry = trackExpiry;
        this.expiryType = trackExpiry ? expiryType : null;
        this.batchTrackingRequired = batchTrackingRequired;
        this.defaultShelfLifeDays = trackExpiry ? defaultShelfLifeDays : null;
        this.expiryAlertBeforeDays = trackExpiry ? expiryAlertBeforeDays : null;
        this.blockSaleAfterExpiry = trackExpiry && blockSaleAfterExpiry;
        this.displayOrder = displayOrder;
        this.status = Objects.requireNonNull(status, "status is required");
    }

    private void ensureNotArchived() {
        if (archivedAt != null) throw new IllegalStateException("Archived product cannot be changed");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
