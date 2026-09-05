package com.spark.falcon.product.dto.command;

import com.spark.falcon.product.entity.enumtype.ExpiryType;
import com.spark.falcon.product.entity.enumtype.ProductStatus;

import java.util.Set;

public record CreateProductCommand(
        Long ownerId,
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
        java.util.List<com.spark.falcon.product.dto.request.ProductImageRequest> images) {
}
