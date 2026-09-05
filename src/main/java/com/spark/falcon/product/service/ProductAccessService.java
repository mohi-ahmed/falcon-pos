package com.spark.falcon.product.service;

import com.spark.falcon.product.dto.response.ProductBarcodeAccessResponse;
import com.spark.falcon.product.dto.response.ProductExpiryAccessResponse;
import com.spark.falcon.product.dto.response.ProductUnitConversionResponse;
import com.spark.falcon.product.dto.response.ProductVariantAccessResponse;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProductAccessService {
    Optional<ProductExpiryAccessResponse> findExpiryConfiguration(Long businessId, Long variantId);

    Optional<ProductVariantAccessResponse> findActiveVariantForBranch(
            Long businessId, Long branchId, Long variantId);

    Optional<ProductVariantAccessResponse> findActiveVariantBySkuForBranch(
            Long businessId, Long branchId, String sku);

    List<ProductVariantAccessResponse> findActiveVariantsForBranch(Long businessId, Long branchId);

    Optional<ProductBarcodeAccessResponse> resolveActiveBarcode(
            Long businessId, Long branchId, String barcode);

    List<ProductBarcodeAccessResponse> findActiveBarcodesForBranch(Long businessId, Long branchId);

    Map<Long, Long> countProductsUsingUnits(Long businessId, Collection<Long> unitIds);

    Optional<ProductUnitConversionResponse> findEffectiveConversion(
            Long businessId, Long branchId, Long variantId, Long sourceUnitId, LocalDate effectiveDate);
}
