package com.spark.falcon.product.dto.response;
import com.spark.falcon.product.entity.enumtype.ProductStatus;
import java.time.Instant;
import java.util.Set;
public record CategoryResponse(Long id, String name, String slug, Long parentCategoryId, String description,
                               String thumbnailReference, int displayOrder, ProductStatus status,
                               Set<Long> branchIds, long totalProducts, boolean archived, Instant createdAt) {}
