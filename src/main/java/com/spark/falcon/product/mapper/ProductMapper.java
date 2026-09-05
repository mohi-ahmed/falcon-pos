package com.spark.falcon.product.mapper;

import com.spark.falcon.product.dto.command.*;
import com.spark.falcon.product.dto.request.*;
import com.spark.falcon.product.dto.response.*;
import com.spark.falcon.product.entity.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class ProductMapper {

    public CreateProductCommand toCreateCommand(Long ownerId, ProductRequest request) {
        return new CreateProductCommand(ownerId, request.getName(), request.getReferenceCode(), request.getProductType(), request.getCategoryId(),
                request.getBrand(), request.getBarcodeFormat(), request.getPackagingType(), request.getTaxRateId(),
                request.getTaxCalculationMethod(), request.getDescription(), request.getThumbnailReference(),
                request.isTrackExpiry(), request.getExpiryType(), request.isBatchTrackingRequired(),
                request.getDefaultShelfLifeDays(), request.getExpiryAlertBeforeDays(), request.isBlockSaleAfterExpiry(),
                request.getDisplayOrder(), request.getStatus(), copy(request.getBranchIds()), request.getImages());
    }

    public UpdateProductCommand toUpdateCommand(Long ownerId, Long productId, ProductRequest request) {
        return new UpdateProductCommand(ownerId, productId, request.getName(), request.getReferenceCode(), request.getProductType(), request.getCategoryId(),
                request.getBrand(), request.getBarcodeFormat(), request.getPackagingType(), request.getTaxRateId(),
                request.getTaxCalculationMethod(), request.getDescription(), request.getThumbnailReference(),
                request.isTrackExpiry(), request.getExpiryType(), request.isBatchTrackingRequired(),
                request.getDefaultShelfLifeDays(), request.getExpiryAlertBeforeDays(), request.isBlockSaleAfterExpiry(),
                request.getDisplayOrder(), request.getStatus(), copy(request.getBranchIds()), request.getImages());
    }

    public CreateProductVariantCommand toCreateVariantCommand(Long ownerId, Long productId, ProductVariantRequest request) {
        return new CreateProductVariantCommand(ownerId, productId, request.getVariantName(), request.getSku(),
                request.getBaseInventoryUnitId(), request.getPurchaseUnitId(), request.getSellingUnitId(),
                request.getSellingPrice(), request.getReorderLevel(), request.getStatus());
    }

    public UpdateProductVariantCommand toUpdateVariantCommand(Long ownerId, Long productId, Long variantId,
                                                               ProductVariantRequest request) {
        return new UpdateProductVariantCommand(ownerId, productId, variantId, request.getVariantName(), request.getSku(),
                request.getBaseInventoryUnitId(), request.getPurchaseUnitId(), request.getSellingUnitId(),
                request.getSellingPrice(), request.getReorderLevel(), request.getStatus());
    }

    public CreateProductBarcodeCommand toCreateBarcodeCommand(Long ownerId, Long productId, Long variantId,
                                                               ProductBarcodeRequest request) {
        return new CreateProductBarcodeCommand(ownerId, productId, variantId, request.getUnitId(), request.getBarcode());
    }

    public CreateProductUnitConversionCommand toCreateConversionCommand(Long ownerId, Long productId, Long variantId,
                                                                         ProductUnitConversionRequest request) {
        return new CreateProductUnitConversionCommand(ownerId, productId, variantId, request.getSourceUnitId(),
                request.getTargetUnitId(), request.getConversionFactor(), request.getDecimalPrecision(),
                request.getEffectiveFrom(), request.getEffectiveTo());
    }

    public ProductResponse toResponse(Product product, Set<Long> branchIds, java.util.List<ProductImageResponse> images) {
        return new ProductResponse(product.getId(), product.getBusinessId(), product.getName(), product.getReferenceCode(),
                product.getProductType(), product.getCategoryId(), product.getBrand(), product.getBarcodeFormat(), product.getPackagingType(),
                product.getTaxRateId(), product.getTaxCalculationMethod(), product.getDescription(),
                product.getThumbnailReference(), product.isTrackExpiry(), product.getExpiryType(),
                product.isBatchTrackingRequired(), product.getDefaultShelfLifeDays(), product.getExpiryAlertBeforeDays(),
                product.isBlockSaleAfterExpiry(), product.getDisplayOrder(), product.getStatus(), Set.copyOf(branchIds),
                product.isArchived(), product.getCreatedAt(), java.util.List.copyOf(images));
    }

    public ProductVariantResponse toResponse(ProductVariant variant) {
        return new ProductVariantResponse(variant.getId(), variant.getProductId(), variant.getVariantName(), variant.getSku(),
                variant.getBaseInventoryUnitId(), variant.getPurchaseUnitId(), variant.getSellingUnitId(),
                variant.getSellingPrice(), variant.getReorderLevel(), variant.getStatus());
    }

    public ProductBarcodeResponse toResponse(ProductBarcode barcode) {
        return new ProductBarcodeResponse(barcode.getId(), barcode.getBusinessId(), barcode.getProductVariantId(),
                barcode.getUnitId(), barcode.getBarcode(), barcode.isActive());
    }

    public ProductUnitConversionResponse toResponse(ProductUnitConversion conversion) {
        return new ProductUnitConversionResponse(conversion.getId(), conversion.getProductVariantId(),
                conversion.getSourceUnitId(), conversion.getTargetUnitId(), conversion.getConversionFactor(),
                conversion.getDecimalPrecision(), conversion.getEffectiveFrom(), conversion.getEffectiveTo(),
                conversion.isActive());
    }

    private Set<Long> copy(Set<Long> values) {
        return values == null ? Set.of() : new LinkedHashSet<>(values);
    }
}
