package com.spark.falcon.expense.service;

import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.expense.dto.ExpenseCategoryFilter;
import com.spark.falcon.expense.dto.ExpenseCategoryRequest;
import com.spark.falcon.expense.dto.ExpenseCategoryResponse;
import com.spark.falcon.expense.entity.ExpenseCategory;
import com.spark.falcon.expense.entity.ExpenseStatus;
import com.spark.falcon.expense.exception.ExpenseAccessDeniedException;
import com.spark.falcon.expense.exception.ExpenseNotFoundException;
import com.spark.falcon.expense.exception.ExpenseStateException;
import com.spark.falcon.expense.exception.ExpenseValidationException;
import com.spark.falcon.expense.repository.ExpenseCategoryRepository;
import com.spark.falcon.expense.repository.ExpenseRepository;
import com.spark.falcon.expense.repository.ExpenseSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExpenseCategoryService {
    private static final Set<String> PROHIBITED = Set.of(
            "product-purchase", "supplier-payment", "supplier-due-payment", "customer-refund", "sales-return",
            "sell-delete", "sell-void", "transaction-void", "inventory-loss", "stock-adjustment", "branch-transfer",
            "cash-withdrawal", "recoverable-deposit-advance", "refundable-expense");

    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseSearchRepository searchRepository;
    private final BusinessAccessService businessAccessService;
    private final Clock clock;

    @Transactional
    public ExpenseCategoryResponse create(Long ownerId, ExpenseCategoryRequest request) {
        Long businessId = business(ownerId);
        String slug = slug(request.getSlug(), request.getName());
        validate(businessId, null, request, slug);
        ExpenseCategory saved = categoryRepository.save(ExpenseCategory.create(
                businessId, clean(request.getName()), slug, request.getParentCategoryId(),
                cleanNullable(request.getDescription()), request.isActive(), request.getDisplayOrder(), Instant.now(clock)));
        return response(saved, businessId);
    }

    @Transactional
    public ExpenseCategoryResponse update(Long ownerId, Long id, ExpenseCategoryRequest request) {
        Long businessId = business(ownerId);
        ExpenseCategory category = require(businessId, id);
        String slug = slug(request.getSlug(), request.getName());
        validate(businessId, id, request, slug);
        category.change(clean(request.getName()), slug, request.getParentCategoryId(), cleanNullable(request.getDescription()),
                request.isActive(), request.getDisplayOrder(), Instant.now(clock));
        return response(category, businessId);
    }

    @Transactional
    public ExpenseCategoryResponse activate(Long ownerId, Long id) {
        Long businessId = business(ownerId);
        ExpenseCategory category = require(businessId, id);
        try {
            category.activate(Instant.now(clock));
        } catch (IllegalStateException ex) {
            throw new ExpenseStateException(ex.getMessage());
        }
        return response(category, businessId);
    }

    @Transactional
    public ExpenseCategoryResponse deactivate(Long ownerId, Long id) {
        Long businessId = business(ownerId);
        ExpenseCategory category = require(businessId, id);
        category.deactivate(Instant.now(clock));
        return response(category, businessId);
    }

    @Transactional
    public void archive(Long ownerId, Long id) {
        Long businessId = business(ownerId);
        ExpenseCategory category = require(businessId, id);
        if (expenseRepository.existsByCategoryId(id)) {
            throw new ExpenseStateException("Used Expense Category cannot be deleted; deactivate it instead");
        }
        category.archive(Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> list(Long ownerId) {
        Long businessId = business(ownerId);
        return categoryRepository.findAllByBusinessIdAndArchivedFalseOrderByDisplayOrderAscNameAsc(businessId)
                .stream().map(c -> response(c, businessId)).toList();
    }

    @Transactional(readOnly = true)
    public Page<ExpenseCategoryResponse> search(Long ownerId, ExpenseCategoryFilter filter, Pageable pageable) {
        Long businessId = business(ownerId);
        return searchRepository.searchCategories(businessId, filter, pageable)
                .map(category -> response(category, businessId));
    }

    @Transactional(readOnly = true)
    public ExpenseCategoryResponse find(Long ownerId, Long id) {
        Long businessId = business(ownerId);
        return response(require(businessId, id), businessId);
    }

    private void validate(Long businessId, Long currentId, ExpenseCategoryRequest request, String slug) {
        if (isProhibited(slug)) {
            throw new ExpenseValidationException(
                    "This transaction belongs to another module and cannot be an Operating Expense category");
        }
        categoryRepository.findByBusinessIdAndSlug(businessId, slug)
                .filter(c -> !c.getId().equals(currentId))
                .ifPresent(c -> { throw new ExpenseValidationException("Expense Category slug is already used"); });

        if (request.getParentCategoryId() != null) {
            if (request.getParentCategoryId().equals(currentId)) {
                throw new ExpenseValidationException("Category cannot be its own parent");
            }
            ExpenseCategory parent = require(businessId, request.getParentCategoryId());
            Set<Long> visited = new HashSet<>();
            while (parent != null && parent.getParentCategoryId() != null) {
                if (!visited.add(parent.getId()) || parent.getParentCategoryId().equals(currentId)) {
                    throw new ExpenseValidationException("Expense Category hierarchy cannot contain a cycle");
                }
                parent = require(businessId, parent.getParentCategoryId());
            }
        }
    }

    private boolean isProhibited(String slug) {
        if (PROHIBITED.contains(slug)) return true;
        return slug.contains("purchase") || slug.contains("supplier-payment") || slug.contains("customer-refund")
                || slug.contains("sales-return") || slug.contains("sell-void") || slug.contains("inventory-loss")
                || slug.contains("stock-adjustment") || slug.contains("branch-transfer")
                || slug.contains("cash-withdrawal") || slug.contains("recoverable") || slug.contains("refundable");
    }

    private ExpenseCategoryResponse response(ExpenseCategory category, Long businessId) {
        String parent = category.getParentCategoryId() == null ? null
                : categoryRepository.findByIdAndBusinessId(category.getParentCategoryId(), businessId)
                .map(ExpenseCategory::getName).orElse(null);
        return new ExpenseCategoryResponse(
                category.getId(), category.getName(), category.getSlug(), category.getParentCategoryId(), parent,
                category.getDescription(), category.isActive(), category.getDisplayOrder(),
                expenseRepository.countByBusinessIdAndCategoryIdAndStatusIn(businessId, category.getId(), List.of(ExpenseStatus.POSTED, ExpenseStatus.REVERSED)),
                expenseRepository.existsByCategoryId(category.getId()), category.isArchived(), category.getCreatedAt());
    }

    private ExpenseCategory require(Long businessId, Long id) {
        return categoryRepository.findByIdAndBusinessId(id, businessId).orElseThrow(ExpenseNotFoundException::new);
    }

    private Long business(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId)
                .orElseThrow(ExpenseAccessDeniedException::new).businessId();
    }

    private String slug(String supplied, String name) {
        String value = supplied == null || supplied.isBlank() ? name : supplied;
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private String clean(String value) { return value.trim(); }
    private String cleanNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
