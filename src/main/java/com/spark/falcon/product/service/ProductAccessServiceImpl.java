package com.spark.falcon.product.service;

import com.spark.falcon.product.dto.response.ProductBarcodeAccessResponse;
import com.spark.falcon.product.dto.response.ProductExpiryAccessResponse;
import com.spark.falcon.product.dto.response.ProductUnitConversionResponse;
import com.spark.falcon.product.dto.response.ProductVariantAccessResponse;
import com.spark.falcon.product.entity.Product;
import com.spark.falcon.product.entity.ProductBarcode;
import com.spark.falcon.product.entity.ProductUnitConversion;
import com.spark.falcon.product.entity.ProductVariant;
import com.spark.falcon.product.entity.enumtype.ProductStatus;
import com.spark.falcon.product.mapper.ProductMapper;
import com.spark.falcon.product.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductAccessServiceImpl implements ProductAccessService {
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductBarcodeRepository barcodeRepository;
    private final ProductUnitConversionRepository conversionRepository;
    private final BranchProductRepository branchProductRepository;
    private final ProductMapper mapper;

    @Override
    public Optional<ProductExpiryAccessResponse> findExpiryConfiguration(Long businessId, Long variantId) {
        ProductVariant variant = variantRepository.findById(variantId).orElse(null);
        if (variant == null) return Optional.empty();
        Product product = productRepository.findByIdAndBusinessId(variant.getProductId(), businessId).orElse(null);
        if (product == null) return Optional.empty();
        return Optional.of(new ProductExpiryAccessResponse(
                product.getId(), variant.getId(), product.getExpiryAlertBeforeDays()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductVariantAccessResponse> findActiveVariantForBranch(
            Long businessId, Long branchId, Long variantId) {
        ProductVariant variant = variantRepository.findByIdAndStatus(variantId, ProductStatus.ACTIVE).orElse(null);
        if (variant == null) return Optional.empty();
        Product product = productRepository.findByIdAndBusinessIdAndArchivedAtIsNull(variant.getProductId(), businessId)
                .orElse(null);
        if (product == null || !product.isOperationallyActive()) return Optional.empty();
        if (!branchProductRepository.existsByProductIdAndBranchIdAndActiveTrue(product.getId(), branchId))
            return Optional.empty();
        return Optional.of(toAccessResponse(product, variant, branchId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductVariantAccessResponse> findActiveVariantBySkuForBranch(
            Long businessId, Long branchId, String sku) {
        if (sku == null || sku.isBlank()) return Optional.empty();
        return variantRepository.findBySkuIgnoreCaseAndStatus(sku.trim(), ProductStatus.ACTIVE).stream()
                .map(variant -> {
                    Product product = productRepository
                            .findByIdAndBusinessIdAndArchivedAtIsNull(variant.getProductId(), businessId)
                            .orElse(null);
                    if (product == null || !product.isOperationallyActive()) return null;
                    if (!branchProductRepository.existsByProductIdAndBranchIdAndActiveTrue(product.getId(), branchId))
                        return null;
                    return toAccessResponse(product, variant, branchId);
                })
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantAccessResponse> findActiveVariantsForBranch(Long businessId, Long branchId) {
        return productRepository.findByBusinessIdOrderByDisplayOrderAscNameAsc(businessId).stream()
                .filter(Product::isOperationallyActive)
                .filter(product -> branchProductRepository.existsByProductIdAndBranchIdAndActiveTrue(product.getId(), branchId))
                .flatMap(product -> variantRepository.findByProductIdOrderByVariantNameAsc(product.getId()).stream()
                        .filter(variant -> variant.getStatus() == ProductStatus.ACTIVE)
                        .map(variant -> toAccessResponse(product, variant, branchId)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductBarcodeAccessResponse> resolveActiveBarcode(
            Long businessId, Long branchId, String barcodeValue) {
        if (barcodeValue == null || barcodeValue.isBlank()) return Optional.empty();
        ProductBarcode barcode = barcodeRepository.findByBusinessIdAndBarcodeAndActiveTrue(
                businessId, barcodeValue.trim()).orElse(null);
        if (barcode == null) return Optional.empty();
        return findActiveVariantForBranch(businessId, branchId, barcode.getProductVariantId())
                .map(variant -> new ProductBarcodeAccessResponse(variant, barcode.getId(),
                        barcode.getBarcode(), barcode.getUnitId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductBarcodeAccessResponse> findActiveBarcodesForBranch(Long businessId, Long branchId) {
        return barcodeRepository.findByBusinessIdAndActiveTrueOrderByBarcodeAsc(businessId).stream()
                .map(barcode -> findActiveVariantForBranch(businessId, branchId, barcode.getProductVariantId())
                        .map(variant -> new ProductBarcodeAccessResponse(
                                variant, barcode.getId(), barcode.getBarcode(), barcode.getUnitId()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> countProductsUsingUnits(Long businessId, Collection<Long> unitIds) {
        if (businessId == null || unitIds == null || unitIds.isEmpty()) return Map.of();
        Set<Long> requested = unitIds.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requested.isEmpty()) return Map.of();

        List<Long> productIds = productRepository.findByBusinessIdOrderByDisplayOrderAscNameAsc(businessId).stream()
                .map(Product::getId).toList();
        LinkedHashMap<Long, Long> counts = new LinkedHashMap<>();
        requested.forEach(unitId -> counts.put(unitId, 0L));
        if (productIds.isEmpty()) return Map.copyOf(counts);

        List<ProductVariant> variants = variantRepository.findForBusinessAndProductIds(businessId, productIds);
        if (variants.isEmpty()) return Map.copyOf(counts);
        Map<Long, Long> productIdByVariant = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, ProductVariant::getProductId));
        Map<Long, Set<Long>> productsByUnit = new LinkedHashMap<>();
        requested.forEach(unitId -> productsByUnit.put(unitId, new LinkedHashSet<>()));

        for (ProductVariant variant : variants) {
            for (Long unitId : requested) {
                if (unitId.equals(variant.getBaseInventoryUnitId())
                        || unitId.equals(variant.getPurchaseUnitId())
                        || unitId.equals(variant.getSellingUnitId())) {
                    productsByUnit.get(unitId).add(variant.getProductId());
                }
            }
        }

        List<Long> variantIds = variants.stream().map(ProductVariant::getId).toList();
        for (ProductUnitConversion conversion : conversionRepository.findByProductVariantIdIn(variantIds)) {
            Long productId = productIdByVariant.get(conversion.getProductVariantId());
            if (productId == null) continue;
            if (requested.contains(conversion.getSourceUnitId())) {
                productsByUnit.get(conversion.getSourceUnitId()).add(productId);
            }
            if (requested.contains(conversion.getTargetUnitId())) {
                productsByUnit.get(conversion.getTargetUnitId()).add(productId);
            }
        }
        productsByUnit.forEach((unitId, products) -> counts.put(unitId, (long) products.size()));
        return Map.copyOf(counts);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductUnitConversionResponse> findEffectiveConversion(
            Long businessId, Long branchId, Long variantId, Long sourceUnitId, LocalDate effectiveDate) {
        if (effectiveDate == null) return Optional.empty();
        Optional<ProductVariantAccessResponse> variant = findActiveVariantForBranch(businessId, branchId, variantId);
        if (variant.isEmpty()) return Optional.empty();
        List<ProductUnitConversion> effective = conversionRepository
                .findByProductVariantIdAndSourceUnitIdAndActiveTrueOrderByEffectiveFromDesc(variantId, sourceUnitId)
                .stream()
                .filter(value -> value.isEffectiveOn(effectiveDate))
                .toList();
        if (effective.size() != 1) return Optional.empty();
        return Optional.of(mapper.toResponse(effective.getFirst()));
    }

    private ProductVariantAccessResponse toAccessResponse(
            Product product,
            ProductVariant variant,
            Long branchId
    ) {
        return new ProductVariantAccessResponse(
                product.getBusinessId(),
                branchId,
                product.getId(),
                variant.getId(),
                product.getName(),
                product.getReferenceCode(),
                variant.getVariantName(),
                variant.getSku(),
                variant.getBaseInventoryUnitId(),
                variant.getPurchaseUnitId(),
                variant.getSellingUnitId(),
                variant.getSellingPrice(),
                product.getThumbnailReference(),
                product.getTaxRateId(),
                product.getTaxCalculationMethod(),
                product.isTrackExpiry(),
                product.isBatchTrackingRequired()
        );
    }
}
