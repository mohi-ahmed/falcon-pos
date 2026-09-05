package com.spark.falcon.product.service;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.product.dto.command.CreateProductCommand;
import com.spark.falcon.product.dto.command.UpdateProductCommand;
import com.spark.falcon.product.dto.response.ProductResponse;
import com.spark.falcon.product.entity.BranchProduct;
import com.spark.falcon.product.entity.Product;
import com.spark.falcon.product.entity.ProductAuditEvent;
import com.spark.falcon.product.entity.ProductImage;
import com.spark.falcon.product.entity.enumtype.ProductAuditAction;
import com.spark.falcon.product.entity.enumtype.ProductStatus;
import com.spark.falcon.product.exception.ProductAccessDeniedException;
import com.spark.falcon.product.exception.ProductNotFoundException;
import com.spark.falcon.product.exception.ProductReferenceCodeAlreadyUsedException;
import com.spark.falcon.product.mapper.ProductMapper;
import com.spark.falcon.product.repository.BranchProductRepository;
import com.spark.falcon.product.repository.ProductAuditEventRepository;
import com.spark.falcon.product.repository.ProductRepository;
import com.spark.falcon.product.repository.ProductVariantRepository;
import com.spark.falcon.product.repository.CategoryRepository;
import com.spark.falcon.product.repository.ProductImageRepository;
import com.spark.falcon.product.validation.ProductValidator;
import com.spark.falcon.settings.service.TaxRateAccessService;
import com.spark.falcon.inventory.service.InventoryProductHistoryAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final BranchProductRepository branchProductRepository;
    private final ProductAuditEventRepository auditRepository;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final TaxRateAccessService taxRateAccessService;
    private final InventoryProductHistoryAccessService inventoryProductHistoryAccessService;
    private final ProductMapper mapper;
    private final ProductValidator validator;
    private final Clock clock;

    @Transactional
    public ProductResponse create(CreateProductCommand command) {
        BusinessAccessResponse business = business(command.ownerId());
        validator.validate(command);
        Set<Long> branchIds = validator.copyBranchIds(command.branchIds());
        validateBranches(business.businessId(), branchIds);
        validateTaxRate(business.businessId(), command.taxRateId());
        validateCategory(business.businessId(), command.categoryId());

        String referenceCode = validator.normalizeReferenceCode(command.referenceCode());
        if (productRepository.existsByBusinessIdAndReferenceCodeIgnoreCase(business.businessId(), referenceCode))
            throw new ProductReferenceCodeAlreadyUsedException(referenceCode);

        Instant now = Instant.now(clock);
        Product product = productRepository.saveAndFlush(Product.create(business.businessId(), command.name(),
                referenceCode, command.productType(), command.categoryId(), command.brand(), command.barcodeFormat(), command.packagingType(),
                command.taxRateId(), command.taxCalculationMethod(), command.description(), command.thumbnailReference(),
                command.trackExpiry(), command.expiryType(), command.batchTrackingRequired(),
                command.defaultShelfLifeDays(), command.expiryAlertBeforeDays(), command.blockSaleAfterExpiry(),
                command.displayOrder(), command.status(), now));

        Set<Long> assignedBranches = syncBranches(product.getId(), branchIds, now);
        syncImages(product, command.images(), command.thumbnailReference(), now);
        auditRepository.save(ProductAuditEvent.record(business.businessId(), product.getId(), command.ownerId(),
                ProductAuditAction.PRODUCT_CREATED, "PRODUCT", product.getId(), now));
        return response(product, assignedBranches);
    }

    @Transactional
    public ProductResponse update(UpdateProductCommand command) {
        BusinessAccessResponse business = business(command.ownerId());
        validator.validate(command);
        Set<Long> branchIds = validator.copyBranchIds(command.branchIds());
        validateBranches(business.businessId(), branchIds);
        validateTaxRate(business.businessId(), command.taxRateId());
        validateCategory(business.businessId(), command.categoryId());

        Product product = productRepository.findByIdAndBusinessId(command.productId(), business.businessId())
                .orElseThrow(ProductNotFoundException::new);
        boolean expiryTrackingDisabled = product.isTrackExpiry() && !command.trackExpiry();
        boolean hasExpiryHistory = expiryTrackingDisabled && inventoryProductHistoryAccessService
                .hasBatchOrMovementHistory(business.businessId(), productVariantRepository
                        .findByProductIdOrderByVariantNameAsc(product.getId()).stream().map(value -> value.getId()).toList());
        String referenceCode = validator.normalizeReferenceCode(command.referenceCode());
        if (productRepository.existsByBusinessIdAndReferenceCodeIgnoreCaseAndIdNot(
                business.businessId(), referenceCode, product.getId()))
            throw new ProductReferenceCodeAlreadyUsedException(referenceCode);

        Instant now = Instant.now(clock);
        product.update(command.name(), referenceCode, command.productType(), command.categoryId(), command.brand(), command.barcodeFormat(),
                command.packagingType(), command.taxRateId(), command.taxCalculationMethod(), command.description(),
                command.thumbnailReference(), command.trackExpiry(), command.expiryType(),
                command.batchTrackingRequired(), command.defaultShelfLifeDays(), command.expiryAlertBeforeDays(),
                command.blockSaleAfterExpiry(), command.displayOrder(), command.status(), now);
        Product saved = productRepository.saveAndFlush(product);
        Set<Long> assignedBranches = syncBranches(saved.getId(), branchIds, now);
        syncImages(saved, command.images(), command.thumbnailReference(), now);
        auditRepository.save(ProductAuditEvent.record(business.businessId(), saved.getId(), command.ownerId(),
                ProductAuditAction.PRODUCT_UPDATED, "PRODUCT", saved.getId(), now));
        if (hasExpiryHistory) {
            auditRepository.save(ProductAuditEvent.record(business.businessId(), saved.getId(), command.ownerId(),
                    ProductAuditAction.EXPIRY_TRACKING_DISABLED, "PRODUCT", saved.getId(), now));
        }
        return response(saved, assignedBranches);
    }

    @Transactional
    public ProductResponse changeStatus(Long ownerId, Long productId, ProductStatus status) {
        BusinessAccessResponse business = business(ownerId);
        Product product = productRepository.findByIdAndBusinessId(productId, business.businessId())
                .orElseThrow(ProductNotFoundException::new);
        Instant now = Instant.now(clock);
        product.changeStatus(status, now);
        Product saved = productRepository.saveAndFlush(product);
        auditRepository.save(ProductAuditEvent.record(business.businessId(), saved.getId(), ownerId,
                ProductAuditAction.PRODUCT_STATUS_CHANGED, "PRODUCT", saved.getId(), now));
        return response(saved, activeBranchIds(saved.getId()));
    }

    @Transactional
    public ProductResponse archive(Long ownerId, Long productId) {
        BusinessAccessResponse business = business(ownerId);
        Product product = productRepository.findByIdAndBusinessId(productId, business.businessId())
                .orElseThrow(ProductNotFoundException::new);
        Instant now = Instant.now(clock);
        product.archive(now);
        Product saved = productRepository.saveAndFlush(product);
        auditRepository.save(ProductAuditEvent.record(business.businessId(), saved.getId(), ownerId,
                ProductAuditAction.PRODUCT_ARCHIVED, "PRODUCT", saved.getId(), now));
        return response(saved, activeBranchIds(saved.getId()));
    }

    @Transactional
    public ProductResponse restore(Long ownerId, Long productId) {
        BusinessAccessResponse business = business(ownerId);
        Product product = productRepository.findByIdAndBusinessId(productId, business.businessId())
                .orElseThrow(ProductNotFoundException::new);
        Instant now = Instant.now(clock);
        product.restore(now);
        Product saved = productRepository.saveAndFlush(product);
        auditRepository.save(ProductAuditEvent.record(business.businessId(), saved.getId(), ownerId,
                ProductAuditAction.PRODUCT_RESTORED, "PRODUCT", saved.getId(), now));
        return response(saved, activeBranchIds(saved.getId()));
    }

    @Transactional
    public void bulkArchive(Long ownerId, Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) throw new IllegalArgumentException("Select at least one product");
        new LinkedHashSet<>(productIds).forEach(productId -> archive(ownerId, productId));
    }

    @Transactional
    public void bulkRestore(Long ownerId, Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) throw new IllegalArgumentException("Select at least one product");
        new LinkedHashSet<>(productIds).forEach(productId -> restore(ownerId, productId));
    }

    @Transactional(readOnly = true)
    public Optional<ProductResponse> findByOwnerAndId(Long ownerId, Long productId) {
        BusinessAccessResponse business = business(ownerId);
        return productRepository.findByIdAndBusinessId(productId, business.businessId())
                .map(product -> response(product, activeBranchIds(product.getId())));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(Long ownerId) {
        BusinessAccessResponse business = business(ownerId);
        return productRepository.findByBusinessIdOrderByDisplayOrderAscNameAsc(business.businessId()).stream()
                .map(product -> response(product, activeBranchIds(product.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(Long ownerId, Long branchId, Long categoryId, ProductStatus status,
                                        Boolean archived, String keyword, Pageable pageable) {
        Long businessId = business(ownerId).businessId();
        String query = keyword == null ? "" : keyword.trim();
        Page<Product> page = productRepository.search(businessId, branchId, categoryId, status, archived, query, pageable);
        List<Long> ids = page.getContent().stream().map(Product::getId).toList();
        Map<Long, Set<Long>> branches = new HashMap<>();
        if (!ids.isEmpty()) branchProductRepository.findByProductIdIn(ids).stream().filter(BranchProduct::isActive)
                .forEach(value -> branches.computeIfAbsent(value.getProductId(), ignored -> new LinkedHashSet<>()).add(value.getBranchId()));
        Map<Long, List<ProductImage>> images = ids.isEmpty() ? Map.of() : productImageRepository
                .findByProductIdInOrderByProductIdAscDisplayOrderAscIdAsc(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(ProductImage::getProductId));
        return page.map(product -> response(product, branches.getOrDefault(product.getId(), Set.of()),
                images.getOrDefault(product.getId(), List.of())));
    }

    private BusinessAccessResponse business(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId).orElseThrow(ProductAccessDeniedException::new);
    }

    private void validateBranches(Long businessId, Set<Long> branchIds) {
        for (Long branchId : branchIds) {
            if (branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId).isEmpty())
                throw new ProductAccessDeniedException();
        }
    }

    private void validateTaxRate(Long businessId, Long taxRateId) {
        if (taxRateId != null && taxRateAccessService.findActive(businessId, taxRateId).isEmpty())
            throw new ProductAccessDeniedException();
    }

    private void validateCategory(Long businessId, Long categoryId) {
        if (categoryId != null && categoryRepository.findByIdAndBusinessId(categoryId, businessId)
                .filter(category -> !category.isArchived())
                .isEmpty()) throw new ProductAccessDeniedException();
    }

    private void syncImages(Product product, List<com.spark.falcon.product.dto.request.ProductImageRequest> requests,
                            String thumbnailReference, Instant now) {
        List<com.spark.falcon.product.dto.request.ProductImageRequest> values = requests == null ? List.of() : requests;
        if (values.isEmpty() && !productImageRepository.findByProductIdOrderByDisplayOrderAscIdAsc(product.getId()).isEmpty()) return;
        productImageRepository.deleteByProductId(product.getId());
        long primaryCount = values.stream().filter(value -> value != null && value.isPrimaryImage()).count();
        if (primaryCount > 1) throw new IllegalArgumentException("Only one primary product image is allowed");
        for (var value : values) {
            if (value == null || value.getImageReference() == null || value.getImageReference().isBlank()) continue;
            if (value.getDisplayOrder() < 0) throw new IllegalArgumentException("Image display order must not be negative");
            productImageRepository.save(ProductImage.create(product.getId(), value.getImageReference(),
                    value.getDisplayOrder(), value.isPrimaryImage(), now));
        }
        if (primaryCount == 0 && thumbnailReference != null && !thumbnailReference.isBlank()) {
            productImageRepository.save(ProductImage.create(product.getId(), thumbnailReference, 0, true, now));
        }
    }

    private ProductResponse response(Product product, Set<Long> branchIds) {
        return response(product, branchIds, productImageRepository.findByProductIdOrderByDisplayOrderAscIdAsc(product.getId()));
    }

    private ProductResponse response(Product product, Set<Long> branchIds, List<ProductImage> productImages) {
        var images = productImages.stream()
                .map(value -> new com.spark.falcon.product.dto.response.ProductImageResponse(value.getId(),
                        value.getImageReference(), value.getDisplayOrder(), value.isPrimaryImage()))
                .toList();
        return mapper.toResponse(product, branchIds, images);
    }

    private Set<Long> syncBranches(Long productId, Set<Long> requestedBranchIds, Instant now) {
        Map<Long, BranchProduct> existing = new LinkedHashMap<>();
        branchProductRepository.findByProductId(productId).forEach(value -> existing.put(value.getBranchId(), value));

        for (Long branchId : requestedBranchIds) {
            BranchProduct assignment = existing.get(branchId);
            if (assignment == null) {
                branchProductRepository.save(BranchProduct.assign(productId, branchId, now));
            } else if (!assignment.isActive()) {
                assignment.activate(now);
                branchProductRepository.save(assignment);
            }
        }

        for (BranchProduct assignment : existing.values()) {
            if (!requestedBranchIds.contains(assignment.getBranchId()) && assignment.isActive()) {
                assignment.deactivate(now);
                branchProductRepository.save(assignment);
            }
        }
        return activeBranchIds(productId);
    }

    Set<Long> activeBranchIds(Long productId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        branchProductRepository.findByProductId(productId).stream()
                .filter(BranchProduct::isActive)
                .map(BranchProduct::getBranchId)
                .forEach(ids::add);
        return ids;
    }
}
