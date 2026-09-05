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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class InventoryLossService {
    private final InventoryLossRepository repository;
    private final InventorySearchRepository searchRepository;
    private final BranchProductStockRepository stockRepository;
    private final ProductBatchRepository batchRepository;
    private final InventoryPostingService postingService;
    private final InventoryOperationSupport support;
    private final InventoryAuditEventRepository auditRepository;
    private final Clock clock;

    @Transactional
    public InventoryLoss create(CreateInventoryLossCommand command) {
        Long businessId = support.business(command.ownerId()); support.branch(businessId, command.branchId());
        String key = requiredKey(command.idempotencyKey());
        InventoryLoss repeated = repository.findByBusinessIdAndIdempotencyKey(businessId, key).orElse(null);
        if (repeated != null) return repeated;
        LossState state = validateDraft(businessId, command.branchId(), command.productVariantId(), command.productBatchId(),
                command.disposalDate(), command.enteredUnitId(), command.enteredQuantity());
        InventoryLoss loss = InventoryLoss.draft(businessId, command.branchId(), command.productVariantId(), command.productBatchId(),
                command.disposalDate(), command.enteredQuantity(), command.enteredUnitId(), state.resolved().factor(), state.resolved().baseQuantity(),
                state.stock().getBaseQuantity(), state.stock().getWeightedAverageCost(), requiredReason(command.reason()), optional(command.notes()),
                optional(command.attachmentReference()), state.batch() == null ? null : state.batch().getStatus(), command.ownerId(), key, Instant.now(clock));
        loss = repository.saveAndFlush(loss);
        audit(loss, "DRAFT_CREATED", command.ownerId(), loss.getReason());
        return loss;
    }

    @Transactional
    public InventoryLoss updateDraft(UpdateInventoryLossCommand command) {
        Long businessId = support.business(command.ownerId());
        InventoryLoss loss = require(businessId, command.lossId());
        if (loss.getStatus() != InventoryOperationStatus.DRAFT) throw new InventoryOperationStateException("Only Draft Loss may be updated");
        LossState state = validateDraft(businessId, loss.getBranchId(), command.productVariantId(), command.productBatchId(),
                command.disposalDate(), command.enteredUnitId(), command.enteredQuantity());
        loss.updateDraft(command.productVariantId(), command.productBatchId(), command.disposalDate(), command.enteredQuantity(),
                command.enteredUnitId(), state.resolved().factor(), state.resolved().baseQuantity(), state.stock().getBaseQuantity(),
                state.stock().getWeightedAverageCost(), requiredReason(command.reason()), optional(command.notes()), optional(command.attachmentReference()),
                state.batch() == null ? null : state.batch().getStatus());
        audit(loss, "DRAFT_UPDATED", command.ownerId(), loss.getReason());
        return loss;
    }

    @Transactional public InventoryLoss submit(Long ownerId, Long id) { return transition(ownerId, id, "SUBMITTED", "Inventory loss submitted for approval", InventoryLoss::submit); }
    @Transactional public InventoryLoss approve(Long ownerId, Long id) { return transition(ownerId, id, "APPROVED", "Inventory loss approved for posting", l -> l.approve(ownerId)); }
    @Transactional public InventoryLoss reject(Long ownerId, Long id) { return reject(ownerId, id, "Inventory loss rejected"); }
    @Transactional public InventoryLoss reject(Long ownerId, Long id, String reason) { return transition(ownerId, id, "REJECTED", requiredReason(reason), InventoryLoss::reject); }
    @Transactional public InventoryLoss cancel(Long ownerId, Long id) { return cancel(ownerId, id, "Inventory loss cancelled"); }
    @Transactional public InventoryLoss cancel(Long ownerId, Long id, String reason) { return transition(ownerId, id, "CANCELLED", requiredReason(reason), InventoryLoss::cancel); }

    @Transactional
    public void deleteDraft(Long ownerId, Long id) {
        InventoryLoss loss = require(support.business(ownerId), id);
        if (loss.getStatus() != InventoryOperationStatus.DRAFT) throw new InventoryOperationStateException("Only Draft Loss may be deleted");
        audit(loss, "DRAFT_DELETED", ownerId, "Draft inventory loss deleted");
        repository.delete(loss);
    }

    @Transactional
    public InventoryLoss post(Long ownerId, Long id) {
        Long businessId = support.business(ownerId);
        InventoryLoss loss = require(businessId, id);
        if (loss.getStatus() != InventoryOperationStatus.APPROVED) throw new InventoryOperationStateException("Only Approved Loss may be posted");
        BranchProductStock stock = stockRepository.findForUpdate(businessId, loss.getBranchId(), loss.getProductVariantId()).orElseThrow(InventoryStockNotFoundException::new);
        ProductBatch batch = lockBatchIfPresent(businessId, loss.getBranchId(), loss.getProductBatchId(), loss.getProductVariantId());
        if (loss.getBaseQuantity().compareTo(stock.availableToReserve()) > 0) throw new InventoryPostingException("Disposal quantity exceeds current unreserved stock");
        if (batch != null && loss.getBaseQuantity().compareTo(batch.availableToReserve()) > 0) throw new InventoryPostingException("Disposal quantity exceeds current unreserved batch stock");
        loss.refreshPostingSnapshot(stock.getBaseQuantity(), stock.getWeightedAverageCost(), batch == null ? null : batch.getStatus());

        InventoryPostingResponse posting = postingService.postOperationalChange(new OperationalStockChangeRequest(
                businessId, loss.getBranchId(), loss.getProductVariantId(), loss.getProductBatchId(), loss.getEnteredQuantity(), loss.getEnteredUnitId(),
                loss.getConversionFactor(), loss.getBaseQuantity(), false, loss.getWeightedAverageCostSnapshot(), StockMovementType.INVENTORY_LOSS,
                StockSourceType.INVENTORY_LOSS, String.valueOf(loss.getId()), null, "loss-post-" + loss.getId(), null, ownerId, loss.getReason(), loss.getNotes()));
        loss.posted(ownerId, posting.getMovement().getId(), Instant.now(clock));
        audit(loss, "POSTED", ownerId, "Movement " + posting.getMovement().getId() + "; financial loss " + loss.getFinancialLoss());
        return loss;
    }

    @Transactional
    public InventoryLoss reverse(Long ownerId, Long id, String reason, String idempotencyKey) {
        Long businessId = support.business(ownerId);
        InventoryLoss loss = require(businessId, id);
        if (loss.getStatus() == InventoryOperationStatus.REVERSED) return loss;
        if (loss.getStatus() != InventoryOperationStatus.POSTED) throw new InventoryOperationStateException("Only Posted Loss may be reversed");
        ProductBatch batch = lockBatchIfPresent(businessId, loss.getBranchId(), loss.getProductBatchId(), loss.getProductVariantId());
        StockMovementReversalRequest request = new StockMovementReversalRequest();
        request.setBusinessId(businessId); request.setBranchId(loss.getBranchId()); request.setOriginalMovementId(loss.getStockMovementId());
        request.setSourceReferenceId(String.valueOf(loss.getId())); request.setPostingKey(requiredKey(idempotencyKey));
        request.setActorType(InventoryActorType.OWNER); request.setActorId(ownerId); request.setReason(requiredReason(reason));
        Long movementId = postingService.reverseStockMovement(request).getMovement().getId();
        if (batch != null && loss.getPreviousBatchStatus() != null) {
            batch.restoreStatusAfterReversal(loss.getPreviousBatchStatus(), LocalDate.now(clock), Instant.now(clock));
            batchRepository.saveAndFlush(batch);
        }
        loss.reversed(movementId);
        audit(loss, "REVERSED", ownerId, reason);
        return loss;
    }

    @Transactional(readOnly = true)
    public List<InventoryLoss> list(Long ownerId, Long branchId) {
        Long businessId = support.business(ownerId); support.branch(businessId, branchId);
        return repository.findAllByBusinessIdAndBranchIdOrderByCreatedAtDesc(businessId, branchId);
    }

    @Transactional(readOnly = true)
    public Page<InventoryLoss> search(Long ownerId, Long branchId, InventoryLossFilter filter, Pageable pageable) {
        Long businessId = support.business(ownerId); support.branch(businessId, branchId);
        InventoryLossFilter f = filter == null ? new InventoryLossFilter(null,null,null,null,null,null,null,null,null) : filter;
        return searchRepository.searchLosses(businessId, branchId, f.lossId(), f.productVariantId(), f.batchId(), optional(f.reason()), f.status(),
                f.createdBy(), f.approvedBy(), f.from(), f.to(), pageable);
    }

    @Transactional(readOnly = true) public InventoryLoss find(Long ownerId, Long id) { return require(support.business(ownerId), id); }

    private LossState validateDraft(Long businessId, Long branchId, Long variantId, Long batchId, LocalDate disposalDate,
                                    Long unitId, java.math.BigDecimal enteredQuantity) {
        if (disposalDate == null) throw new InventoryPostingException("Disposal date is required");
        InventoryOperationSupport.Resolved resolved = support.resolve(businessId, branchId, variantId, unitId, enteredQuantity, disposalDate);
        BranchProductStock stock = stockRepository.findByBusinessIdAndBranchIdAndProductVariantId(businessId, branchId, variantId).orElseThrow(InventoryStockNotFoundException::new);
        if (resolved.baseQuantity().compareTo(stock.availableToReserve()) > 0) throw new InventoryPostingException("Disposal quantity exceeds unreserved stock");
        ProductBatch batch = null;
        if (resolved.variant().trackExpiry() || resolved.variant().batchTrackingRequired()) {
            if (batchId == null) throw new InventoryPostingException("Exact batch is required for expiry- or batch-controlled loss");
            batch = requireBatch(businessId, branchId, batchId, variantId);
            if (resolved.baseQuantity().compareTo(batch.availableToReserve()) > 0) throw new InventoryPostingException("Disposal quantity exceeds unreserved batch stock");
        } else if (batchId != null) {
            throw new InventoryPostingException("Batch must not be selected for a non batch-controlled Product Variant");
        }
        return new LossState(resolved, stock, batch);
    }

    private ProductBatch lockBatchIfPresent(Long businessId, Long branchId, Long batchId, Long variantId) {
        if (batchId == null) return null;
        return batchRepository.findForUpdate(businessId, branchId, batchId).filter(b -> b.getProductVariantId().equals(variantId))
                .orElseThrow(() -> new InventoryPostingException("Eligible loss batch was not found"));
    }
    private ProductBatch requireBatch(Long businessId, Long branchId, Long batchId, Long variantId) {
        return batchRepository.findByIdAndBusinessIdAndBranchId(batchId, businessId, branchId).filter(b -> b.getProductVariantId().equals(variantId))
                .orElseThrow(() -> new InventoryPostingException("Eligible loss batch was not found"));
    }
    private InventoryLoss transition(Long ownerId, Long id, String action, String reason, Consumer<InventoryLoss> work) {
        InventoryLoss l = require(support.business(ownerId), id);
        try { work.accept(l); } catch (IllegalStateException e) { throw new InventoryOperationStateException(e.getMessage()); }
        audit(l, action, ownerId, reason); return l;
    }
    private InventoryLoss require(Long businessId, Long id) { return repository.findByIdAndBusinessId(id, businessId).orElseThrow(InventoryOperationNotFoundException::new); }
    private void audit(InventoryLoss l, String action, Long actor, String detail) { auditRepository.save(InventoryAuditEvent.record(l.getBusinessId(), l.getBranchId(), "LOSS", l.getId(), action, actor, detail, Instant.now(clock))); }
    private String requiredKey(String v) { if (v == null || v.isBlank()) throw new InventoryPostingException("Idempotency key is required"); return v.trim(); }
    private String requiredReason(String v) { if (v == null || v.isBlank()) throw new InventoryPostingException("Reason is required"); return v.trim(); }
    private String optional(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private record LossState(InventoryOperationSupport.Resolved resolved, BranchProductStock stock, ProductBatch batch) {}
}
