package com.spark.falcon.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "product_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "product_id", nullable = false, updatable = false) private Long productId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_image_product"))
    @Getter(AccessLevel.NONE) private Product productReference;
    @Column(name = "image_reference", nullable = false, length = 500) private String imageReference;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "primary_image", nullable = false) private boolean primaryImage;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static ProductImage create(Long productId, String imageReference, int displayOrder,
                                      boolean primaryImage, Instant now) {
        ProductImage value = new ProductImage();
        value.productId = Objects.requireNonNull(productId);
        value.imageReference = required(imageReference);
        value.displayOrder = displayOrder;
        value.primaryImage = primaryImage;
        value.createdAt = Objects.requireNonNull(now);
        value.updatedAt = now;
        return value;
    }
    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("imageReference is required");
        return value.trim();
    }
}
