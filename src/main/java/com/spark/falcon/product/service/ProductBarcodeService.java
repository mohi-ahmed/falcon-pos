package com.spark.falcon.product.service;

import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.product.dto.command.CreateProductBarcodeCommand;
import com.spark.falcon.product.dto.response.ProductBarcodeResponse;
import com.spark.falcon.product.entity.Product;
import com.spark.falcon.product.entity.ProductAuditEvent;
import com.spark.falcon.product.entity.ProductBarcode;
import com.spark.falcon.product.entity.ProductVariant;
import com.spark.falcon.product.entity.enumtype.ProductAuditAction;
import com.spark.falcon.product.exception.*;
import com.spark.falcon.product.mapper.ProductMapper;
import com.spark.falcon.product.repository.*;
import com.spark.falcon.product.validation.ProductValidator;
import com.spark.falcon.settings.service.UnitAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductBarcodeService {
    private final ProductRepository productRepository;
    private final ProductBarcodeRepository barcodeRepository;
    private final ProductUnitConversionRepository conversionRepository;
    private final ProductAuditEventRepository auditRepository;
    private final BusinessAccessService businessAccessService;
    private final ProductVariantService variantService;
    private final ProductService productService;
    private final UnitAccessService unitAccessService;
    private final ProductMapper mapper;
    private final ProductValidator validator;
    private final Clock clock;

    @Transactional
    public ProductBarcodeResponse create(CreateProductBarcodeCommand command) {
        BusinessAccessResponse business = business(command.ownerId());
        Product product = product(command.productId(), business.businessId());
        ProductVariant variant = variantService.requireVariant(product.getId(), command.variantId());
        validateUnitEligibility(business.businessId(), product.getId(), variant, command.unitId());

        String barcodeValue = validator.normalizeBarcode(command.barcode());
        if (barcodeRepository.existsByBusinessIdAndBarcodeAndActiveTrue(business.businessId(), barcodeValue))
            throw new ProductBarcodeAlreadyUsedException(barcodeValue);

        Instant now = Instant.now(clock);
        ProductBarcode barcode = barcodeRepository.saveAndFlush(ProductBarcode.create(business.businessId(),
                variant.getId(), command.unitId(), barcodeValue, now));
        auditRepository.save(ProductAuditEvent.record(business.businessId(), product.getId(), command.ownerId(),
                ProductAuditAction.BARCODE_CREATED, "PRODUCT_BARCODE", barcode.getId(), now));
        return mapper.toResponse(barcode);
    }

    @Transactional
    public ProductBarcodeResponse deactivate(Long ownerId, Long productId, Long variantId, Long barcodeId) {
        BusinessAccessResponse business = business(ownerId);
        Product product = product(productId, business.businessId());
        ProductVariant variant = variantService.requireVariant(product.getId(), variantId);
        ProductBarcode barcode = barcodeRepository.findByIdAndBusinessId(barcodeId, business.businessId())
                .filter(value -> value.getProductVariantId().equals(variant.getId()))
                .orElseThrow(() -> new ProductConfigurationConflictException("Product barcode was not found"));
        Instant now = Instant.now(clock);
        barcode.deactivate(now);
        ProductBarcode saved = barcodeRepository.saveAndFlush(barcode);
        auditRepository.save(ProductAuditEvent.record(business.businessId(), product.getId(), ownerId,
                ProductAuditAction.BARCODE_DEACTIVATED, "PRODUCT_BARCODE", saved.getId(), now));
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductBarcodeResponse> findAll(Long ownerId, Long productId, Long variantId) {
        BusinessAccessResponse business = business(ownerId);
        Product product = product(productId, business.businessId());
        ProductVariant variant = variantService.requireVariant(product.getId(), variantId);
        return barcodeRepository.findByProductVariantIdOrderByIdAsc(variant.getId()).stream().map(mapper::toResponse).toList();
    }

    private void validateUnitEligibility(Long businessId, Long productId, ProductVariant variant, Long unitId) {
        boolean eligible = unitId != null && (unitId.equals(variant.getBaseInventoryUnitId())
                || unitId.equals(variant.getPurchaseUnitId())
                || unitId.equals(variant.getSellingUnitId())
                || conversionRepository.existsByProductVariantIdAndSourceUnitIdAndActiveTrue(variant.getId(), unitId));
        if (!eligible)
            throw new ProductConfigurationConflictException("Barcode unit is not configured for this Product Variant");

        Set<Long> branchIds = productService.activeBranchIds(productId);
        for (Long branchId : branchIds) {
            if (unitAccessService.findActiveForBranch(businessId, branchId, unitId).isEmpty())
                throw new ProductConfigurationConflictException("Barcode unit is inactive for an assigned branch");
        }
    }

    private Product product(Long productId, Long businessId) {
        return productRepository.findByIdAndBusinessIdAndArchivedAtIsNull(productId, businessId)
                .orElseThrow(ProductNotFoundException::new);
    }

    private BusinessAccessResponse business(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId).orElseThrow(ProductAccessDeniedException::new);
    }
}
