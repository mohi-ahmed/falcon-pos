package com.spark.falcon.product.dto.response;

import com.spark.falcon.product.entity.enumtype.ExpiryType;
import com.spark.falcon.product.entity.enumtype.ProductStatus;

import java.time.Instant;
import java.util.Set;

public record ProductResponse(
        Long id,
        Long businessId,
        String name,
        String referenceCode,
        String productType,
        Long categoryId,
        String brand,
        String barcodeFormat,
        String packagingType,
        Long taxRateId,
        String taxCalculationMethod,
        String description,
        String thumbnailReference,
        boolean trackExpiry,
        ExpiryType expiryType,
        boolean batchTrackingRequired,
        Integer defaultShelfLifeDays,
        Integer expiryAlertBeforeDays,
        boolean blockSaleAfterExpiry,
        int displayOrder,
        ProductStatus status,
        Set<Long> branchIds,
        boolean archived,
        Instant createdAt,
        java.util.List<ProductImageResponse> images) {
}
