package com.spark.falcon.product.service;

import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.product.dto.command.CreateProductVariantCommand;
import com.spark.falcon.product.dto.command.UpdateProductVariantCommand;
import com.spark.falcon.product.dto.response.ProductVariantResponse;
import com.spark.falcon.product.entity.Product;
import com.spark.falcon.product.entity.ProductAuditEvent;
import com.spark.falcon.product.entity.ProductVariant;
import com.spark.falcon.product.entity.enumtype.ProductAuditAction;
import com.spark.falcon.product.entity.enumtype.ProductStatus;
import com.spark.falcon.product.exception.*;
import com.spark.falcon.product.mapper.ProductMapper;
import com.spark.falcon.product.repository.ProductAuditEventRepository;
import com.spark.falcon.product.repository.ProductRepository;
import com.spark.falcon.product.repository.ProductVariantRepository;
import com.spark.falcon.product.validation.ProductValidator;
import com.spark.falcon.settings.service.UnitAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductVariantService {
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductAuditEventRepository auditRepository;
    private final BusinessAccessService businessAccessService;
    private final ProductService productService;
    private final UnitAccessService unitAccessService;
    private final ProductMapper mapper;
    private final ProductValidator validator;
    private final Clock clock;

    @Transactional
    public ProductVariantResponse create(CreateProductVariantCommand command) {
        BusinessAccessResponse business = business(command.ownerId());
        Product product = product(command.productId(), business.businessId());
        validator.validate(command);
        validateUnits(business.businessId(), product.getId(), command.baseInventoryUnitId(),
                command.purchaseUnitId(), command.sellingUnitId());
        String sku = validator.normalizeSku(command.sku());
        if (variantRepository.existsByProductIdAndSkuIgnoreCase(product.getId(), sku))
            throw new ProductVariantSkuAlreadyUsedException(sku);

        Instant now = Instant.now(clock);
        ProductVariant variant = variantRepository.saveAndFlush(ProductVariant.create(product.getId(),
                command.variantName(), sku, command.baseInventoryUnitId(), command.purchaseUnitId(),
                command.sellingUnitId(), command.sellingPrice(), command.reorderLevel(), command.status(), now));
        auditRepository.save(ProductAuditEvent.record(business.businessId(), product.getId(), command.ownerId(),
                ProductAuditAction.VARIANT_CREATED, "PRODUCT_VARIANT", variant.getId(), now));
        return mapper.toResponse(variant);
    }

    @Transactional
    public ProductVariantResponse update(UpdateProductVariantCommand command) {
        BusinessAccessResponse business = business(command.ownerId());
        Product product = product(command.productId(), business.businessId());
        ProductVariant variant = variantRepository.findByIdAndProductId(command.variantId(), product.getId())
                .orElseThrow(ProductVariantNotFoundException::new);
        validator.validate(command);
        validateUnits(business.businessId(), product.getId(), command.baseInventoryUnitId(),
                command.purchaseUnitId(), command.sellingUnitId());
        String sku = validator.normalizeSku(command.sku());
        if (variantRepository.existsByProductIdAndSkuIgnoreCaseAndIdNot(product.getId(), sku, variant.getId()))
            throw new ProductVariantSkuAlreadyUsedException(sku);

        Instant now = Instant.now(clock);
        variant.update(command.variantName(), sku, command.baseInventoryUnitId(), command.purchaseUnitId(),
                command.sellingUnitId(), command.sellingPrice(), command.reorderLevel(), command.status(), now);
        ProductVariant saved = variantRepository.saveAndFlush(variant);
        auditRepository.save(ProductAuditEvent.record(business.businessId(), product.getId(), command.ownerId(),
                ProductAuditAction.VARIANT_UPDATED, "PRODUCT_VARIANT", saved.getId(), now));
        return mapper.toResponse(saved);
    }

    @Transactional
    public ProductVariantResponse changeStatus(Long ownerId, Long productId, Long variantId, ProductStatus status) {
        BusinessAccessResponse business = business(ownerId);
        Product product = product(productId, business.businessId());
        ProductVariant variant = variantRepository.findByIdAndProductId(variantId, product.getId())
                .orElseThrow(ProductVariantNotFoundException::new);
        Instant now = Instant.now(clock);
        variant.changeStatus(status, now);
        ProductVariant saved = variantRepository.saveAndFlush(variant);
        auditRepository.save(ProductAuditEvent.record(business.businessId(), product.getId(), ownerId,
                ProductAuditAction.VARIANT_STATUS_CHANGED, "PRODUCT_VARIANT", saved.getId(), now));
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductVariantResponse> findAll(Long ownerId, Long productId) {
        BusinessAccessResponse business = business(ownerId);
        product(productId, business.businessId());
        return variantRepository.findByProductIdOrderByVariantNameAsc(productId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ProductVariantResponse>> findAllForProducts(Long ownerId, List<Long> productIds) {
        Long businessId = business(ownerId).businessId();
        if (productIds == null || productIds.isEmpty()) return Map.of();
        return variantRepository.findForBusinessAndProductIds(businessId, List.copyOf(productIds)).stream()
                .map(mapper::toResponse)
                .collect(java.util.stream.Collectors.groupingBy(ProductVariantResponse::productId,
                        java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));
    }

    ProductVariant requireVariant(Long productId, Long variantId) {
        return variantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(ProductVariantNotFoundException::new);
    }

    private Product product(Long productId, Long businessId) {
        return productRepository.findByIdAndBusinessIdAndArchivedAtIsNull(productId, businessId)
                .orElseThrow(ProductNotFoundException::new);
    }

    private BusinessAccessResponse business(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId).orElseThrow(ProductAccessDeniedException::new);
    }

    private void validateUnits(Long businessId, Long productId, Long... unitIds) {
        Set<Long> branchIds = productService.activeBranchIds(productId);
        if (branchIds.isEmpty()) throw new ProductAccessDeniedException();
        LinkedHashSet<Long> distinctUnitIds = new LinkedHashSet<>();
        for (Long unitId : unitIds) if (unitId != null) distinctUnitIds.add(unitId);
        for (Long branchId : branchIds) {
            for (Long unitId : distinctUnitIds) {
                if (unitAccessService.findActiveForBranch(businessId, branchId, unitId).isEmpty())
                    throw new ProductConfigurationConflictException(
                            "Selected unit is not active for every branch assigned to this product");
            }
        }
    }
}
