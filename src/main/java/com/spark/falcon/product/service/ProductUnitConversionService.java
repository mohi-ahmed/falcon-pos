package com.spark.falcon.product.service;

import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.product.dto.command.CreateProductUnitConversionCommand;
import com.spark.falcon.product.dto.response.ProductUnitConversionResponse;
import com.spark.falcon.product.entity.Product;
import com.spark.falcon.product.entity.ProductAuditEvent;
import com.spark.falcon.product.entity.ProductUnitConversion;
import com.spark.falcon.product.entity.ProductVariant;
import com.spark.falcon.product.entity.enumtype.ProductAuditAction;
import com.spark.falcon.product.exception.*;
import com.spark.falcon.product.mapper.ProductMapper;
import com.spark.falcon.product.repository.ProductAuditEventRepository;
import com.spark.falcon.product.repository.ProductRepository;
import com.spark.falcon.product.repository.ProductUnitConversionRepository;
import com.spark.falcon.product.validation.ProductValidator;
import com.spark.falcon.settings.service.UnitAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductUnitConversionService {
    private final ProductRepository productRepository;
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
    public ProductUnitConversionResponse create(CreateProductUnitConversionCommand command) {
        BusinessAccessResponse business = business(command.ownerId());
        Product product = product(command.productId(), business.businessId());
        ProductVariant variant = variantService.requireVariant(product.getId(), command.variantId());
        validator.validate(command);

        if (!variant.getBaseInventoryUnitId().equals(command.targetUnitId()))
            throw new ProductConfigurationConflictException(
                    "ProductUnitConversion target unit must be the Product Variant Base Inventory Unit");
        if (conversionRepository.existsByProductVariantIdAndSourceUnitIdAndActiveTrue(
                variant.getId(), command.sourceUnitId()))
            throw new ProductConfigurationConflictException(
                    "A conflicting active ProductUnitConversion already exists for this variant and source unit");

        validateUnitAcrossBranches(business.businessId(), product.getId(), command.sourceUnitId());
        validateUnitAcrossBranches(business.businessId(), product.getId(), command.targetUnitId());

        Instant now = Instant.now(clock);
        ProductUnitConversion conversion = conversionRepository.saveAndFlush(ProductUnitConversion.create(
                variant.getId(), command.sourceUnitId(), command.targetUnitId(), command.conversionFactor(),
                command.decimalPrecision(), command.effectiveFrom(), command.effectiveTo(), now));
        auditRepository.save(ProductAuditEvent.record(business.businessId(), product.getId(), command.ownerId(),
                ProductAuditAction.UNIT_CONVERSION_CREATED, "PRODUCT_UNIT_CONVERSION", conversion.getId(), now));
        return mapper.toResponse(conversion);
    }

    @Transactional
    public ProductUnitConversionResponse deactivate(Long ownerId, Long productId, Long variantId,
                                                     Long conversionId, LocalDate effectiveTo) {
        BusinessAccessResponse business = business(ownerId);
        Product product = product(productId, business.businessId());
        ProductVariant variant = variantService.requireVariant(product.getId(), variantId);
        ProductUnitConversion conversion = conversionRepository.findByIdAndProductVariantId(conversionId, variant.getId())
                .orElseThrow(() -> new ProductConfigurationConflictException("ProductUnitConversion was not found"));
        if (effectiveTo != null && effectiveTo.isBefore(conversion.getEffectiveFrom()))
            throw new IllegalArgumentException("effectiveTo must not be before effectiveFrom");

        Instant now = Instant.now(clock);
        conversion.deactivate(effectiveTo, now);
        ProductUnitConversion saved = conversionRepository.saveAndFlush(conversion);
        auditRepository.save(ProductAuditEvent.record(business.businessId(), product.getId(), ownerId,
                ProductAuditAction.UNIT_CONVERSION_DEACTIVATED, "PRODUCT_UNIT_CONVERSION", saved.getId(), now));
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductUnitConversionResponse> findAll(Long ownerId, Long productId, Long variantId) {
        BusinessAccessResponse business = business(ownerId);
        Product product = product(productId, business.businessId());
        ProductVariant variant = variantService.requireVariant(product.getId(), variantId);
        return conversionRepository.findByProductVariantIdOrderByEffectiveFromDesc(variant.getId()).stream()
                .map(mapper::toResponse).toList();
    }

    private void validateUnitAcrossBranches(Long businessId, Long productId, Long unitId) {
        Set<Long> branchIds = productService.activeBranchIds(productId);
        for (Long branchId : branchIds) {
            if (unitAccessService.findActiveForBranch(businessId, branchId, unitId).isEmpty())
                throw new ProductConfigurationConflictException(
                        "Conversion unit is not active for every branch assigned to this product");
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
