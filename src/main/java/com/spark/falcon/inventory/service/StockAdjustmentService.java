package com.spark.falcon.inventory.service;

import com.spark.falcon.inventory.dto.*;
import com.spark.falcon.inventory.entity.*;
import com.spark.falcon.inventory.exception.*;
import com.spark.falcon.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockAdjustmentService {
    private final StockAdjustmentRepository repository;
    private final InventorySearchRepository searchRepository;
    private final BranchProductStockRepository stockRepository;
    private final ProductBatchRepository batchRepository;
    private final InventoryPostingService postingService;
    private final InventoryOperationSupport support;
    private final InventoryAuditEventRepository auditRepository;
    private final Clock clock;

    @Transactional
    public StockAdjustment create(CreateStockAdjustmentCommand command) {
        Long businessId = support.business(command.ownerId());
        support.branch(businessId, command.branchId());
        String idempotencyKey = requiredKey(command.idempotencyKey());
        StockAdjustment repeated = repository.findByBusinessIdAndIdempotencyKey(businessId, idempotencyKey).orElse(null);
        if (repeated != null) return repeated;

        DraftState state = validateDraft(businessId, command.branchId(), command.productVariantId(), command.productBatchId(),
                command.type(), command.enteredUnitId(), command.enteredQuantity(), LocalDate.now(clock));
        Instant now = Instant.now(clock);
        StockAdjustment adjustment = StockAdjustment.draft(
                businessId, command.branchId(), command.productVariantId(), command.productBatchId(), command.type(),
                command.enteredQuantity(), command.enteredUnitId(), state.resolved().factor(), state.resolved().baseQuantity(),
                state.stock().getBaseQuantity(), state.stock().getWeightedAverageCost(), requiredReason(command.reason()),
                optional(command.notes()), optional(command.attachmentReference()),
                command.sourceType() == null ? AdjustmentSourceType.MANUAL : command.sourceType(), command.sourceCountId(),
                command.ownerId(), idempotencyKey, now);
        adjustment = repository.saveAndFlush(adjustment);
        audit(adjustment, "DRAFT_CREATED", command.ownerId(), adjustment.getReason());
        return adjustment;
    }

    @Transactional
    public StockAdjustment updateDraft(UpdateStockAdjustmentCommand command) {
        Long businessId = support.business(command.ownerId());
        StockAdjustment adjustment = require(businessId, command.adjustmentId());
        if (adjustment.getStatus() != InventoryOperationStatus.DRAFT) throw new InventoryOperationStateException("Only Draft Adjustment may be updated");
        DraftState state = validateDraft(businessId, adjustment.getBranchId(), command.productVariantId(), command.productBatchId(),
                command.type(), command.enteredUnitId(), command.enteredQuantity(), LocalDate.now(clock));
        adjustment.updateDraft(command.productVariantId(), command.productBatchId(), command.type(), command.enteredQuantity(),
                command.enteredUnitId(), state.resolved().factor(), state.resolved().baseQuantity(), state.stock().getBaseQuantity(),
                state.stock().getWeightedAverageCost(), requiredReason(command.reason()), optional(command.notes()),
                optional(command.attachmentReference()), command.sourceType() == null ? AdjustmentSourceType.MANUAL : command.sourceType(),
                command.sourceCountId());
        audit(adjustment, "DRAFT_UPDATED", command.ownerId(), adjustment.getReason());
        return adjustment;
    }

    @Transactional public StockAdjustment submit(Long ownerId, Long id) { return transition(ownerId, id, "SUBMITTED", "Adjustment submitted for approval", StockAdjustment::submit); }
    @Transactional public StockAdjustment approve(Long ownerId, Long id) { return transition(ownerId, id, "APPROVED", "Adjustment approved for posting", a -> a.approve(ownerId)); }
    @Transactional public StockAdjustment reject(Long ownerId, Long id) { return reject(ownerId, id, "Adjustment rejected"); }
    @Transactional public StockAdjustment reject(Long ownerId, Long id, String reason) { return transition(ownerId, id, "REJECTED", requiredReason(reason), StockAdjustment::reject); }
    @Transactional public StockAdjustment cancel(Long ownerId, Long id) { return cancel(ownerId, id, "Adjustment cancelled"); }
    @Transactional public StockAdjustment cancel(Long ownerId, Long id, String reason) { return transition(ownerId, id, "CANCELLED", requiredReason(reason), StockAdjustment::cancel); }

    @Transactional
    public void deleteDraft(Long ownerId, Long id) {
        StockAdjustment adjustment = require(support.business(ownerId), id);
        if (adjustment.getStatus() != InventoryOperationStatus.DRAFT) throw new InventoryOperationStateException("Only Draft Adjustment may be deleted");
        audit(adjustment, "DRAFT_DELETED", ownerId, "Draft adjustment deleted");
        repository.delete(adjustment);
    }

    @Transactional
    public StockAdjustment post(Long ownerId, Long id) {
        Long businessId = support.business(ownerId);
        StockAdjustment adjustment = require(businessId, id);
        if (adjustment.getStatus() != InventoryOperationStatus.APPROVED) throw new InventoryOperationStateException("Only Approved Adjustment may be posted");

        BranchProductStock stock = stockRepository.findForUpdate(businessId, adjustment.getBranchId(), adjustment.getProductVariantId())
                .orElseThrow(InventoryStockNotFoundException::new);
        ProductBatch batch = lockBatchIfPresent(businessId, adjustment.getBranchId(), adjustment.getProductBatchId(), adjustment.getProductVariantId());
        validatePostingAvailability(adjustment, stock, batch);
        adjustment.refreshPostingSnapshot(stock.getBaseQuantity(), stock.getWeightedAverageCost());

        InventoryPostingResponse posting = postingService.postOperationalChange(new OperationalStockChangeRequest(
                businessId, adjustment.getBranchId(), adjustment.getProductVariantId(), adjustment.getProductBatchId(),
                adjustment.getEnteredQuantity(), adjustment.getEnteredUnitId(), adjustment.getConversionFactor(), adjustment.getBaseQuantity(),
                adjustment.getType() == AdjustmentType.INCREASE, adjustment.getUnitCostSnapshot(),
                adjustment.getType() == AdjustmentType.INCREASE ? StockMovementType.STOCK_ADJUSTMENT_INCREASE : StockMovementType.STOCK_ADJUSTMENT_DECREASE,
                StockSourceType.STOCK_ADJUSTMENT, String.valueOf(adjustment.getId()), null,
                "adjustment-post-" + adjustment.getId(), null, ownerId, adjustment.getReason(), adjustment.getNotes()));
        adjustment.posted(ownerId, posting.getMovement().getId(), Instant.now(clock));
        audit(adjustment, "POSTED", ownerId, "Movement " + posting.getMovement().getId());
        return adjustment;
    }

    @Transactional
    public StockAdjustment reverse(Long ownerId, Long id, String reason, String idempotencyKey) {
        Long businessId = support.business(ownerId);
        StockAdjustment adjustment = require(businessId, id);
        if (adjustment.getStatus() == InventoryOperationStatus.REVERSED) return adjustment;
        if (adjustment.getStatus() != InventoryOperationStatus.POSTED) throw new InventoryOperationStateException("Only Posted Adjustment may be reversed");
        StockMovementReversalRequest request = new StockMovementReversalRequest();
        request.setBusinessId(businessId); request.setBranchId(adjustment.getBranchId());
        request.setOriginalMovementId(adjustment.getStockMovementId()); request.setSourceReferenceId(String.valueOf(adjustment.getId()));
        request.setPostingKey(requiredKey(idempotencyKey)); request.setActorType(InventoryActorType.OWNER); request.setActorId(ownerId);
        request.setReason(requiredReason(reason));
        Long movementId = postingService.reverseStockMovement(request).getMovement().getId();
        adjustment.reversed(movementId);
        audit(adjustment, "REVERSED", ownerId, reason);
        return adjustment;
    }

    @Transactional
    public StockAdjustment createFromCountAndPost(Long ownerId, Long countId, PhysicalStockCountRow row) {
        AdjustmentType type = row.getVariance().signum() > 0 ? AdjustmentType.INCREASE : AdjustmentType.DECREASE;
        CreateStockAdjustmentCommand command = new CreateStockAdjustmentCommand(ownerId, row.getCount().getBranchId(), row.getProductVariantId(),
                row.getProductBatchId(), type, row.getVariance().abs(), row.getBaseInventoryUnitId(), BigDecimal.ONE,
                row.getVarianceReason() == null ? "Physical stock count variance" : row.getVarianceReason(), null, null,
                AdjustmentSourceType.PHYSICAL_COUNT, countId, "count-" + countId + "-row-" + row.getId());
        StockAdjustment adjustment = create(command);
        if (adjustment.getStatus() == InventoryOperationStatus.DRAFT) adjustment.submit();
        if (adjustment.getStatus() == InventoryOperationStatus.SUBMITTED) adjustment.approve(ownerId);
        repository.flush();
        return adjustment.getStatus() == InventoryOperationStatus.POSTED ? adjustment : post(ownerId, adjustment.getId());
    }

    @Transactional(readOnly = true)
    public List<StockAdjustment> list(Long ownerId, Long branchId) {
        Long businessId = support.business(ownerId); support.branch(businessId, branchId);
        return repository.findAllByBusinessIdAndBranchIdOrderByCreatedAtDesc(businessId, branchId);
    }

    @Transactional(readOnly = true)
    public Page<StockAdjustment> search(Long ownerId, Long branchId, StockAdjustmentFilter filter, Pageable pageable) {
        Long businessId = support.business(ownerId); support.branch(businessId, branchId);
        StockAdjustmentFilter f = filter == null ? new StockAdjustmentFilter(null,null,null,null,null,null,null,null,null,null,null) : filter;
        return searchRepository.searchAdjustments(businessId, branchId, f.adjustmentId(), f.productVariantId(), f.batchId(), f.type(), optional(f.reason()),
                f.status(), f.sourceType(), f.createdBy(), f.approvedBy(), f.from(), f.to(), pageable);
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> totalAbsoluteVarianceValues(Long ownerId, Long branchId, Collection<Long> countIds) {
        Long businessId = support.business(ownerId);
        support.branch(businessId, branchId);
        if (countIds == null || countIds.isEmpty()) return Map.of();

        List<Long> ids = countIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return Map.of();

        return repository.sumAbsoluteVarianceValueBySourceCountIds(
                        businessId,
                        branchId,
                        AdjustmentSourceType.PHYSICAL_COUNT,
                        List.of(InventoryOperationStatus.POSTED, InventoryOperationStatus.REVERSED),
                        ids).stream()
                .collect(Collectors.toUnmodifiableMap(
                        StockCountVarianceValueResponse::countId,
                        value -> value.totalAbsoluteVarianceValue() == null
                                ? BigDecimal.ZERO
                                : value.totalAbsoluteVarianceValue()));
    }

    @Transactional(readOnly = true) public StockAdjustment find(Long ownerId, Long id) { return require(support.business(ownerId), id); }

    private DraftState validateDraft(Long businessId, Long branchId, Long variantId, Long batchId, AdjustmentType type,
                                     Long unitId, BigDecimal quantity, LocalDate date) {
        if (type == null) throw new InventoryPostingException("Adjustment Type is required");
        InventoryOperationSupport.Resolved resolved = support.resolve(businessId, branchId, variantId, unitId, quantity, date);
        BranchProductStock stock = stockRepository.findByBusinessIdAndBranchIdAndProductVariantId(businessId, branchId, variantId)
                .orElseThrow(InventoryStockNotFoundException::new);
        ProductBatch batch = null;
        if (resolved.variant().trackExpiry() || resolved.variant().batchTrackingRequired()) {
            if (batchId == null) throw new InventoryPostingException("Exact batch is required");
            batch = requireBatch(businessId, branchId, batchId, variantId);
        } else if (batchId != null) {
            throw new InventoryPostingException("Batch must not be selected for a non batch-controlled Product Variant");
        }
        if (type == AdjustmentType.DECREASE) {
            if (resolved.baseQuantity().compareTo(stock.availableToReserve()) > 0) throw new InventoryPostingException("Adjustment exceeds unreserved stock");
            if (batch != null && resolved.baseQuantity().compareTo(batch.availableToReserve()) > 0) throw new InventoryPostingException("Adjustment exceeds unreserved batch stock");
        }
        validateBatchForAdjustment(type, batch, date);
        return new DraftState(resolved, stock, batch);
    }

    private void validatePostingAvailability(StockAdjustment a, BranchProductStock stock, ProductBatch batch) {
        validateBatchForAdjustment(a.getType(), batch, LocalDate.now(clock));
        if (a.getType() == AdjustmentType.DECREASE) {
            if (a.getBaseQuantity().compareTo(stock.availableToReserve()) > 0) throw new InventoryPostingException("Adjustment exceeds current unreserved stock");
            if (batch != null && a.getBaseQuantity().compareTo(batch.availableToReserve()) > 0) throw new InventoryPostingException("Adjustment exceeds current unreserved batch stock");
        }
    }

    private void validateBatchForAdjustment(AdjustmentType type, ProductBatch batch, LocalDate effectiveDate) {
        if (batch == null) return;
        if (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(effectiveDate) && type == AdjustmentType.DECREASE) {
            throw new InventoryPostingException("Expired batch cannot be used as sellable-stock adjustment decrease");
        }
        if (type == AdjustmentType.DECREASE && batch.getStatus() != ProductBatchStatus.ACTIVE) {
            throw new InventoryPostingException("Only Active batch may be decreased through Stock Adjustment");
        }
        if (type == AdjustmentType.INCREASE && (batch.getStatus() == ProductBatchStatus.RETURNED || batch.getStatus() == ProductBatchStatus.RECALLED)) {
            throw new InventoryPostingException("Returned or Recalled batch cannot be increased through Stock Adjustment");
        }
    }

    private ProductBatch lockBatchIfPresent(Long businessId, Long branchId, Long batchId, Long variantId) {
        if (batchId == null) return null;
        return batchRepository.findForUpdate(businessId, branchId, batchId)
                .filter(b -> b.getProductVariantId().equals(variantId))
                .orElseThrow(() -> new InventoryPostingException("Eligible batch was not found"));
    }
    private ProductBatch requireBatch(Long businessId, Long branchId, Long batchId, Long variantId) {
        return batchRepository.findByIdAndBusinessIdAndBranchId(batchId, businessId, branchId)
                .filter(b -> b.getProductVariantId().equals(variantId))
                .orElseThrow(() -> new InventoryPostingException("Eligible batch was not found"));
    }
    private StockAdjustment transition(Long ownerId, Long id, String action, String reason, Consumer<StockAdjustment> work) {
        StockAdjustment a = require(support.business(ownerId), id);
        try { work.accept(a); } catch (IllegalStateException e) { throw new InventoryOperationStateException(e.getMessage()); }
        audit(a, action, ownerId, reason);
        return a;
    }
    private StockAdjustment require(Long businessId, Long id) { return repository.findByIdAndBusinessId(id, businessId).orElseThrow(InventoryOperationNotFoundException::new); }
    private void audit(StockAdjustment a, String action, Long actor, String detail) { auditRepository.save(InventoryAuditEvent.record(a.getBusinessId(), a.getBranchId(), "ADJUSTMENT", a.getId(), action, actor, detail, Instant.now(clock))); }
    private String requiredKey(String value) { if (value == null || value.isBlank()) throw new InventoryPostingException("Idempotency key is required"); return value.trim(); }
    private String requiredReason(String value) { if (value == null || value.isBlank()) throw new InventoryPostingException("Reason is required"); return value.trim(); }
    private String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private record DraftState(InventoryOperationSupport.Resolved resolved, BranchProductStock stock, ProductBatch batch) {}
}
