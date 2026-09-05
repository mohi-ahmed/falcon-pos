package com.spark.falcon.inventory.repository;

import com.spark.falcon.inventory.entity.*;
import com.spark.falcon.product.entity.Product;
import com.spark.falcon.product.entity.ProductVariant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dynamic, typed inventory list queries.
 *
 * <p>The previous JPQL used patterns such as {@code :fromDate is null or ...} and
 * {@code lower(concat('%', :queryText, '%'))}. With Hibernate 7 + PostgreSQL,
 * null parameters in those expressions can be sent without a usable SQL type,
 * which causes errors such as {@code lower(bytea)} and
 * {@code could not determine data type of parameter}. Criteria predicates are
 * added only when a filter is present, so no untyped null parameter reaches
 * PostgreSQL while filtering and pagination stay database-side.</p>
 */
@Repository
public class InventorySearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public Page<StockMovement> searchMovements(
            Long businessId, Long branchId, Long movementId, Long productId,
            Long variantId, Long batchId, StockMovementType movementType,
            StockSourceType sourceType, Long userId, Instant fromDate, Instant toDate,
            Boolean reversal, Long reversalReferenceId, Long queryMovementId,
            String queryText, Pageable pageable) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<StockMovement> data = cb.createQuery(StockMovement.class);
        Root<StockMovement> root = data.from(StockMovement.class);
        MovementJoins joins = movementJoins(root);
        data.where(movementPredicates(cb, root, joins, businessId, branchId, movementId,
                productId, variantId, batchId, movementType, sourceType, userId,
                fromDate, toDate, reversal, reversalReferenceId, queryMovementId, queryText));
        data.orderBy(orders(cb, root, pageable.getSort(), Set.of("id", "postedAt", "movementType", "baseQuantityChange", "quantityBefore", "quantityAfter", "financialUnitCostSnapshot", "inventoryValueChange", "sourceType", "postedByActorId"), "postedAt", "id"));

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<StockMovement> countRoot = count.from(StockMovement.class);
        MovementJoins countJoins = movementJoins(countRoot);
        count.select(cb.count(countRoot));
        count.where(movementPredicates(cb, countRoot, countJoins, businessId, branchId, movementId,
                productId, variantId, batchId, movementType, sourceType, userId,
                fromDate, toDate, reversal, reversalReferenceId, queryMovementId, queryText));

        return page(data, count, pageable);
    }

    public Page<PhysicalStockCount> searchCounts(
            Long businessId, Long branchId, Long countId, StockCountScope scope,
            InventoryOperationStatus status, Long createdBy, Long assignedCounterId,
            Instant fromTime, Instant toTime, Pageable pageable) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<PhysicalStockCount> data = cb.createQuery(PhysicalStockCount.class);
        Root<PhysicalStockCount> root = data.from(PhysicalStockCount.class);
        data.where(countPredicates(cb, root, businessId, branchId, countId, scope, status,
                createdBy, assignedCounterId, fromTime, toTime));
        data.orderBy(orders(cb, root, pageable.getSort(), Set.of("id", "countDate", "scope", "status", "createdBy", "assignedCounterId", "createdAt", "submittedAt", "approvedAt", "postedAt"), "createdAt", "id"));

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<PhysicalStockCount> countRoot = count.from(PhysicalStockCount.class);
        count.select(cb.count(countRoot));
        count.where(countPredicates(cb, countRoot, businessId, branchId, countId, scope, status,
                createdBy, assignedCounterId, fromTime, toTime));

        return page(data, count, pageable);
    }

    public Page<StockAdjustment> searchAdjustments(
            Long businessId, Long branchId, Long adjustmentId, Long variantId,
            Long batchId, AdjustmentType type, String reason,
            InventoryOperationStatus status, AdjustmentSourceType sourceType,
            Long createdBy, Long approvedBy, Instant fromDate, Instant toDate,
            Pageable pageable) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<StockAdjustment> data = cb.createQuery(StockAdjustment.class);
        Root<StockAdjustment> root = data.from(StockAdjustment.class);
        data.where(adjustmentPredicates(cb, root, businessId, branchId, adjustmentId,
                variantId, batchId, type, reason, status, sourceType, createdBy,
                approvedBy, fromDate, toDate));
        data.orderBy(orders(cb, root, pageable.getSort(), Set.of("id", "type", "baseQuantity", "quantityBefore", "quantityAfter", "inventoryValueChange", "reason", "status", "createdBy", "approvedBy", "createdAt", "postedAt"), "createdAt", "id"));

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<StockAdjustment> countRoot = count.from(StockAdjustment.class);
        count.select(cb.count(countRoot));
        count.where(adjustmentPredicates(cb, countRoot, businessId, branchId, adjustmentId,
                variantId, batchId, type, reason, status, sourceType, createdBy,
                approvedBy, fromDate, toDate));

        return page(data, count, pageable);
    }

    public Page<BranchTransfer> searchTransfers(
            Long businessId, Long contextBranchId, Long transferId,
            Long sourceBranchId, Long destinationBranchId, BranchTransferStatus status,
            Long createdBy, Long approvedBy, Long dispatchedBy, Long receivedBy,
            Instant fromDate, Instant toDate, Pageable pageable) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BranchTransfer> data = cb.createQuery(BranchTransfer.class);
        Root<BranchTransfer> root = data.from(BranchTransfer.class);
        data.where(transferPredicates(cb, root, businessId, contextBranchId, transferId,
                sourceBranchId, destinationBranchId, status, createdBy, approvedBy,
                dispatchedBy, receivedBy, fromDate, toDate));
        data.orderBy(orders(cb, root, pageable.getSort(), Set.of("id", "sourceBranchId", "destinationBranchId", "status", "createdBy", "approvedBy", "dispatchedBy", "receivedBy", "createdAt", "approvedAt", "dispatchedAt", "lastReceivedAt"), "createdAt", "id"));

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<BranchTransfer> countRoot = count.from(BranchTransfer.class);
        count.select(cb.count(countRoot));
        count.where(transferPredicates(cb, countRoot, businessId, contextBranchId, transferId,
                sourceBranchId, destinationBranchId, status, createdBy, approvedBy,
                dispatchedBy, receivedBy, fromDate, toDate));

        return page(data, count, pageable);
    }

    public Page<InventoryLoss> searchLosses(
            Long businessId, Long branchId, Long lossId, Long variantId,
            Long batchId, String reason, InventoryOperationStatus status,
            Long createdBy, Long approvedBy, Instant fromDate, Instant toDate,
            Pageable pageable) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<InventoryLoss> data = cb.createQuery(InventoryLoss.class);
        Root<InventoryLoss> root = data.from(InventoryLoss.class);
        data.where(lossPredicates(cb, root, businessId, branchId, lossId, variantId,
                batchId, reason, status, createdBy, approvedBy, fromDate, toDate));
        data.orderBy(orders(cb, root, pageable.getSort(), Set.of("id", "disposalDate", "baseQuantity", "weightedAverageCostSnapshot", "financialLoss", "reason", "status", "createdBy", "approvedBy", "createdAt", "postedAt"), "createdAt", "id"));

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<InventoryLoss> countRoot = count.from(InventoryLoss.class);
        count.select(cb.count(countRoot));
        count.where(lossPredicates(cb, countRoot, businessId, branchId, lossId, variantId,
                batchId, reason, status, createdBy, approvedBy, fromDate, toDate));

        return page(data, count, pageable);
    }

    private Predicate[] movementPredicates(
            CriteriaBuilder cb, Root<StockMovement> root, MovementJoins joins,
            Long businessId, Long branchId, Long movementId, Long productId,
            Long variantId, Long batchId, StockMovementType movementType,
            StockSourceType sourceType, Long userId, Instant fromDate, Instant toDate,
            Boolean reversal, Long reversalReferenceId, Long queryMovementId,
            String queryText) {

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("businessId"), businessId));
        predicates.add(cb.equal(root.get("branchId"), branchId));
        addEqual(cb, predicates, root, "id", movementId);
        if (productId != null) predicates.add(cb.equal(joins.variant().get("productId"), productId));
        addEqual(cb, predicates, root, "productVariantId", variantId);
        addEqual(cb, predicates, root, "productBatchId", batchId);
        addEqual(cb, predicates, root, "movementType", movementType);
        addEqual(cb, predicates, root, "sourceType", sourceType);
        addEqual(cb, predicates, root, "postedByActorId", userId);
        if (fromDate != null) predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("postedAt"), fromDate));
        if (toDate != null) predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("postedAt"), toDate));
        if (reversal != null) {
            predicates.add(reversal ? cb.isNotNull(root.get("reversalOfMovementId"))
                    : cb.isNull(root.get("reversalOfMovementId")));
        }
        addEqual(cb, predicates, root, "reversalOfMovementId", reversalReferenceId);

        String query = cleanLower(queryText);
        if (query != null) {
            String pattern = "%" + query + "%";
            List<Predicate> any = new ArrayList<>();
            if (queryMovementId != null) {
                any.add(cb.equal(root.get("id"), queryMovementId));
                any.add(cb.equal(root.get("reversalOfMovementId"), queryMovementId));
            }
            any.add(likeLower(cb, root.get("sourceReferenceId"), pattern));
            any.add(likeLower(cb, root.get("sourceLineReference"), pattern));
            any.add(likeLower(cb, root.get("postingKey"), pattern));
            any.add(likeLower(cb, joins.product().get("name"), pattern));
            any.add(likeLower(cb, joins.product().get("referenceCode"), pattern));
            any.add(likeLower(cb, joins.variant().get("variantName"), pattern));
            any.add(likeLower(cb, joins.variant().get("sku"), pattern));
            any.add(likeLower(cb, joins.batch().get("batchNumber"), pattern));
            predicates.add(cb.or(any.toArray(Predicate[]::new)));
        }
        return predicates.toArray(Predicate[]::new);
    }

    private MovementJoins movementJoins(Root<StockMovement> root) {
        Join<StockMovement, ProductVariant> variant = root.join("productVariantReference", JoinType.INNER);
        Join<ProductVariant, Product> product = variant.join("productReference", JoinType.INNER);
        Join<StockMovement, ProductBatch> batch = root.join("productBatchReference", JoinType.LEFT);
        return new MovementJoins(variant, product, batch);
    }

    private Predicate[] countPredicates(CriteriaBuilder cb, Root<PhysicalStockCount> root,
                                        Long businessId, Long branchId, Long countId,
                                        StockCountScope scope, InventoryOperationStatus status,
                                        Long createdBy, Long assignedCounterId,
                                        Instant fromTime, Instant toTime) {
        List<Predicate> predicates = base(cb, root, businessId, branchId);
        addEqual(cb, predicates, root, "id", countId);
        addEqual(cb, predicates, root, "scope", scope);
        addEqual(cb, predicates, root, "status", status);
        addEqual(cb, predicates, root, "createdBy", createdBy);
        addEqual(cb, predicates, root, "assignedCounterId", assignedCounterId);
        addRange(cb, predicates, root, "createdAt", fromTime, toTime);
        return predicates.toArray(Predicate[]::new);
    }

    private Predicate[] adjustmentPredicates(CriteriaBuilder cb, Root<StockAdjustment> root,
                                             Long businessId, Long branchId, Long adjustmentId,
                                             Long variantId, Long batchId, AdjustmentType type,
                                             String reason, InventoryOperationStatus status,
                                             AdjustmentSourceType sourceType, Long createdBy,
                                             Long approvedBy, Instant fromDate, Instant toDate) {
        List<Predicate> predicates = base(cb, root, businessId, branchId);
        addEqual(cb, predicates, root, "id", adjustmentId);
        addEqual(cb, predicates, root, "productVariantId", variantId);
        addEqual(cb, predicates, root, "productBatchId", batchId);
        addEqual(cb, predicates, root, "type", type);
        addContains(cb, predicates, root, "reason", reason);
        addEqual(cb, predicates, root, "status", status);
        addEqual(cb, predicates, root, "sourceType", sourceType);
        addEqual(cb, predicates, root, "createdBy", createdBy);
        addEqual(cb, predicates, root, "approvedBy", approvedBy);
        addRange(cb, predicates, root, "createdAt", fromDate, toDate);
        return predicates.toArray(Predicate[]::new);
    }

    private Predicate[] transferPredicates(CriteriaBuilder cb, Root<BranchTransfer> root,
                                           Long businessId, Long contextBranchId, Long transferId,
                                           Long sourceBranchId, Long destinationBranchId,
                                           BranchTransferStatus status, Long createdBy, Long approvedBy,
                                           Long dispatchedBy, Long receivedBy, Instant fromDate, Instant toDate) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("businessId"), businessId));
        predicates.add(cb.or(cb.equal(root.get("sourceBranchId"), contextBranchId),
                cb.equal(root.get("destinationBranchId"), contextBranchId)));
        addEqual(cb, predicates, root, "id", transferId);
        addEqual(cb, predicates, root, "sourceBranchId", sourceBranchId);
        addEqual(cb, predicates, root, "destinationBranchId", destinationBranchId);
        addEqual(cb, predicates, root, "status", status);
        addEqual(cb, predicates, root, "createdBy", createdBy);
        addEqual(cb, predicates, root, "approvedBy", approvedBy);
        addEqual(cb, predicates, root, "dispatchedBy", dispatchedBy);
        addEqual(cb, predicates, root, "receivedBy", receivedBy);
        addRange(cb, predicates, root, "createdAt", fromDate, toDate);
        return predicates.toArray(Predicate[]::new);
    }

    private Predicate[] lossPredicates(CriteriaBuilder cb, Root<InventoryLoss> root,
                                       Long businessId, Long branchId, Long lossId, Long variantId,
                                       Long batchId, String reason, InventoryOperationStatus status,
                                       Long createdBy, Long approvedBy, Instant fromDate, Instant toDate) {
        List<Predicate> predicates = base(cb, root, businessId, branchId);
        addEqual(cb, predicates, root, "id", lossId);
        addEqual(cb, predicates, root, "productVariantId", variantId);
        addEqual(cb, predicates, root, "productBatchId", batchId);
        addContains(cb, predicates, root, "reason", reason);
        addEqual(cb, predicates, root, "status", status);
        addEqual(cb, predicates, root, "createdBy", createdBy);
        addEqual(cb, predicates, root, "approvedBy", approvedBy);
        addRange(cb, predicates, root, "createdAt", fromDate, toDate);
        return predicates.toArray(Predicate[]::new);
    }

    private <T> List<Predicate> base(CriteriaBuilder cb, Root<T> root, Long businessId, Long branchId) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("businessId"), businessId));
        predicates.add(cb.equal(root.get("branchId"), branchId));
        return predicates;
    }

    private <T> void addEqual(CriteriaBuilder cb, List<Predicate> predicates, Root<T> root,
                              String field, Object value) {
        if (value != null) predicates.add(cb.equal(root.get(field), value));
    }

    private <T> void addRange(CriteriaBuilder cb, List<Predicate> predicates, Root<T> root,
                              String field, Instant from, Instant to) {
        if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get(field), from));
        if (to != null) predicates.add(cb.lessThanOrEqualTo(root.<Instant>get(field), to));
    }

    private <T> void addContains(CriteriaBuilder cb, List<Predicate> predicates, Root<T> root,
                                 String field, String value) {
        String cleaned = cleanLower(value);
        if (cleaned != null) predicates.add(likeLower(cb, root.get(field), "%" + cleaned + "%"));
    }

    private Predicate likeLower(CriteriaBuilder cb, Path<?> path, String pattern) {
        Expression<String> text = path.as(String.class);
        return cb.like(cb.lower(cb.coalesce(text, "")), pattern);
    }

    private String cleanLower(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
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

    private <T> Page<T> page(CriteriaQuery<T> data, CriteriaQuery<Long> count, Pageable pageable) {
        TypedQuery<T> query = entityManager.createQuery(data);
        query.setFirstResult(Math.toIntExact(pageable.getOffset()));
        query.setMaxResults(pageable.getPageSize());
        List<T> content = query.getResultList();
        long total = entityManager.createQuery(count).getSingleResult();
        return new PageImpl<>(content, pageable, total);
    }

    private record MovementJoins(Join<StockMovement, ProductVariant> variant,
                                 Join<ProductVariant, Product> product,
                                 Join<StockMovement, ProductBatch> batch) { }
}
