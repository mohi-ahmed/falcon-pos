package com.spark.falcon.inventory.service;

import com.spark.falcon.inventory.dto.*;
import com.spark.falcon.inventory.entity.*;
import com.spark.falcon.inventory.exception.*;
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
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class BranchTransferService {
    private final BranchTransferRepository repository;
    private final InventorySearchRepository searchRepository;
    private final BranchTransferReceiptRepository receiptRepository;
    private final BranchProductStockRepository stockRepository;
    private final ProductBatchRepository batchRepository;
    private final InventoryPostingService postingService;
    private final InventoryOperationSupport support;
    private final InventoryAuditEventRepository auditRepository;
    private final Clock clock;

    @Transactional
    public BranchTransfer create(CreateBranchTransferCommand command) {
        Long businessId = support.business(command.ownerId());
        support.branch(businessId, command.sourceBranchId()); support.branch(businessId, command.destinationBranchId());
        String idempotencyKey = requiredKey(command.idempotencyKey());
        BranchTransfer repeated = repository.findByBusinessIdAndIdempotencyKey(businessId, idempotencyKey).orElse(null);
        if (repeated != null) return repeated;
        validateDates(command.requestDate(), command.expectedDispatchDate());
        BranchTransfer transfer = BranchTransfer.draft(businessId, command.sourceBranchId(), command.destinationBranchId(),
                command.requestDate(), command.expectedDispatchDate(), optional(command.notes()), optional(command.attachmentReference()),
                command.ownerId(), idempotencyKey, Instant.now(clock));
        populateItems(transfer, command.items(), command.requestDate());
        transfer = repository.saveAndFlush(transfer);
        audit(transfer, "DRAFT_CREATED", command.ownerId(), "Transfer draft created");
        return transfer;
    }

    @Transactional
    public BranchTransfer updateDraft(UpdateBranchTransferCommand command) {
        Long businessId = support.business(command.ownerId());
        BranchTransfer transfer = require(businessId, command.transferId());
        if (transfer.getStatus() != BranchTransferStatus.DRAFT) throw new InventoryOperationStateException("Only Draft Transfer may be updated");
        support.branch(businessId, command.destinationBranchId());
        validateDates(command.requestDate(), command.expectedDispatchDate());
        try { transfer.updateDraft(command.destinationBranchId(), command.requestDate(), command.expectedDispatchDate(), optional(command.notes()), optional(command.attachmentReference())); }
        catch (IllegalArgumentException | IllegalStateException e) { throw new InventoryPostingException(e.getMessage()); }
        transfer.clearDraftItems();
        populateItems(transfer, command.items(), command.requestDate());
        audit(transfer, "DRAFT_UPDATED", command.ownerId(), "Transfer draft updated");
        return transfer;
    }

    @Transactional public BranchTransfer submit(Long ownerId, Long id) { return transition(ownerId, id, "SUBMITTED", "Transfer submitted for approval", BranchTransfer::submit); }

    @Transactional
    public BranchTransfer approve(Long ownerId, Long id) {
        Long businessId = support.business(ownerId);
        BranchTransfer transfer = require(businessId, id);
        if (transfer.getStatus() != BranchTransferStatus.SUBMITTED) throw new InventoryOperationStateException("Only Submitted Transfer may be approved");
        Instant now = Instant.now(clock); LocalDate today = LocalDate.now(clock);
        for (BranchTransferItem item : transfer.getItems()) {
            BranchProductStock stock = stockRepository.findForUpdate(businessId, transfer.getSourceBranchId(), item.getProductVariantId()).orElseThrow(InventoryStockNotFoundException::new);
            if (item.getBaseQuantity().compareTo(stock.availableToReserve()) > 0) throw new InventoryPostingException("Transfer item exceeds current unreserved source stock");
            ProductBatch batch = lockBatchIfPresent(businessId, transfer.getSourceBranchId(), item.getSourceBatchId(), item.getProductVariantId());
            validateSellableBatch(batch, today, item.getBaseQuantity());
            try {
                stock.reserve(item.getBaseQuantity(), now);
                if (batch != null) batch.reserve(item.getBaseQuantity(), now);
            } catch (IllegalArgumentException e) { throw new InventoryPostingException(e.getMessage()); }
            stockRepository.saveAndFlush(stock);
            if (batch != null) batchRepository.saveAndFlush(batch);
        }
        transfer.approved(ownerId, now);
        audit(transfer, "APPROVED_AND_RESERVED", ownerId, "Source sellable quantity reserved");
        return transfer;
    }

    @Transactional public BranchTransfer reject(Long ownerId, Long id) { return reject(ownerId, id, "Transfer rejected"); }
    @Transactional public BranchTransfer reject(Long ownerId, Long id, String reason) { return transition(ownerId, id, "REJECTED", requiredReason(reason), BranchTransfer::rejected); }

    @Transactional
    public void deleteDraft(Long ownerId, Long id) {
        BranchTransfer transfer = require(support.business(ownerId), id);
        if (transfer.getStatus() != BranchTransferStatus.DRAFT) throw new InventoryOperationStateException("Only Draft Transfer may be deleted");
        audit(transfer, "DRAFT_DELETED", ownerId, "Draft transfer deleted"); repository.delete(transfer);
    }

    @Transactional
    public BranchTransfer dispatch(Long ownerId, Long id) {
        Long businessId = support.business(ownerId);
        BranchTransfer transfer = require(businessId, id);
        if (transfer.getStatus() != BranchTransferStatus.APPROVED) throw new InventoryOperationStateException("Only Approved Transfer may be dispatched");
        Instant now = Instant.now(clock); LocalDate today = LocalDate.now(clock);
        for (BranchTransferItem item : transfer.getItems()) {
            BranchProductStock stock = stockRepository.findForUpdate(businessId, transfer.getSourceBranchId(), item.getProductVariantId()).orElseThrow(InventoryStockNotFoundException::new);
            ProductBatch batch = lockBatchIfPresent(businessId, transfer.getSourceBranchId(), item.getSourceBatchId(), item.getProductVariantId());
            validateSellableBatch(batch, today, item.getBaseQuantity());
            if (stock.getReservedBaseQuantity().compareTo(item.getBaseQuantity()) < 0) throw new InventoryPostingException("Transfer reservation is no longer sufficient");
            if (batch != null && batch.getReservedBaseQuantity().compareTo(item.getBaseQuantity()) < 0) throw new InventoryPostingException("Transfer batch reservation is no longer sufficient");
            item.refreshTransferUnitCostSnapshot(stock.getWeightedAverageCost());
            try {
                stock.releaseReservation(item.getBaseQuantity(), now);
                if (batch != null) batch.releaseReservation(item.getBaseQuantity(), now);
            } catch (IllegalArgumentException e) { throw new InventoryPostingException(e.getMessage()); }
            stockRepository.saveAndFlush(stock); if (batch != null) batchRepository.saveAndFlush(batch);

            InventoryPostingResponse posting = postingService.postOperationalChange(new OperationalStockChangeRequest(
                    businessId, transfer.getSourceBranchId(), item.getProductVariantId(), item.getSourceBatchId(), item.getEnteredQuantity(),
                    item.getEnteredUnitId(), item.getConversionFactor(), item.getBaseQuantity(), false, item.getTransferUnitCostSnapshot(),
                    StockMovementType.TRANSFER_OUT, StockSourceType.BRANCH_TRANSFER, String.valueOf(transfer.getId()), String.valueOf(item.getId()),
                    "transfer-dispatch-" + transfer.getId() + "-" + item.getId(), null, ownerId, "Branch Transfer dispatch", transfer.getNotes()));
            item.dispatched(posting.getMovement().getId());
        }
        transfer.dispatched(ownerId, now);
        audit(transfer, "DISPATCHED", ownerId, "Source stock and value dispatched");
        return transfer;
    }

    @Transactional
    public BranchTransfer receive(ReceiveBranchTransferCommand command) {
        Long businessId = support.business(command.ownerId());
        BranchTransfer transfer = require(businessId, command.transferId());
        String receiptKey = requiredKey(command.idempotencyKey());
        if (receiptRepository.findByBusinessIdAndTransferIdAndIdempotencyKey(businessId, transfer.getId(), receiptKey).isPresent()) return transfer;
        if (transfer.getStatus() != BranchTransferStatus.DISPATCHED && transfer.getStatus() != BranchTransferStatus.PARTIALLY_RECEIVED) {
            throw new InventoryOperationStateException("Only Dispatched or Partially Received Transfer may be received");
        }
        if (command.items() == null || command.items().isEmpty()) throw new InventoryPostingException("Transfer receipt requires item disposition");

        Instant now = Instant.now(clock); LocalDate today = LocalDate.now(clock);
        BranchTransferReceipt receipt = receiptRepository.saveAndFlush(BranchTransferReceipt.create(businessId, transfer.getId(), receiptKey, command.ownerId(), now));
        Set<Long> receiptItems = new HashSet<>();
        for (ReceiveBranchTransferCommand.ItemReceipt disposition : command.items()) {
            if (!receiptItems.add(disposition.transferItemId())) throw new InventoryPostingException("Duplicate Transfer Item in the same receipt");
            BranchTransferItem item = transfer.getItems().stream().filter(i -> i.getId().equals(disposition.transferItemId())).findFirst().orElseThrow(InventoryOperationNotFoundException::new);
            BigDecimal accepted = nonNegative(disposition.acceptedQuantity());
            BigDecimal damaged = nonNegative(disposition.damagedQuantity());
            BigDecimal missing = nonNegative(disposition.missingQuantity());
            BigDecimal rejected = nonNegative(disposition.rejectedQuantity());
            BigDecimal total = accepted.add(damaged).add(missing).add(rejected);
            if (total.signum() <= 0 || total.compareTo(item.remaining()) > 0) throw new InventoryPostingException("Receipt disposition exceeds remaining dispatched quantity");
            String discrepancyReason = optional(disposition.discrepancyReason());
            if (damaged.add(missing).add(rejected).signum() > 0 && discrepancyReason == null) throw new InventoryPostingException("Discrepancy reason is required");

            Long destinationBatchId = null; Long movementId = null;
            if (accepted.signum() > 0) {
                ProductVariantAccessResponse variant = support.variant(businessId, transfer.getDestinationBranchId(), item.getProductVariantId());
                if (variant.trackExpiry() || variant.batchTrackingRequired()) {
                    ProductBatch source = requireBatch(businessId, transfer.getSourceBranchId(), item.getSourceBatchId(), item.getProductVariantId());
                    if (source.getExpiryDate() != null && source.getExpiryDate().isBefore(today)) {
                        throw new InventoryPostingException("Stock that expired in transit cannot enter destination sellable stock; record it as damaged or rejected");
                    }
                    ProductBatch destination = batchRepository.findByBusinessIdAndBranchIdAndProductVariantIdAndBatchNumberIgnoreCase(
                                    businessId, transfer.getDestinationBranchId(), item.getProductVariantId(), source.getBatchNumber())
                            .orElseGet(() -> batchRepository.saveAndFlush(ProductBatch.createTransferDestination(businessId, transfer.getDestinationBranchId(),
                                    item.getProductVariantId(), source.getBatchNumber(), source.getManufacturingDate(), source.getExpiryDate(),
                                    source.getOriginalPurchaseUnitCost(), item.getTransferUnitCostSnapshot(), now)));
                    destinationBatchId = destination.getId();
                }
                InventoryPostingResponse posting = postingService.postOperationalChange(new OperationalStockChangeRequest(
                        businessId, transfer.getDestinationBranchId(), item.getProductVariantId(), destinationBatchId,
                        accepted, variant.baseInventoryUnitId(), BigDecimal.ONE, accepted, true, item.getTransferUnitCostSnapshot(),
                        StockMovementType.TRANSFER_IN, StockSourceType.BRANCH_TRANSFER, String.valueOf(transfer.getId()), String.valueOf(item.getId()),
                        "transfer-receive-" + receiptKey + "-" + item.getId(), null, command.ownerId(), "Branch Transfer receipt", discrepancyReason));
                movementId = posting.getMovement().getId();
            }
            try { item.receive(accepted, damaged, missing, rejected, discrepancyReason, movementId, destinationBatchId); }
            catch (IllegalArgumentException e) { throw new InventoryPostingException(e.getMessage()); }
            receipt.addItem(BranchTransferReceiptItem.create(item.getId(), accepted, damaged, missing, rejected, discrepancyReason, destinationBatchId, movementId));
        }
        receiptRepository.saveAndFlush(receipt);
        try { transfer.received(command.ownerId(), now); } catch (IllegalStateException e) { throw new InventoryOperationStateException(e.getMessage()); }
        audit(transfer, "RECEIPT_RECORDED", command.ownerId(), "Receipt " + receiptKey + "; status " + transfer.getStatus());
        return transfer;
    }

    @Transactional
    public BranchTransfer resolveDiscrepancy(Long ownerId, Long id, Long itemId, TransferDiscrepancyResolutionType type,
                                             String linkedReference, String reason) {
        BranchTransfer transfer = require(support.business(ownerId), id);
        if (transfer.getStatus() != BranchTransferStatus.PARTIALLY_RECEIVED) throw new InventoryOperationStateException("Only Partially Received Transfer may resolve discrepancy");
        BranchTransferItem item = transfer.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst().orElseThrow(InventoryOperationNotFoundException::new);
        String resolutionReason = requiredReason(reason);
        try { item.resolveDiscrepancy(type, linkedReference, ownerId, Instant.now(clock)); transfer.received(ownerId, Instant.now(clock)); }
        catch (IllegalArgumentException | IllegalStateException e) { throw new InventoryOperationStateException(e.getMessage()); }
        audit(transfer, "DISCREPANCY_RESOLVED", ownerId, type + " -> " + linkedReference + "; " + resolutionReason);
        return transfer;
    }

    @Transactional
    public BranchTransfer cancelWithRelease(Long ownerId, Long id) { return cancelWithRelease(ownerId, id, "Transfer cancelled"); }

    @Transactional
    public BranchTransfer cancelWithRelease(Long ownerId, Long id, String reason) {
        Long businessId = support.business(ownerId); BranchTransfer transfer = require(businessId, id);
        if (!transfer.mayCancel()) throw new InventoryOperationStateException("Transfer cannot be cancelled");
        String cancellationReason = requiredReason(reason);
        if (transfer.getStatus() == BranchTransferStatus.APPROVED) {
            Instant now = Instant.now(clock);
            for (BranchTransferItem item : transfer.getItems()) {
                BranchProductStock stock = stockRepository.findForUpdate(businessId, transfer.getSourceBranchId(), item.getProductVariantId()).orElseThrow(InventoryStockNotFoundException::new);
                try { stock.releaseReservation(item.getBaseQuantity(), now); } catch (IllegalArgumentException e) { throw new InventoryPostingException(e.getMessage()); }
                stockRepository.saveAndFlush(stock);
                if (item.getSourceBatchId() != null) {
                    ProductBatch batch = batchRepository.findForUpdate(businessId, transfer.getSourceBranchId(), item.getSourceBatchId()).orElseThrow(InventoryOperationNotFoundException::new);
                    try { batch.releaseReservation(item.getBaseQuantity(), now); } catch (IllegalArgumentException e) { throw new InventoryPostingException(e.getMessage()); }
                    batchRepository.saveAndFlush(batch);
                }
            }
        }
        transfer.cancelledAfterRelease(); audit(transfer, "CANCELLED", ownerId, cancellationReason); return transfer;
    }

    @Transactional(readOnly = true)
    public List<BranchTransfer> list(Long ownerId, Long branchId) {
        Long businessId = support.business(ownerId); support.branch(businessId, branchId);
        return repository.findAllByBusinessIdAndSourceBranchIdOrBusinessIdAndDestinationBranchIdOrderByCreatedAtDesc(businessId, branchId, businessId, branchId);
    }

    @Transactional(readOnly = true)
    public Page<BranchTransfer> search(Long ownerId, Long branchId, BranchTransferFilter filter, Pageable pageable) {
        Long businessId = support.business(ownerId); support.branch(businessId, branchId);
        BranchTransferFilter f = filter == null ? new BranchTransferFilter(null,null,null,null,null,null,null,null,null,null) : filter;
        Page<BranchTransfer> page = searchRepository.searchTransfers(businessId, branchId, f.transferId(), f.sourceBranchId(), f.destinationBranchId(), f.status(), f.createdBy(),
                f.approvedBy(), f.dispatchedBy(), f.receivedBy(), f.from(), f.to(), pageable);
        // List rendering needs item counts/value summaries after the service transaction closes.
        // Initialize items here rather than relying on Open-Session-In-View.
        page.getContent().forEach(transfer -> transfer.getItems().size());
        return page;
    }

    @Transactional(readOnly = true) public BranchTransfer find(Long ownerId, Long id) { return require(support.business(ownerId), id); }
    @Transactional(readOnly = true) public List<BranchTransferReceipt> receipts(Long ownerId, Long transferId) {
        Long businessId = support.business(ownerId); require(businessId, transferId);
        return receiptRepository.findByBusinessIdAndTransferIdOrderByReceivedAtAscIdAsc(businessId, transferId);
    }

    private void populateItems(BranchTransfer transfer, List<CreateBranchTransferCommand.Item> items, LocalDate effectiveDate) {
        if (items == null || items.isEmpty()) throw new InventoryPostingException("Transfer requires items");
        Set<String> unique = new HashSet<>();
        for (CreateBranchTransferCommand.Item item : items) {
            InventoryOperationSupport.Resolved resolved = support.resolve(transfer.getBusinessId(), transfer.getSourceBranchId(), item.productVariantId(), item.enteredUnitId(), item.enteredQuantity(), effectiveDate);
            String identity = item.productVariantId() + ":" + (item.productBatchId() == null ? "NO_BATCH" : item.productBatchId());
            if (!unique.add(identity)) throw new InventoryPostingException("Duplicate Product Variant or Batch in Transfer");
            BranchProductStock stock = stockRepository.findByBusinessIdAndBranchIdAndProductVariantId(transfer.getBusinessId(), transfer.getSourceBranchId(), item.productVariantId()).orElseThrow(InventoryStockNotFoundException::new);
            if (resolved.baseQuantity().compareTo(stock.availableToReserve()) > 0) throw new InventoryPostingException("Transfer exceeds available sellable stock");
            ProductBatch batch = null;
            if (resolved.variant().trackExpiry() || resolved.variant().batchTrackingRequired()) {
                if (item.productBatchId() == null) throw new InventoryPostingException("Exact batch is required for Transfer");
                batch = requireBatch(transfer.getBusinessId(), transfer.getSourceBranchId(), item.productBatchId(), item.productVariantId());
                validateSellableBatch(batch, effectiveDate, resolved.baseQuantity());
            } else if (item.productBatchId() != null) {
                throw new InventoryPostingException("Batch must not be selected for a non batch-controlled Product Variant");
            }
            transfer.add(BranchTransferItem.create(item.productVariantId(), item.productBatchId(), item.enteredQuantity(), item.enteredUnitId(),
                    resolved.factor(), resolved.baseQuantity(), stock.getWeightedAverageCost()));
        }
    }

    private void validateSellableBatch(ProductBatch batch, LocalDate effectiveDate, BigDecimal needed) {
        if (batch == null) return;
        if (batch.getStatus() != ProductBatchStatus.ACTIVE || (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(effectiveDate))) {
            throw new InventoryPostingException("Batch is not eligible for sellable Transfer");
        }
        if (needed.compareTo(batch.availableToReserve()) > 0 && batch.getReservedBaseQuantity().compareTo(needed) < 0) {
            throw new InventoryPostingException("Batch quantity is not sufficient for Transfer");
        }
    }

    private ProductBatch lockBatchIfPresent(Long businessId, Long branchId, Long batchId, Long variantId) {
        if (batchId == null) return null;
        return batchRepository.findForUpdate(businessId, branchId, batchId).filter(b -> b.getProductVariantId().equals(variantId))
                .orElseThrow(() -> new InventoryPostingException("Transfer batch was not found"));
    }
    private ProductBatch requireBatch(Long businessId, Long branchId, Long batchId, Long variantId) {
        return batchRepository.findByIdAndBusinessIdAndBranchId(batchId, businessId, branchId).filter(b -> b.getProductVariantId().equals(variantId))
                .orElseThrow(() -> new InventoryPostingException("Transfer batch was not found"));
    }
    private BranchTransfer transition(Long ownerId, Long id, String action, String reason, Consumer<BranchTransfer> work) {
        BranchTransfer t = require(support.business(ownerId), id);
        try { work.accept(t); } catch (IllegalStateException e) { throw new InventoryOperationStateException(e.getMessage()); }
        audit(t, action, ownerId, reason); return t;
    }
    private BranchTransfer require(Long businessId, Long id) { return repository.findByIdAndBusinessId(id, businessId).orElseThrow(InventoryOperationNotFoundException::new); }
    private void audit(BranchTransfer t, String action, Long actor, String detail) { auditRepository.save(InventoryAuditEvent.record(t.getBusinessId(), t.getSourceBranchId(), "TRANSFER", t.getId(), action, actor, detail, Instant.now(clock))); }
    private void validateDates(LocalDate request, LocalDate expected) { if (request == null) throw new InventoryPostingException("Transfer request date is required"); if (expected != null && expected.isBefore(request)) throw new InventoryPostingException("Expected dispatch date cannot be before request date"); }
    private String requiredKey(String v) { if (v == null || v.isBlank()) throw new InventoryPostingException("Idempotency key is required"); return v.trim(); }
    private String requiredReason(String v) { if (v == null || v.isBlank()) throw new InventoryPostingException("Reason is required"); return v.trim(); }
    private String optional(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private BigDecimal nonNegative(BigDecimal v) { BigDecimal n = v == null ? BigDecimal.ZERO.setScale(8) : v; if (n.signum() < 0) throw new InventoryPostingException("Receipt quantities must not be negative"); return n; }
}
