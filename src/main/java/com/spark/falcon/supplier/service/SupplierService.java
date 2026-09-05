package com.spark.falcon.supplier.service;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.inventory.dto.SupplierInventoryExpiryProjectionResponse;
import com.spark.falcon.inventory.service.InventorySupplierReadService;
import com.spark.falcon.product.dto.response.ProductResponse;
import com.spark.falcon.product.exception.ProductNotFoundException;
import com.spark.falcon.product.service.ProductService;
import com.spark.falcon.purchase.dto.response.SupplierReturnProjectionResponse;
import com.spark.falcon.purchase.service.PurchaseSupplierReadService;
import com.spark.falcon.supplier.dto.SupplierExpiryPerformanceResponse;
import com.spark.falcon.supplier.dto.SupplierRequest;
import com.spark.falcon.supplier.dto.SupplierResponse;
import com.spark.falcon.supplier.entity.ProductSupplier;
import com.spark.falcon.supplier.entity.Supplier;
import com.spark.falcon.supplier.entity.SupplierBranch;
import com.spark.falcon.supplier.exception.SupplierAccessDeniedException;
import com.spark.falcon.supplier.exception.SupplierNotFoundException;
import com.spark.falcon.supplier.repository.ProductSupplierRepository;
import com.spark.falcon.supplier.repository.SupplierBranchRepository;
import com.spark.falcon.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierBranchRepository supplierBranchRepository;
    private final ProductSupplierRepository productSupplierRepository;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final ProductService productService;
    private final InventorySupplierReadService inventorySupplierReadService;
    private final PurchaseSupplierReadService purchaseSupplierReadService;
    private final Clock clock;

    @Transactional
    public SupplierResponse create(Long ownerId, SupplierRequest request) {
        BusinessAccessResponse business = business(ownerId);
        Set<Long> branchIds = validateBranchAssignments(business.businessId(), request.getBranchIds());
        Instant now = Instant.now(clock);

        Supplier supplier = new Supplier();
        supplier.setBusinessId(business.businessId());
        copyRequestToSupplier(request, supplier);
        supplier.setCreatedAt(now);
        supplier.setUpdatedAt(now);

        Supplier saved = supplierRepository.saveAndFlush(supplier);
        syncBranchAssignments(saved.getId(), branchIds, now);
        return toResponse(saved);
    }

    @Transactional
    public SupplierResponse update(Long ownerId, Long supplierId, SupplierRequest request) {
        BusinessAccessResponse business = business(ownerId);
        Set<Long> branchIds = validateBranchAssignments(business.businessId(), request.getBranchIds());
        Supplier supplier = supplierRepository.findByIdAndBusinessId(supplierId, business.businessId())
                .orElseThrow(SupplierNotFoundException::new);

        if (supplier.getArchivedAt() != null) {
            throw new SupplierAccessDeniedException();
        }

        copyRequestToSupplier(request, supplier);
        Instant now = Instant.now(clock);
        supplier.setUpdatedAt(now);

        Supplier saved = supplierRepository.saveAndFlush(supplier);
        syncBranchAssignments(saved.getId(), branchIds, now);
        return toResponse(saved);
    }

    @Transactional
    public SupplierResponse archive(Long ownerId, Long supplierId) {
        BusinessAccessResponse business = business(ownerId);
        Supplier supplier = supplierRepository.findByIdAndBusinessId(supplierId, business.businessId())
                .orElseThrow(SupplierNotFoundException::new);

        if (supplier.getArchivedAt() == null) {
            Instant now = Instant.now(clock);
            supplier.setActive(false);
            supplier.setArchivedAt(now);
            supplier.setUpdatedAt(now);
        }

        return toResponse(supplierRepository.saveAndFlush(supplier));
    }

    @Transactional(readOnly = true)
    public Optional<SupplierResponse> findByOwnerAndId(Long ownerId, Long supplierId) {
        BusinessAccessResponse business = business(ownerId);
        return supplierRepository.findByIdAndBusinessId(supplierId, business.businessId())
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<SupplierResponse> findActiveByBusinessIdAndId(Long businessId, Long supplierId) {
        return supplierRepository.findByIdAndBusinessId(supplierId, businessId)
                .filter(supplier -> supplier.isActive() && supplier.getArchivedAt() == null)
                .map(this::toResponse);
    }

    public Optional<SupplierResponse> findActiveForBranch(Long businessId, Long branchId, Long supplierId) {
        if (!supplierBranchRepository.existsBySupplierIdAndBranchId(supplierId, branchId)) {
            return Optional.empty();
        }
        return findActiveByBusinessIdAndId(businessId, supplierId);
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> findAll(Long ownerId, String keyword, Pageable pageable) {
        BusinessAccessResponse business = business(ownerId);
        String safeKeyword = keyword == null ? "" : keyword.trim();
        return supplierRepository.searchActive(business.businessId(), safeKeyword, pageable)
                .map(this::toResponse);
    }

    public Page<SupplierResponse> findAllForBranch(
            Long ownerId, Long branchId, String keyword, Pageable pageable) {
        BusinessAccessResponse business = business(ownerId);
        if (branchAccessService.findActiveByBusinessIdAndBranchId(business.businessId(), branchId).isEmpty()) {
            throw new SupplierAccessDeniedException();
        }
        String safeKeyword = keyword == null ? "" : keyword.trim();
        return supplierRepository.searchActiveForBranch(
                        business.businessId(), branchId, safeKeyword, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<SupplierResponse>> findLinkedSuppliersByProductIds(Long ownerId, List<Long> productIds) {
        Long businessId = business(ownerId).businessId();
        if (productIds == null || productIds.isEmpty()) return Map.of();
        List<ProductSupplier> links = productSupplierRepository
                .findByProductIdInOrderByProductIdAscSupplierIdAsc(List.copyOf(productIds));
        Map<Long, SupplierResponse> suppliers = supplierRepository.findAllById(
                        links.stream().map(ProductSupplier::getSupplierId).distinct().toList()).stream()
                .filter(value -> businessId.equals(value.getBusinessId()))
                .collect(java.util.stream.Collectors.toMap(Supplier::getId, this::toResponse));
        Map<Long, List<SupplierResponse>> result = new LinkedHashMap<>();
        for (ProductSupplier link : links) {
            SupplierResponse supplier = suppliers.get(link.getSupplierId());
            if (supplier != null) result.computeIfAbsent(link.getProductId(), ignored -> new ArrayList<>()).add(supplier);
        }
        return result;
    }

    public Set<Long> findAssignedBranchIds(Long ownerId, Long supplierId) {
        BusinessAccessResponse business = business(ownerId);
        supplierRepository.findByIdAndBusinessId(supplierId, business.businessId())
                .orElseThrow(SupplierNotFoundException::new);
        return supplierBranchRepository.findBySupplierIdOrderByBranchIdAsc(supplierId).stream()
                .map(SupplierBranch::getBranchId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public SupplierExpiryPerformanceResponse findExpiryPerformance(
            Long ownerId, Long branchId, Long supplierId) {
        BusinessAccessResponse business = business(ownerId);
        supplierRepository.findByIdAndBusinessId(supplierId, business.businessId())
                .orElseThrow(SupplierNotFoundException::new);
        if (branchAccessService.findByBusinessIdAndBranchId(business.businessId(), branchId).isEmpty()) {
            throw new SupplierAccessDeniedException();
        }

        SupplierInventoryExpiryProjectionResponse inventory = inventorySupplierReadService
                .projectSupplierExpiry(business.businessId(), branchId, supplierId);
        SupplierReturnProjectionResponse returns = purchaseSupplierReadService
                .projectSupplierReturns(
                        business.businessId(), branchId, supplierId, inventory.expiryControlledBatchIds());

        return new SupplierExpiryPerformanceResponse(
                inventory.activeExpiryControlledBatchCount(),
                inventory.nearExpiryBatchCount(),
                inventory.expiredBatchCount(),
                returns.expiryBatchPurchaseReturnCount(),
                returns.refundAndCreditAmount(),
                inventory.expiryLossAmount()
        );
    }

    @Transactional
    public void assignProduct(Long ownerId, Long supplierId, Long productId) {
        BusinessAccessResponse business = business(ownerId);
        Supplier supplier = supplierRepository.findByIdAndBusinessId(supplierId, business.businessId())
                .orElseThrow(SupplierNotFoundException::new);

        if (supplier.getArchivedAt() != null) {
            throw new SupplierAccessDeniedException();
        }

        ProductResponse product = productService.findByOwnerAndId(ownerId, productId)
                .orElseThrow(ProductNotFoundException::new);
        if (product.archived()) {
            throw new ProductNotFoundException();
        }

        if (productSupplierRepository.existsBySupplierIdAndProductId(supplierId, productId)) {
            return;
        }

        ProductSupplier productSupplier = new ProductSupplier();
        productSupplier.setSupplierId(supplierId);
        productSupplier.setProductId(productId);
        productSupplier.setCreatedAt(Instant.now(clock));
        productSupplierRepository.saveAndFlush(productSupplier);
    }

    @Transactional
    public void removeProduct(Long ownerId, Long supplierId, Long productId) {
        BusinessAccessResponse business = business(ownerId);
        supplierRepository.findByIdAndBusinessId(supplierId, business.businessId())
                .orElseThrow(SupplierNotFoundException::new);

        productService.findByOwnerAndId(ownerId, productId)
                .orElseThrow(ProductNotFoundException::new);
        productSupplierRepository.deleteBySupplierIdAndProductId(supplierId, productId);
    }

    @Transactional(readOnly = true)
    public List<Long> findLinkedProductIds(Long ownerId, Long supplierId) {
        BusinessAccessResponse business = business(ownerId);
        supplierRepository.findByIdAndBusinessId(supplierId, business.businessId())
                .orElseThrow(SupplierNotFoundException::new);

        return productSupplierRepository.findBySupplierIdOrderByIdAsc(supplierId).stream()
                .map(ProductSupplier::getProductId)
                .toList();
    }

    private Set<Long> validateBranchAssignments(Long businessId, Set<Long> requestedBranchIds) {
        if (requestedBranchIds == null || requestedBranchIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one branch");
        }
        Set<Long> branchIds = requestedBranchIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (branchIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one branch");
        }
        for (Long branchId : branchIds) {
            if (branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId).isEmpty()) {
                throw new SupplierAccessDeniedException();
            }
        }
        return branchIds;
    }

    private void syncBranchAssignments(Long supplierId, Set<Long> requestedBranchIds, Instant now) {
        List<SupplierBranch> existing = supplierBranchRepository.findBySupplierIdOrderByBranchIdAsc(supplierId);
        Set<Long> existingIds = existing.stream()
                .map(SupplierBranch::getBranchId)
                .collect(java.util.stream.Collectors.toSet());

        List<SupplierBranch> removed = existing.stream()
                .filter(value -> !requestedBranchIds.contains(value.getBranchId()))
                .toList();
        if (!removed.isEmpty()) {
            supplierBranchRepository.deleteAll(removed);
        }

        List<SupplierBranch> added = requestedBranchIds.stream()
                .filter(branchId -> !existingIds.contains(branchId))
                .map(branchId -> {
                    SupplierBranch value = new SupplierBranch();
                    value.setSupplierId(supplierId);
                    value.setBranchId(branchId);
                    value.setCreatedAt(now);
                    return value;
                })
                .toList();
        if (!added.isEmpty()) {
            supplierBranchRepository.saveAll(added);
        }
    }

    private BusinessAccessResponse business(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId)
                .orElseThrow(SupplierAccessDeniedException::new);
    }

    private void copyRequestToSupplier(SupplierRequest request, Supplier supplier) {
        supplier.setName(required(request.getName(), "Supplier name is required"));
        supplier.setCodeName(optional(request.getCodeName()));
        supplier.setEmail(normalizeEmail(request.getEmail()));
        supplier.setMobileNumber(optional(request.getMobileNumber()));
        supplier.setAddress(optional(request.getAddress()));
        supplier.setCity(optional(request.getCity()));
        supplier.setState(optional(request.getState()));
        supplier.setCountry(optional(request.getCountry()));
        supplier.setAdditionalDetails(optional(request.getAdditionalDetails()));
        supplier.setActive(request.isActive());
        supplier.setDisplayOrder(request.getDisplayOrder());
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getBusinessId(),
                supplier.getName(),
                supplier.getCodeName(),
                supplier.getEmail(),
                supplier.getMobileNumber(),
                supplier.getAddress(),
                supplier.getCity(),
                supplier.getState(),
                supplier.getCountry(),
                supplier.getAdditionalDetails(),
                supplier.isActive(),
                supplier.getDisplayOrder(),
                productSupplierRepository.countBySupplierId(supplier.getId()),
                supplier.getArchivedAt() != null,
                supplier.getCreatedAt()
        );
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeEmail(String value) {
        String email = optional(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }
}
