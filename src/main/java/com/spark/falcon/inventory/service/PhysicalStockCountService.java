package com.spark.falcon.inventory.service;

import com.spark.falcon.inventory.dto.CreateStockCountCommand;
import com.spark.falcon.inventory.dto.PhysicalStockCountFilter;
import com.spark.falcon.inventory.dto.RecordStockCountRowCommand;
import com.spark.falcon.inventory.dto.UpdateStockCountCommand;
import com.spark.falcon.inventory.entity.*;
import com.spark.falcon.inventory.exception.InventoryOperationNotFoundException;
import com.spark.falcon.inventory.exception.InventoryOperationStateException;
import com.spark.falcon.inventory.exception.InventoryPostingException;
import com.spark.falcon.inventory.exception.InventoryStockNotFoundException;
import com.spark.falcon.inventory.repository.*;
import com.spark.falcon.product.dto.response.ProductVariantAccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PhysicalStockCountService {

    private final PhysicalStockCountRepository repository;
    private final InventorySearchRepository searchRepository;
    private final BranchProductStockRepository stockRepository;
    private final ProductBatchRepository batchRepository;
    private final InventoryOperationSupport support;
    private final StockAdjustmentService adjustmentService;
    private final InventoryAuditEventRepository auditRepository;
    private final Clock clock;

    @Transactional
    public PhysicalStockCount create(CreateStockCountCommand command) {
        Long businessId = support.business(command.ownerId());
        support.branch(businessId, command.branchId());

        String idempotencyKey = key(command.idempotencyKey());
        PhysicalStockCount repeated = repository.findByBusinessIdAndIdempotencyKey(businessId, idempotencyKey)
                .orElse(null);
        if (repeated != null) return repeated;

        validateHeader(command.countDate(), command.scope(), command.assignedCounterId());

        PhysicalStockCount count = PhysicalStockCount.create(
                businessId,
                command.branchId(),
                command.countDate(),
                command.scope(),
                command.assignedCounterId(),
                clean(command.notes()),
                clean(command.attachmentReference()),
                command.ownerId(),
                idempotencyKey,
                Instant.now(clock));

        populateRows(count, command.ownerId(), command.scope(), command.selections());
        count = repository.saveAndFlush(count);
        audit(count, "DRAFT_CREATED", command.ownerId(),
                "System quantity snapshot rows prepared for scope " + command.scope());
        return count;
    }

    @Transactional
    public PhysicalStockCount updateDraft(UpdateStockCountCommand command) {
        Long businessId = support.business(command.ownerId());
        PhysicalStockCount count = require(businessId, command.countId());
        if (count.getStatus() != InventoryOperationStatus.DRAFT) {
            throw new InventoryOperationStateException("Only Draft Count may be updated");
        }
        validateHeader(command.countDate(), command.scope(), command.assignedCounterId());

        count.updateDraft(
                command.countDate(),
                command.scope(),
                command.assignedCounterId(),
                clean(command.notes()),
                clean(command.attachmentReference()));
        count.clearDraftRows();
        populateRows(count, command.ownerId(), command.scope(), command.selections());
        audit(count, "DRAFT_UPDATED", command.ownerId(), "Draft Count scope and rows updated");
        return count;
    }

    @Transactional
    public PhysicalStockCount record(RecordStockCountRowCommand command) {
        PhysicalStockCount count = require(support.business(command.ownerId()), command.countId());
        if (count.getStatus() != InventoryOperationStatus.DRAFT
                && count.getStatus() != InventoryOperationStatus.COUNTING) {
            throw new InventoryOperationStateException("Only Draft or Counting Count can be edited");
        }

        PhysicalStockCountRow row = count.getRows().stream()
                .filter(value -> value.getId().equals(command.rowId()))
                .findFirst()
                .orElseThrow(InventoryOperationNotFoundException::new);

        InventoryOperationSupport.Resolved resolved = support.resolveNonNegative(
                count.getBusinessId(),
                count.getBranchId(),
                row.getProductVariantId(),
                command.countedUnitId(),
                command.countedQuantity(),
                count.getCountDate());

        if (command.conversionFactor() != null
                && resolved.factor().compareTo(command.conversionFactor()) != 0) {
            throw new InventoryPostingException("Resolved Conversion Factor does not match request");
        }

        row.record(
                command.countedQuantity(),
                command.countedUnitId(),
                resolved.factor(),
                clean(command.varianceReason()));

        audit(count, "ROW_COUNTED", command.ownerId(),
                "Row=" + row.getId()
                        + ", entered=" + row.getCountedQuantity()
                        + ", base=" + row.getConvertedBaseQuantity()
                        + ", variance=" + row.getVariance());
        return count;
    }

    @Transactional
    public PhysicalStockCount start(Long ownerId, Long countId) {
        PhysicalStockCount count = require(support.business(ownerId), countId);
        if (count.getStatus() != InventoryOperationStatus.DRAFT) {
            throw new InventoryOperationStateException("Only Draft Count may start");
        }

        refreshSystemSnapshots(count);
        try {
            count.start();
        } catch (IllegalStateException ex) {
            throw new InventoryOperationStateException(ex.getMessage());
        }
        audit(count, "COUNTING", ownerId,
                "System quantity snapshots refreshed and preserved when counting started");
        return count;
    }

    @Transactional
    public PhysicalStockCount submit(Long ownerId, Long countId) {
        PhysicalStockCount count = require(support.business(ownerId), countId);
        if (count.getRows().stream().anyMatch(row -> row.getCountedQuantity() == null)) {
            throw new InventoryOperationStateException("Every Count row must be counted before submission");
        }
        try {
            count.submit(Instant.now(clock));
        } catch (IllegalStateException ex) {
            throw new InventoryOperationStateException(ex.getMessage());
        }
        audit(count, "SUBMITTED", ownerId, "Physical Count submitted for review");
        return count;
    }

    @Transactional
    public PhysicalStockCount review(Long ownerId, Long countId) {
        return transition(ownerId, countId, "UNDER_REVIEW", "Physical Count moved under review",
                PhysicalStockCount::review);
    }

    @Transactional
    public PhysicalStockCount requestRecount(Long ownerId, Long countId) {
        return transition(ownerId, countId, "RECOUNT_REQUESTED", "Recount requested",
                PhysicalStockCount::requestRecount);
    }

    @Transactional
    public PhysicalStockCount approve(Long ownerId, Long countId) {
        PhysicalStockCount count = require(support.business(ownerId), countId);
        try {
            count.approve(ownerId, Instant.now(clock));
        } catch (IllegalStateException ex) {
            throw new InventoryOperationStateException(ex.getMessage());
        }
        audit(count, "APPROVED", ownerId, "Physical Count approved; stock remains unchanged until variance posting");
        return count;
    }

    @Transactional
    public PhysicalStockCount reject(Long ownerId, Long countId, String reason) {
        PhysicalStockCount count = require(support.business(ownerId), countId);
        try {
            count.reject();
        } catch (IllegalStateException ex) {
            throw new InventoryOperationStateException(ex.getMessage());
        }
        audit(count, "REJECTED", ownerId, requiredReason(reason, "Rejection reason is required"));
        return count;
    }

    @Transactional
    public PhysicalStockCount reject(Long ownerId, Long countId) {
        return reject(ownerId, countId, "Rejected during Physical Count review");
    }

    @Transactional
    public PhysicalStockCount cancel(Long ownerId, Long countId, String reason) {
        PhysicalStockCount count = require(support.business(ownerId), countId);
        try {
            count.cancel();
        } catch (IllegalStateException ex) {
            throw new InventoryOperationStateException(ex.getMessage());
        }
        audit(count, "CANCELLED", ownerId, requiredReason(reason, "Cancellation reason is required"));
        return count;
    }

    @Transactional
    public PhysicalStockCount cancel(Long ownerId, Long countId) {
        return cancel(ownerId, countId, "Physical Count cancelled before posting");
    }

    @Transactional
    public PhysicalStockCount post(Long ownerId, Long countId) {
        PhysicalStockCount count = require(support.business(ownerId), countId);
        if (count.getStatus() != InventoryOperationStatus.APPROVED) {
            throw new InventoryOperationStateException("Only Approved Count may post variance");
        }

        for (PhysicalStockCountRow row : count.getRows()) {
            if (row.getVariance() != null && row.getVariance().signum() != 0) {
                StockAdjustment adjustment = adjustmentService.createFromCountAndPost(ownerId, count.getId(), row);
                row.linkAdjustment(adjustment.getId());
            }
        }

        count.posted(Instant.now(clock));
        audit(count, "POSTED", ownerId,
                "Approved non-zero variances posted atomically; zero variances remain history-only");
        return count;
    }

    @Transactional
    public void deleteDraft(Long ownerId, Long countId) {
        PhysicalStockCount count = require(support.business(ownerId), countId);
        if (count.getStatus() != InventoryOperationStatus.DRAFT) {
            throw new InventoryOperationStateException("Only Draft Count may be deleted");
        }
        repository.delete(count);
    }

    @Transactional(readOnly = true)
    public List<PhysicalStockCount> list(Long ownerId, Long branchId) {
        Long businessId = support.business(ownerId);
        support.branch(businessId, branchId);
        return repository.findAllByBusinessIdAndBranchIdOrderByCreatedAtDesc(businessId, branchId);
    }

    @Transactional(readOnly = true)
    public Page<PhysicalStockCount> search(Long ownerId, Long branchId,
                                           PhysicalStockCountFilter filter, Pageable pageable) {
        Long businessId = support.business(ownerId);
        support.branch(businessId, branchId);
        PhysicalStockCountFilter safe = filter == null
                ? new PhysicalStockCountFilter(null, null, null, null, null, null, null)
                : filter;
        Page<PhysicalStockCount> page = searchRepository.searchCounts(
                businessId,
                branchId,
                safe.countId(),
                safe.scope(),
                safe.status(),
                safe.createdBy(),
                safe.assignedCounterId(),
                safe.from(),
                safe.to(),
                pageable);
        // The Thymeleaf list renders row counts after this transaction closes. Initialize the
        // collection here so the view does not depend on Open-Session-In-View.
        page.getContent().forEach(count -> count.getRows().size());
        return page;
    }

    @Transactional(readOnly = true)
    public PhysicalStockCount find(Long ownerId, Long countId) {
        return require(support.business(ownerId), countId);
    }

    private void populateRows(PhysicalStockCount count, Long ownerId, StockCountScope scope,
                              List<CreateStockCountCommand.StockCountSelection> requestedSelections) {
        List<CreateStockCountCommand.StockCountSelection> selections;
        if (scope == StockCountScope.BRANCH) {
            selections = stockRepository.findByBusinessIdAndBranchIdOrderByProductVariantIdAsc(
                            count.getBusinessId(), count.getBranchId()).stream()
                    .map(stock -> new CreateStockCountCommand.StockCountSelection(stock.getProductVariantId(), null))
                    .toList();
        } else {
            if (requestedSelections == null || requestedSelections.isEmpty()) {
                throw new InventoryPostingException("Selected Count Scope requires at least one Product Variant or Batch");
            }
            selections = requestedSelections;
        }

        if (scope == StockCountScope.SELECTED_BATCHES
                && selections.stream().anyMatch(selection -> selection.productBatchId() == null)) {
            throw new InventoryPostingException("Selected Batches scope requires an exact Batch for every row");
        }

        Set<String> unique = new HashSet<>();
        for (CreateStockCountCommand.StockCountSelection selection : selections) {
            if (selection == null || selection.productVariantId() == null) {
                throw new InventoryPostingException("Count Product Variant is required");
            }
            ProductVariantAccessResponse variant = support.variant(
                    count.getBusinessId(), count.getBranchId(), selection.productVariantId());

            if (variant.trackExpiry() || variant.batchTrackingRequired()) {
                List<ProductBatch> batches = selection.productBatchId() == null
                        ? batchRepository.findByBusinessIdAndBranchIdAndProductVariantIdOrderByExpiryDateAscIdAsc(
                        count.getBusinessId(), count.getBranchId(), selection.productVariantId())
                        : List.of(batchRepository.findByIdAndBusinessIdAndBranchId(
                                        selection.productBatchId(), count.getBusinessId(), count.getBranchId())
                                .filter(batch -> batch.getProductVariantId().equals(selection.productVariantId()))
                                .orElseThrow(() -> new InventoryPostingException("Count Batch was not found")));

                for (ProductBatch batch : batches) {
                    addRow(count, unique, variant, batch.getId(), batch.getAvailableBaseQuantity());
                }
            } else {
                if (selection.productBatchId() != null) {
                    throw new InventoryPostingException("Non-batch Product Variant cannot use a Batch Count row");
                }
                BranchProductStock stock = stockRepository
                        .findByBusinessIdAndBranchIdAndProductVariantId(
                                count.getBusinessId(), count.getBranchId(), selection.productVariantId())
                        .orElseThrow(InventoryStockNotFoundException::new);
                addRow(count, unique, variant, null, stock.getBaseQuantity());
            }
        }

        if (count.getRows().isEmpty()) {
            throw new InventoryPostingException("Stock Count scope contains no stock rows");
        }
    }

    private void refreshSystemSnapshots(PhysicalStockCount count) {
        for (PhysicalStockCountRow row : count.getRows()) {
            BigDecimal current;
            if (row.getProductBatchId() != null) {
                current = batchRepository.findByIdAndBusinessIdAndBranchId(
                                row.getProductBatchId(), count.getBusinessId(), count.getBranchId())
                        .filter(batch -> batch.getProductVariantId().equals(row.getProductVariantId()))
                        .map(ProductBatch::getAvailableBaseQuantity)
                        .orElseThrow(() -> new InventoryPostingException("Count Batch is no longer available"));
            } else {
                current = stockRepository.findByBusinessIdAndBranchIdAndProductVariantId(
                                count.getBusinessId(), count.getBranchId(), row.getProductVariantId())
                        .map(BranchProductStock::getBaseQuantity)
                        .orElseThrow(InventoryStockNotFoundException::new);
            }
            row.refreshSystemQuantitySnapshot(current);
        }
    }

    private void addRow(PhysicalStockCount count, Set<String> unique,
                        ProductVariantAccessResponse variant, Long batchId, BigDecimal quantity) {
        String uniqueKey = variant.variantId() + ":" + batchId;
        if (!unique.add(uniqueKey)) {
            throw new InventoryPostingException("Duplicate Product Variant or Batch in Count");
        }
        count.addRow(PhysicalStockCountRow.snapshot(
                variant.variantId(), batchId, variant.baseInventoryUnitId(), quantity));
    }

    private PhysicalStockCount transition(Long ownerId, Long countId, String action, String details,
                                          java.util.function.Consumer<PhysicalStockCount> transition) {
        PhysicalStockCount count = require(support.business(ownerId), countId);
        try {
            transition.accept(count);
        } catch (IllegalStateException ex) {
            throw new InventoryOperationStateException(ex.getMessage());
        }
        audit(count, action, ownerId, details);
        return count;
    }

    private PhysicalStockCount require(Long businessId, Long countId) {
        return repository.findByIdAndBusinessId(countId, businessId)
                .orElseThrow(InventoryOperationNotFoundException::new);
    }

    private void audit(PhysicalStockCount count, String action, Long actorId, String details) {
        auditRepository.save(InventoryAuditEvent.record(
                count.getBusinessId(), count.getBranchId(), "COUNT", count.getId(),
                action, actorId, details, Instant.now(clock)));
    }

    private void validateHeader(java.time.LocalDate countDate, StockCountScope scope, Long assignedCounterId) {
        if (countDate == null || scope == null || assignedCounterId == null) {
            throw new InventoryPostingException("Count Date, Scope and Assigned Counter are required");
        }
    }

    private String key(String value) {
        if (value == null || value.isBlank()) throw new InventoryPostingException("Idempotency key is required");
        return value.trim();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requiredReason(String value, String message) {
        if (value == null || value.isBlank()) throw new InventoryPostingException(message);
        return value.trim();
    }
}
