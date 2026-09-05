package com.spark.falcon.product.entity;

import com.spark.falcon.businesssetup.entity.Business;
import com.spark.falcon.product.entity.enumtype.ProductStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "product_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_category_business"))
    @Getter(AccessLevel.NONE)
    private Business businessReference;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 140) private String slug;
    @Column(name = "parent_category_id") private Long parentCategoryId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_category_parent"))
    @Getter(AccessLevel.NONE)
    private Category parentReference;
    @Column(length = 1000) private String description;
    @Column(name = "thumbnail_reference", length = 500) private String thumbnailReference;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ProductStatus status;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    public static Category create(Long businessId, String name, String slug, Long parentCategoryId,
                                  String description, String thumbnailReference, int displayOrder,
                                  ProductStatus status, Instant now) {
        Category value = new Category();
        value.businessId = Objects.requireNonNull(businessId);
        value.apply(name, slug, parentCategoryId, description, thumbnailReference, displayOrder, status);
        value.createdAt = Objects.requireNonNull(now);
        value.updatedAt = now;
        return value;
    }

    public void update(String name, String slug, Long parentCategoryId, String description,
                       String thumbnailReference, int displayOrder, ProductStatus status, Instant now) {
        if (archivedAt != null) throw new IllegalStateException("Archived category cannot be changed");
        apply(name, slug, parentCategoryId, description, thumbnailReference, displayOrder, status);
        updatedAt = Objects.requireNonNull(now);
    }

    public void archive(Instant now) {
        if (archivedAt != null) return;
        status = ProductStatus.INACTIVE;
        archivedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public void restore(Instant now) {
        if (archivedAt == null) return;
        archivedAt = null;
        updatedAt = Objects.requireNonNull(now);
    }

    public boolean isArchived() { return archivedAt != null; }

    private void apply(String name, String slug, Long parentCategoryId, String description,
                       String thumbnailReference, int displayOrder, ProductStatus status) {
        this.name = required(name);
        this.slug = required(slug).toLowerCase(Locale.ROOT);
        this.parentCategoryId = parentCategoryId;
        this.description = optional(description);
        this.thumbnailReference = optional(thumbnailReference);
        this.displayOrder = displayOrder;
        this.status = Objects.requireNonNull(status);
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required category value is missing");
        return value.trim();
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
