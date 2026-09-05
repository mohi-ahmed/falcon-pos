package com.spark.falcon.expense.repository;

import com.spark.falcon.expense.dto.ExpenseCategoryFilter;
import com.spark.falcon.expense.dto.ExpenseListFilter;
import com.spark.falcon.expense.entity.Expense;
import com.spark.falcon.expense.entity.ExpenseCategory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Repository
public class ExpenseSearchRepository {

    private static final Set<String> EXPENSE_SORT_FIELDS = Set.of(
            "id", "expenseDate", "classification", "title", "amount", "paymentMethodSnapshot",
            "status", "createdBy", "createdAt", "postedAt");
    private static final Set<String> CATEGORY_SORT_FIELDS = Set.of(
            "id", "name", "slug", "displayOrder", "active", "createdAt");

    @PersistenceContext
    private EntityManager entityManager;

    public Page<Expense> searchExpenses(Long businessId, Long branchId, ExpenseListFilter filter, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Expense> data = cb.createQuery(Expense.class);
        Root<Expense> root = data.from(Expense.class);
        data.where(expensePredicates(cb, root, businessId, branchId, filter));
        data.orderBy(orders(cb, root, pageable.getSort(), EXPENSE_SORT_FIELDS, "expenseDate", "id"));

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<Expense> countRoot = count.from(Expense.class);
        count.select(cb.count(countRoot));
        count.where(expensePredicates(cb, countRoot, businessId, branchId, filter));
        return page(data, count, pageable);
    }

    public Page<ExpenseCategory> searchCategories(Long businessId, ExpenseCategoryFilter filter, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ExpenseCategory> data = cb.createQuery(ExpenseCategory.class);
        Root<ExpenseCategory> root = data.from(ExpenseCategory.class);
        data.where(categoryPredicates(cb, data, root, businessId, filter));
        data.orderBy(orders(cb, root, pageable.getSort(), CATEGORY_SORT_FIELDS, "displayOrder", "name"));

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<ExpenseCategory> countRoot = count.from(ExpenseCategory.class);
        count.select(cb.count(countRoot));
        count.where(categoryPredicates(cb, count, countRoot, businessId, filter));
        return page(data, count, pageable);
    }

    private Predicate[] expensePredicates(CriteriaBuilder cb, Root<Expense> root, Long businessId, Long branchId,
                                          ExpenseListFilter filter) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("businessId"), businessId));
        predicates.add(cb.equal(root.get("branchId"), branchId));
        if (filter == null) return predicates.toArray(Predicate[]::new);
        equal(cb, predicates, root, "id", filter.expenseId());
        equal(cb, predicates, root, "classification", filter.classification());
        equal(cb, predicates, root, "categoryId", filter.categoryId());
        equal(cb, predicates, root, "paymentMethodId", filter.paymentMethodId());
        equal(cb, predicates, root, "status", filter.status());
        equal(cb, predicates, root, "createdBy", filter.createdBy());
        if (filter.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), filter.from()));
        if (filter.to() != null) predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), filter.to()));

        String query = clean(filter.query());
        if (query != null) {
            String pattern = "%" + query + "%";
            List<Predicate> any = new ArrayList<>();
            Long numeric = positiveLong(query);
            if (numeric != null) any.add(cb.equal(root.get("id"), numeric));
            any.add(like(cb, root.get("title"), pattern));
            any.add(like(cb, root.get("payee"), pattern));
            any.add(like(cb, root.get("paymentReference"), pattern));
            any.add(like(cb, root.get("paidFromReference"), pattern));
            any.add(like(cb, root.get("categoryNameSnapshot"), pattern));
            predicates.add(cb.or(any.toArray(Predicate[]::new)));
        }
        return predicates.toArray(Predicate[]::new);
    }

    private Predicate[] categoryPredicates(CriteriaBuilder cb, CriteriaQuery<?> ownerQuery, Root<ExpenseCategory> root,
                                           Long businessId, ExpenseCategoryFilter filter) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("businessId"), businessId));
        predicates.add(cb.isFalse(root.get("archived")));
        if (filter == null) return predicates.toArray(Predicate[]::new);
        if (Boolean.TRUE.equals(filter.topLevelOnly())) predicates.add(cb.isNull(root.get("parentCategoryId")));
        else equal(cb, predicates, root, "parentCategoryId", filter.parentCategoryId());
        equal(cb, predicates, root, "active", filter.active());
        if (filter.createdDate() != null) {
            Instant start = filter.createdDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant end = filter.createdDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            predicates.add(cb.lessThan(root.get("createdAt"), end));
        }
        if (filter.used() != null) {
            Subquery<Long> usage = ownerQuery.subquery(Long.class);
            Root<Expense> expense = usage.from(Expense.class);
            usage.select(cb.literal(1L));
            usage.where(cb.equal(expense.get("businessId"), businessId), cb.equal(expense.get("categoryId"), root.get("id")));
            predicates.add(filter.used() ? cb.exists(usage) : cb.not(cb.exists(usage)));
        }
        String query = clean(filter.query());
        if (query != null) {
            String pattern = "%" + query + "%";
            List<Predicate> any = new ArrayList<>();
            Long numeric = positiveLong(query);
            if (numeric != null) any.add(cb.equal(root.get("id"), numeric));
            any.add(like(cb, root.get("name"), pattern));
            any.add(like(cb, root.get("slug"), pattern));
            any.add(like(cb, root.get("description"), pattern));

            Subquery<Long> parentMatch = ownerQuery.subquery(Long.class);
            Root<ExpenseCategory> parent = parentMatch.from(ExpenseCategory.class);
            parentMatch.select(parent.get("id"));
            parentMatch.where(cb.equal(parent.get("businessId"), businessId), like(cb, parent.get("name"), pattern));
            any.add(root.get("parentCategoryId").in(parentMatch));
            predicates.add(cb.or(any.toArray(Predicate[]::new)));
        }
        return predicates.toArray(Predicate[]::new);
    }

    private <T> List<Order> orders(CriteriaBuilder cb, Root<T> root, Sort sort, Set<String> allowed,
                                   String defaultPrimary, String defaultSecondary) {
        List<Order> orders = new ArrayList<>();
        if (sort != null) {
            for (Sort.Order sortOrder : sort) {
                if (!allowed.contains(sortOrder.getProperty())) continue;
                orders.add(sortOrder.isAscending()
                        ? cb.asc(root.get(sortOrder.getProperty()))
                        : cb.desc(root.get(sortOrder.getProperty())));
            }
        }
        if (orders.isEmpty()) {
            orders.add(cb.desc(root.get(defaultPrimary)));
            orders.add(cb.desc(root.get(defaultSecondary)));
        }
        return orders;
    }

    private <T> void equal(CriteriaBuilder cb, List<Predicate> predicates, Root<T> root, String field, Object value) {
        if (value != null) predicates.add(cb.equal(root.get(field), value));
    }

    private Predicate like(CriteriaBuilder cb, Expression<?> value, String pattern) {
        return cb.like(cb.lower(cb.coalesce(value.as(String.class), "")), pattern);
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Long positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value.replaceFirst("^(exp|cat)-", ""));
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private <T> Page<T> page(CriteriaQuery<T> data, CriteriaQuery<Long> count, Pageable pageable) {
        TypedQuery<T> query = entityManager.createQuery(data);
        query.setFirstResult(Math.toIntExact(pageable.getOffset()));
        query.setMaxResults(pageable.getPageSize());
        return new PageImpl<>(query.getResultList(), pageable, entityManager.createQuery(count).getSingleResult());
    }
}
