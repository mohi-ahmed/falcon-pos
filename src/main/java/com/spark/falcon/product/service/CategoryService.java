package com.spark.falcon.product.service;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.product.dto.request.CategoryRequest;
import com.spark.falcon.product.dto.response.CategoryResponse;
import com.spark.falcon.product.entity.BranchCategory;
import com.spark.falcon.product.entity.Category;
import com.spark.falcon.product.exception.ProductAccessDeniedException;
import com.spark.falcon.product.repository.BranchCategoryRepository;
import com.spark.falcon.product.repository.CategoryRepository;
import com.spark.falcon.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.spark.falcon.product.entity.enumtype.ProductStatus;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final BranchCategoryRepository branchCategoryRepository;
    private final ProductRepository productRepository;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final Clock clock;

    @Transactional
    public CategoryResponse create(Long ownerId, CategoryRequest request) {
        BusinessAccessResponse business = business(ownerId);
        String slug = slug(request.getSlug(), request.getName());
        validate(business.businessId(), null, request, slug);
        Instant now = Instant.now(clock);
        Category category = categoryRepository.saveAndFlush(Category.create(business.businessId(), request.getName(),
                slug, request.getParentCategoryId(), request.getDescription(), request.getThumbnailReference(),
                request.getDisplayOrder(), request.getStatus(), now));
        return response(category, syncBranches(category.getId(), request.getBranchIds(), now));
    }

    @Transactional
    public CategoryResponse update(Long ownerId, Long categoryId, CategoryRequest request) {
        BusinessAccessResponse business = business(ownerId);
        Category category = require(business.businessId(), categoryId);
        String slug = slug(request.getSlug(), request.getName());
        validate(business.businessId(), categoryId, request, slug);
        Instant now = Instant.now(clock);
        category.update(request.getName(), slug, request.getParentCategoryId(), request.getDescription(),
                request.getThumbnailReference(), request.getDisplayOrder(), request.getStatus(), now);
        return response(categoryRepository.saveAndFlush(category), syncBranches(categoryId, request.getBranchIds(), now));
    }

    @Transactional
    public CategoryResponse archive(Long ownerId, Long categoryId) {
        BusinessAccessResponse business = business(ownerId);
        Category category = require(business.businessId(), categoryId);
        category.archive(Instant.now(clock));
        return response(categoryRepository.saveAndFlush(category), branchIds(categoryId));
    }

    @Transactional
    public CategoryResponse restore(Long ownerId, Long categoryId) {
        BusinessAccessResponse business = business(ownerId);
        Category category = require(business.businessId(), categoryId);
        category.restore(Instant.now(clock));
        return response(categoryRepository.saveAndFlush(category), branchIds(categoryId));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll(Long ownerId) {
        Long businessId = business(ownerId).businessId();
        return categoryRepository.findByBusinessIdOrderByDisplayOrderAscNameAsc(businessId).stream()
                .map(value -> response(value, branchIds(value.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findOne(Long ownerId, Long categoryId) {
        Long businessId = business(ownerId).businessId();
        Category value = require(businessId, categoryId);
        return response(value, branchIds(categoryId));
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> search(Long ownerId, Long branchId, ProductStatus status, Boolean archived,
                                         String keyword, Pageable pageable) {
        Long businessId = business(ownerId).businessId();
        Page<Category> page = categoryRepository.search(businessId, branchId, status, archived,
                keyword == null ? "" : keyword.trim(), pageable);
        List<Long> ids = page.getContent().stream().map(Category::getId).toList();
        Map<Long, Set<Long>> branches = new HashMap<>();
        if (!ids.isEmpty()) branchCategoryRepository.findByCategoryIdIn(ids).stream().filter(BranchCategory::isActive)
                .forEach(value -> branches.computeIfAbsent(value.getCategoryId(), ignored -> new LinkedHashSet<>()).add(value.getBranchId()));
        Map<Long, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) productRepository.countByCategoryIds(ids)
                .forEach(value -> counts.put((Long) value[0], (Long) value[1]));
        return page.map(value -> new CategoryResponse(value.getId(), value.getName(), value.getSlug(), value.getParentCategoryId(),
                value.getDescription(), value.getThumbnailReference(), value.getDisplayOrder(), value.getStatus(),
                Set.copyOf(branches.getOrDefault(value.getId(), Set.of())), counts.getOrDefault(value.getId(), 0L),
                value.isArchived(), value.getCreatedAt()));
    }

    private void validate(Long businessId, Long categoryId, CategoryRequest request, String slug) {
        if (request.getDisplayOrder() < 0) throw new IllegalArgumentException("displayOrder must not be negative");
        if (request.getBranchIds() == null || request.getBranchIds().isEmpty())
            throw new IllegalArgumentException("At least one branch assignment is required");
        for (Long branchId : request.getBranchIds()) {
            if (branchId == null || branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId).isEmpty())
                throw new ProductAccessDeniedException();
        }
        if (request.getParentCategoryId() != null) {
            if (Objects.equals(categoryId, request.getParentCategoryId()))
                throw new IllegalArgumentException("Category cannot be its own parent");
            require(businessId, request.getParentCategoryId());
        }
    }

    private Set<Long> syncBranches(Long categoryId, Set<Long> requested, Instant now) {
        Map<Long, BranchCategory> existing = new LinkedHashMap<>();
        branchCategoryRepository.findByCategoryId(categoryId).forEach(v -> existing.put(v.getBranchId(), v));
        for (Long branchId : requested) {
            BranchCategory value = existing.get(branchId);
            if (value == null) branchCategoryRepository.save(BranchCategory.assign(categoryId, branchId, now));
            else if (!value.isActive()) { value.activate(now); branchCategoryRepository.save(value); }
        }
        for (BranchCategory value : existing.values()) {
            if (!requested.contains(value.getBranchId()) && value.isActive()) {
                value.deactivate(now); branchCategoryRepository.save(value);
            }
        }
        return branchIds(categoryId);
    }

    private Set<Long> branchIds(Long categoryId) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        branchCategoryRepository.findByCategoryId(categoryId).stream().filter(BranchCategory::isActive)
                .map(BranchCategory::getBranchId).forEach(result::add);
        return result;
    }

    private CategoryResponse response(Category value, Set<Long> branchIds) {
        return new CategoryResponse(value.getId(), value.getName(), value.getSlug(), value.getParentCategoryId(),
                value.getDescription(), value.getThumbnailReference(), value.getDisplayOrder(), value.getStatus(),
                Set.copyOf(branchIds), productRepository.countByCategoryId(value.getId()), value.isArchived(), value.getCreatedAt());
    }

    private Category require(Long businessId, Long id) {
        return categoryRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Category was not found"));
    }
    private BusinessAccessResponse business(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId).orElseThrow(ProductAccessDeniedException::new);
    }
    private String slug(String supplied, String name) {
        String source = supplied == null || supplied.isBlank() ? name : supplied;
        String normalized = Normalizer.normalize(source.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String value = normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (value.isBlank()) throw new IllegalArgumentException("Category slug is required");
        return value;
    }
}
