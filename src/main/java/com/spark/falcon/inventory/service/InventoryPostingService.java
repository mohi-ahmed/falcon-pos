package com.spark.falcon.inventory.service;

import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.inventory.dto.*;
import com.spark.falcon.inventory.entity.*;
import com.spark.falcon.inventory.exception.InventoryAccessDeniedException;
import com.spark.falcon.inventory.exception.InventoryPostingException;
import com.spark.falcon.inventory.exception.InventoryStockNotFoundException;
import com.spark.falcon.inventory.repository.BranchProductStockRepository;
import com.spark.falcon.inventory.repository.ProductBatchRepository;
import com.spark.falcon.inventory.repository.StockMovementRepository;
import com.spark.falcon.product.dto.response.ProductUnitConversionResponse;
import com.spark.falcon.product.dto.response.ProductVariantAccessResponse;
import com.spark.falcon.product.service.ProductAccessService;
import com.spark.falcon.settings.service.BranchSettingsAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryPostingService {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final int QUANTITY_SCALE = 8;
    private static final int MONEY_SCALE = 4;

    private final BranchProductStockRepository stockRepository;
    private final ProductBatchRepository batchRepository;
    private final StockMovementRepository movementRepository;
    private final BranchAccessService branchAccessService;
    private final ProductAccessService productAccessService;
    private final BranchSettingsAccessService branchSettingsAccessService;
    private final Clock clock;

    @Transactional
    public InventoryPostingResponse postOperationalChange(OperationalStockChangeRequest request) {
        if (request == null || request.businessId() == null || request.branchId() == null
                || request.productVariantId() == null || request.baseQuantity() == null
                || request.baseQuantity().signum() <= 0 || request.enteredQuantity() == null
                || request.enteredUnitId() == null || request.conversionFactor() == null
                || request.conversionFactor().signum() <= 0 || request.movementType() == null
                || request.sourceType() == null || request.sourceReferenceId() == null
                || request.sourceReferenceId().isBlank() || request.postingKey() == null
                || request.postingKey().isBlank() || request.actorId() == null) {
            throw new InventoryPostingException("Complete operational stock-change information is required");
        }
        Optional<StockMovement> repeated = movementRepository.findByBusinessIdAndBranchIdAndPostingKey(
                request.businessId(), request.branchId(), request.postingKey().trim());
        if (repeated.isPresent()) return originalPosting(repeated.get());
        requireBranch(request.businessId(), request.branchId());
        ProductVariantAccessResponse variant=requireVariant(request.businessId(),request.branchId(),request.productVariantId());
        BranchProductStock stock=stockRepository.findForUpdate(request.businessId(),request.branchId(),request.productVariantId())
                .orElseGet(()->BranchProductStock.create(request.businessId(),request.branchId(),request.productVariantId(),variant.baseInventoryUnitId(),Instant.now(clock)));
        ProductBatch batch=null; if(variant.trackExpiry()||variant.batchTrackingRequired()) {
            if(request.productBatchId()==null) throw new InventoryPostingException("Exact batch is required for this Product Variant");
            batch=batchRepository.findForUpdate(request.businessId(),request.branchId(),request.productBatchId()).orElseThrow(()->new InventoryPostingException("Eligible Product Batch was not found"));
            if(!batch.getProductVariantId().equals(request.productVariantId())) throw new InventoryPostingException("Batch does not belong to Product Variant");
        }
        Instant now=Instant.now(clock);BigDecimal before=stock.getBaseQuantity();BigDecimal cost=request.preservedUnitCost()==null?stock.getWeightedAverageCost():request.preservedUnitCost();BigDecimal value;
        if (!request.increase() && request.movementType() != StockMovementType.TRANSFER_OUT) {
            if (request.baseQuantity().compareTo(stock.availableToReserve()) > 0) {
                throw new InventoryPostingException("Stock change exceeds current unreserved branch stock");
            }
            if (batch != null && request.baseQuantity().compareTo(batch.availableToReserve()) > 0) {
                throw new InventoryPostingException("Stock change exceeds current unreserved batch stock");
            }
        }
        try { if(request.increase()){stock.receive(request.baseQuantity(),cost,now);if(batch!=null){if(request.sourceType()==StockSourceType.BRANCH_TRANSFER)batch.receiveTransfer(request.baseQuantity(),now);else batch.adjustAvailable(request.baseQuantity(),now);}value=request.baseQuantity().multiply(cost).setScale(4,RoundingMode.HALF_UP);}else{if(request.movementType()==StockMovementType.TRANSFER_OUT){value=stock.removeAtPreservedCost(request.baseQuantity(),cost,now);}else{value=stock.decreaseAtWeightedAverageCost(request.baseQuantity(),now);cost=before.signum()==0?BigDecimal.ZERO:value.divide(request.baseQuantity(),4,RoundingMode.HALF_UP);}if(batch!=null)batch.adjustAvailable(request.baseQuantity().negate(),now);} }
        catch(IllegalArgumentException e){throw new InventoryPostingException(e.getMessage());}
        stock=stockRepository.saveAndFlush(stock);if(batch!=null)batch=batchRepository.saveAndFlush(batch);
        BigDecimal signedQty=request.increase()?request.baseQuantity():request.baseQuantity().negate();BigDecimal signedValue=request.increase()?value:value.negate();
        boolean transferReceipt = request.movementType() == StockMovementType.TRANSFER_IN;
        BigDecimal enteredSnapshot = transferReceipt ? request.baseQuantity() : request.enteredQuantity();
        Long enteredUnitSnapshot = transferReceipt ? variant.baseInventoryUnitId() : request.enteredUnitId();
        BigDecimal conversionSnapshot = transferReceipt ? BigDecimal.ONE : request.conversionFactor();
        StockMovement movement=StockMovement.change(request.businessId(),request.branchId(),stock.getId(),request.productVariantId(),batch==null?null:batch.getId(),request.movementType(),enteredSnapshot,enteredUnitSnapshot,conversionSnapshot,variant.baseInventoryUnitId(),signedQty,before,stock.getBaseQuantity(),cost,signedValue,request.sourceType(),request.sourceReferenceId(),request.sourceLineReference(),request.postingKey().trim(),request.reversalOfMovementId(),InventoryActorType.OWNER,request.actorId(),request.reason(),request.notes(),now);
        return new InventoryPostingResponse(toResponse(stock),toResponse(batch),toResponse(movementRepository.saveAndFlush(movement)));
    }

    @Transactional
    public SaleStockPostingResponse postSaleStock(SaleStockPostingRequest request) {
        validateSaleStockRequest(request);
        Optional<StockMovement> repeated = movementRepository.findByBusinessIdAndBranchIdAndPostingKey(
                request.businessId(), request.branchId(), request.postingKey().trim());
        if (repeated.isPresent()) {
            return new SaleStockPostingResponse(
                    toResponse(repeated.get()), repeated.get().getFinancialUnitCostSnapshot(), List.of());
        }
        requireBranch(request.businessId(), request.branchId());
        ProductVariantAccessResponse variant = requireVariant(
                request.businessId(), request.branchId(), request.productVariantId());
        if (!variant.baseInventoryUnitId().equals(request.baseInventoryUnitId())) {
            throw new InventoryPostingException("Sale Base Inventory Unit does not match Product Variant");
        }
        BigDecimal quantity = normalizeQuantity(request.baseQuantity());
        BranchProductStock stock = stockRepository.findForUpdate(
                        request.businessId(), request.branchId(), request.productVariantId())
                .orElseThrow(InventoryStockNotFoundException::new);
        if (quantity.compareTo(stock.availableToReserve()) > 0) {
            throw new InventoryPostingException("Sale quantity exceeds unreserved sellable branch stock");
        }
        Instant now = Instant.now(clock);
        BigDecimal quantityBefore = stock.getBaseQuantity();
        BigDecimal costSnapshot = stock.getWeightedAverageCost();
        List<InventorySaleBatchAllocationResponse> allocations = new ArrayList<>();
        if (variant.trackExpiry() || variant.batchTrackingRequired()) {
            BigDecimal remaining = quantity;
            List<ProductBatch> candidates = batchRepository
                    .findByBusinessIdAndBranchIdAndProductVariantIdOrderByExpiryDateAscIdAsc(
                            request.businessId(), request.branchId(), request.productVariantId()).stream()
                    .filter(batch -> batch.getStatus() == ProductBatchStatus.ACTIVE)
                    .filter(batch -> batch.availableToReserve().signum() > 0)
                    .filter(batch -> !variant.trackExpiry() || batch.getExpiryDate() == null
                            || !batch.getExpiryDate().isBefore(request.saleDate()))
                    .toList();
            for (ProductBatch candidate : candidates) {
                if (remaining.signum() == 0) break;
                ProductBatch batch = batchRepository.findForUpdate(
                                request.businessId(), request.branchId(), candidate.getId())
                        .orElseThrow(() -> new InventoryPostingException("Eligible Sale batch was not found"));
                BigDecimal allocated = remaining.min(batch.availableToReserve());
                batch.reduceAvailable(allocated, now);
                batchRepository.saveAndFlush(batch);
                allocations.add(new InventorySaleBatchAllocationResponse(
                        batch.getId(), batch.getBatchNumber(), batch.getExpiryDate(), allocated));
                remaining = remaining.subtract(allocated);
            }
            if (remaining.signum() > 0) {
                throw new InventoryPostingException("Eligible FEFO batch stock cannot satisfy Sale quantity");
            }
        }
        BigDecimal valueRemoved;
        try {
            valueRemoved = stock.decreaseAtWeightedAverageCost(quantity, now);
        } catch (IllegalArgumentException ex) {
            throw new InventoryPostingException(ex.getMessage());
        }
        stock = stockRepository.saveAndFlush(stock);
        StockMovement movement = StockMovement.change(
                request.businessId(), request.branchId(), stock.getId(), request.productVariantId(), null,
                StockMovementType.SALE, request.enteredQuantity(), request.enteredUnitId(),
                request.conversionFactor(), request.baseInventoryUnitId(), quantity.negate(), quantityBefore,
                stock.getBaseQuantity(), costSnapshot, valueRemoved.negate(), StockSourceType.SALE,
                String.valueOf(request.saleId()), String.valueOf(request.saleItemId()), request.postingKey(), null,
                InventoryActorType.OWNER, request.actorId(), "Sale confirmation", request.notes(), now);
        movement = movementRepository.saveAndFlush(movement);
        return new SaleStockPostingResponse(toResponse(movement), costSnapshot, List.copyOf(allocations));
    }

    @Transactional
    public SaleReturnStockPostingResponse postSaleReturnStock(SaleReturnStockPostingRequest request) {
        validateSaleReturnRequest(request);
        Optional<StockMovement> repeated = movementRepository.findByBusinessIdAndBranchIdAndPostingKey(
                request.businessId(), request.branchId(), request.postingKey().trim());
        if (repeated.isPresent()) return new SaleReturnStockPostingResponse(toResponse(repeated.get()));
        requireBranch(request.businessId(), request.branchId());
        BranchProductStock stock = stockRepository.findForUpdate(
                        request.businessId(), request.branchId(), request.productVariantId())
                .orElseThrow(InventoryStockNotFoundException::new);
        Instant now = Instant.now(clock);
        BigDecimal before = stock.getBaseQuantity();
        BigDecimal valueChange = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        ProductBatch batch = null;
        if (request.sellable()) {
            stock.receive(request.baseQuantity(), request.preservedFinancialCostSnapshot(), now);
            valueChange = request.baseQuantity().multiply(request.preservedFinancialCostSnapshot())
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (request.productBatchId() != null) {
                batch = batchRepository.findForUpdate(request.businessId(), request.branchId(), request.productBatchId())
                        .orElseThrow(() -> new InventoryPostingException("Original Sale batch was not found"));
                if (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(request.returnDate())) {
                    throw new InventoryPostingException("Expired returned item cannot be restored as sellable stock");
                }
                batch.adjustAvailable(request.baseQuantity(), now);
                batchRepository.saveAndFlush(batch);
            }
            stock = stockRepository.saveAndFlush(stock);
        }
        StockMovement movement = StockMovement.change(
                request.businessId(), request.branchId(), stock.getId(), request.productVariantId(),
                batch == null ? request.productBatchId() : batch.getId(), StockMovementType.SALE_RETURN,
                request.enteredReturnQuantity(), request.returnUnitId(), request.conversionFactor(),
                request.baseInventoryUnitId(), request.baseQuantity(), before, stock.getBaseQuantity(),
                request.preservedFinancialCostSnapshot(), valueChange, StockSourceType.SALE_RETURN,
                String.valueOf(request.saleReturnId()), String.valueOf(request.saleReturnItemId()),
                request.postingKey(), null, InventoryActorType.OWNER, request.actorId(), request.reason(),
                request.notes(), now);
        return new SaleReturnStockPostingResponse(toResponse(movementRepository.saveAndFlush(movement)));
    }

    @Transactional
    public InventoryPostingResponse receiveStock(StockReceiptRequest request) {
        validateRequiredRequest(request);

        Optional<StockMovement> repeated = movementRepository.findByBusinessIdAndBranchIdAndPostingKey(
                request.getBusinessId(), request.getBranchId(), request.getPostingKey().trim());
        if (repeated.isPresent()) {
            return originalPosting(repeated.get());
        }

        requireBranch(request.getBusinessId(), request.getBranchId());
        ProductVariantAccessResponse variant = requireVariant(
                request.getBusinessId(), request.getBranchId(), request.getProductVariantId());

        ResolvedQuantity quantity = resolveQuantity(request, variant);
        validateCost(request.getLandedBaseUnitCost(), "landedBaseUnitCost");
        if (request.getOriginalPurchaseUnitCost() != null) {
            validateCost(request.getOriginalPurchaseUnitCost(), "originalPurchaseUnitCost");
        }

        Instant now = Instant.now(clock);
        ProductBatch batch = createPurchaseBatchIfRequired(request, variant, quantity.baseQuantity(), now);

        BranchProductStock stock = stockRepository.findForUpdate(
                        request.getBusinessId(), request.getBranchId(), request.getProductVariantId())
                .orElseGet(() -> BranchProductStock.create(
                        request.getBusinessId(), request.getBranchId(), request.getProductVariantId(),
                        variant.baseInventoryUnitId(), now));

        ensureBaseUnit(stock, variant.baseInventoryUnitId());

        BigDecimal quantityBefore = stock.getBaseQuantity();
        stock.receive(quantity.baseQuantity(), request.getLandedBaseUnitCost(), now);
        stock = stockRepository.saveAndFlush(stock);

        if (batch != null) {
            batch = batchRepository.saveAndFlush(batch);
        }

        BigDecimal valueChange = quantity.baseQuantity().multiply(request.getLandedBaseUnitCost())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        StockMovement movement = StockMovement.inbound(
                request.getBusinessId(), request.getBranchId(), stock.getId(), request.getProductVariantId(),
                batch == null ? null : batch.getId(), request.getMovementType(), request.getEnteredQuantity(),
                request.getEnteredUnitId(), quantity.conversionFactor(), variant.baseInventoryUnitId(),
                quantity.baseQuantity(), quantityBefore, stock.getBaseQuantity(), request.getLandedBaseUnitCost(),
                valueChange, request.getSourceType(), request.getSourceReferenceId(), request.getSourceLineReference(),
                request.getPostingKey(), request.getActorType(), request.getActorId(), request.getReason(),
                request.getNotes(), now);
        movement = movementRepository.saveAndFlush(movement);

        return new InventoryPostingResponse(toResponse(stock), toResponse(batch), toResponse(movement));
    }

    @Transactional
    public InventoryPostingResponse returnPurchaseStock(PurchaseReturnStockRequest request) {
        validatePurchaseReturnRequest(request);

        Optional<StockMovement> repeated = movementRepository.findByBusinessIdAndBranchIdAndPostingKey(
                request.getBusinessId(), request.getBranchId(), request.getPostingKey().trim());
        if (repeated.isPresent()) return originalPosting(repeated.get());

        requireBranch(request.getBusinessId(), request.getBranchId());
        ProductVariantAccessResponse variant = requireVariant(
                request.getBusinessId(), request.getBranchId(), request.getProductVariantId());

        if (!variant.baseInventoryUnitId().equals(request.getBaseInventoryUnitIdSnapshot())) {
            throw new InventoryPostingException("Purchase return Base Inventory Unit snapshot does not match Product Variant");
        }

        BigDecimal baseQuantity = normalizeQuantity(request.getBaseQuantity());
        if (baseQuantity.signum() <= 0) {
            throw new InventoryPostingException("Purchase return Base Quantity must be greater than zero");
        }
        validateCost(request.getPreservedAllocatedLandedUnitCost(), "preservedAllocatedLandedUnitCost");

        BranchProductStock stock = stockRepository.findForUpdate(
                        request.getBusinessId(), request.getBranchId(), request.getProductVariantId())
                .orElseThrow(InventoryStockNotFoundException::new);
        ensureBaseUnit(stock, variant.baseInventoryUnitId());

        Instant now = Instant.now(clock);
        ProductBatch batch = null;
        BigDecimal preservedCost = request.getPreservedAllocatedLandedUnitCost();

        if (request.getProductBatchId() != null) {
            batch = batchRepository.findForUpdate(
                            request.getBusinessId(), request.getBranchId(), request.getProductBatchId())
                    .orElseThrow(() -> new InventoryPostingException("Purchase return batch was not found"));
            if (!batch.getProductVariantId().equals(request.getProductVariantId())) {
                throw new InventoryPostingException("Purchase return batch does not belong to Product Variant");
            }
            if (batch.getAllocatedLandedUnitCost().compareTo(preservedCost) != 0) {
                throw new InventoryPostingException("Purchase return cost must use the original batch allocated landed unit cost");
            }
            if (baseQuantity.compareTo(batch.availableToReserve()) > 0) {
                throw new InventoryPostingException("Purchase return quantity exceeds unreserved batch stock");
            }
            batch.returnToSupplier(baseQuantity, now);
            batch = batchRepository.saveAndFlush(batch);
        }

        if (baseQuantity.compareTo(stock.availableToReserve()) > 0) {
            throw new InventoryPostingException("Purchase return quantity exceeds unreserved branch stock");
        }
        BigDecimal quantityBefore = stock.getBaseQuantity();
        BigDecimal valueRemoved;
        try {
            valueRemoved = stock.removeAtPreservedCost(baseQuantity, preservedCost, now);
        } catch (IllegalArgumentException ex) {
            throw new InventoryPostingException(ex.getMessage());
        }
        stock = stockRepository.saveAndFlush(stock);

        StockMovement movement = StockMovement.change(
                request.getBusinessId(), request.getBranchId(), stock.getId(), request.getProductVariantId(),
                batch == null ? null : batch.getId(), StockMovementType.PURCHASE_RETURN,
                request.getEnteredReturnQuantity(), request.getReturnUnitId(), request.getConversionFactorSnapshot(),
                request.getBaseInventoryUnitIdSnapshot(), baseQuantity.negate(), quantityBefore, stock.getBaseQuantity(),
                preservedCost, valueRemoved.negate(), StockSourceType.PURCHASE_RETURN,
                request.getSourceReferenceId(), request.getSourceLineReference(), request.getPostingKey(), null,
                request.getActorType(), request.getActorId(), request.getReason(), request.getNotes(), now);
        movement = movementRepository.saveAndFlush(movement);

        return new InventoryPostingResponse(toResponse(stock), toResponse(batch), toResponse(movement));
    }

    @Transactional
    public InventoryPostingResponse postStockImport(
            StockImportPostingRequest request,
            BigDecimal expectedCurrentSystemQuantity) {
        validateStockImportRequest(request);
        BigDecimal expectedCurrent = normalizeQuantity(expectedCurrentSystemQuantity);
        if (expectedCurrent.signum() < 0) {
            throw new InventoryPostingException("Validated current system quantity must not be negative");
        }

        Optional<StockMovement> repeated = movementRepository.findByBusinessIdAndBranchIdAndPostingKey(
                request.getBusinessId(), request.getBranchId(), request.getPostingKey().trim());
        if (repeated.isPresent()) return originalPosting(repeated.get());

        requireBranch(request.getBusinessId(), request.getBranchId());
        ProductVariantAccessResponse variant = requireVariant(
                request.getBusinessId(), request.getBranchId(), request.getProductVariantId());

        if (!variant.baseInventoryUnitId().equals(request.getBaseInventoryUnitIdSnapshot())) {
            throw new InventoryPostingException("Stock Import Base Inventory Unit snapshot does not match Product Variant");
        }

        BigDecimal change = normalizeSignedQuantity(request.getBaseQuantityChange());
        if (change.signum() == 0) {
            throw new InventoryPostingException("Stock Import quantity difference must not be zero");
        }

        Instant now = Instant.now(clock);
        BranchProductStock stock = stockRepository.findForUpdate(
                        request.getBusinessId(), request.getBranchId(), request.getProductVariantId())
                .orElse(null);

        boolean batchControlled = variant.trackExpiry() || variant.batchTrackingRequired();
        ProductBatch lockedExistingBatch = null;
        if (batchControlled && request.getExistingProductBatchId() != null) {
            lockedExistingBatch = batchRepository.findForUpdate(
                            request.getBusinessId(), request.getBranchId(), request.getExistingProductBatchId())
                    .orElseThrow(() -> new InventoryPostingException("Stock Import batch was not found"));
            if (!lockedExistingBatch.getProductVariantId().equals(request.getProductVariantId())) {
                throw new InventoryPostingException("Stock Import batch does not belong to Product Variant");
            }
        }

        BigDecimal currentAtPosting = batchControlled
                ? (lockedExistingBatch == null ? BigDecimal.ZERO.setScale(QUANTITY_SCALE) : lockedExistingBatch.getAvailableBaseQuantity())
                : (stock == null ? BigDecimal.ZERO.setScale(QUANTITY_SCALE) : stock.getBaseQuantity());
        if (currentAtPosting.compareTo(expectedCurrent) != 0) {
            throw new InventoryPostingException(
                    "Stock changed after Stock Import validation; validate the Import Batch again before confirmation");
        }

        if (stock == null && change.signum() < 0) {
            throw new InventoryPostingException("Stock Import cannot reduce stock that does not exist");
        }
        if (stock == null) {
            stock = BranchProductStock.create(
                    request.getBusinessId(), request.getBranchId(), request.getProductVariantId(),
                    variant.baseInventoryUnitId(), now);
        }
        ensureBaseUnit(stock, variant.baseInventoryUnitId());

        BigDecimal quantityBefore = stock.getBaseQuantity();
        BigDecimal costSnapshot;
        BigDecimal inventoryValueChange;

        if (request.isNonSellableImport()) {
            if (!variant.trackExpiry() && !variant.batchTrackingRequired()) {
                throw new InventoryPostingException("Non-sellable Stock Import requires a batch-controlled Product Variant");
            }
            costSnapshot = request.getLandedBaseUnitCost() == null
                    ? stock.getWeightedAverageCost()
                    : request.getLandedBaseUnitCost();
            validateCost(costSnapshot, "landedBaseUnitCost");
            inventoryValueChange = change.multiply(costSnapshot).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        } else if (change.signum() > 0) {
            costSnapshot = request.getLandedBaseUnitCost() == null
                    ? stock.getWeightedAverageCost()
                    : request.getLandedBaseUnitCost();
            validateCost(costSnapshot, "landedBaseUnitCost");
            stock.receive(change, costSnapshot, now);
            inventoryValueChange = change.multiply(costSnapshot).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        } else {
            costSnapshot = stock.getWeightedAverageCost();
            try {
                BigDecimal removed = stock.decreaseAtWeightedAverageCost(change.abs(), now);
                inventoryValueChange = removed.negate();
            } catch (IllegalArgumentException ex) {
                throw new InventoryPostingException(ex.getMessage());
            }
        }
        stock = stockRepository.saveAndFlush(stock);

        ProductBatch batch = updateImportBatchIfRequired(
                request, variant, change, costSnapshot, now, lockedExistingBatch);

        StockMovement movement = StockMovement.change(
                request.getBusinessId(), request.getBranchId(), stock.getId(), request.getProductVariantId(),
                batch == null ? null : batch.getId(), StockMovementType.STOCK_IMPORT,
                request.getEnteredQuantity(), request.getEnteredUnitId(), request.getConversionFactorSnapshot(),
                request.getBaseInventoryUnitIdSnapshot(), change, quantityBefore, stock.getBaseQuantity(),
                costSnapshot, inventoryValueChange, StockSourceType.STOCK_IMPORT,
                request.getSourceReferenceId(), request.getSourceLineReference(), request.getPostingKey(), null,
                request.getActorType(), request.getActorId(), request.getReason(), request.getNotes(), now);
        movement = movementRepository.saveAndFlush(movement);

        return new InventoryPostingResponse(toResponse(stock), toResponse(batch), toResponse(movement));
    }

    @Transactional
    public InventoryPostingResponse reverseStockMovement(StockMovementReversalRequest request) {
        validateReversalRequest(request);

        Optional<StockMovement> repeated = movementRepository.findByBusinessIdAndBranchIdAndPostingKey(
                request.getBusinessId(), request.getBranchId(), request.getPostingKey().trim());
        if (repeated.isPresent()) return originalPosting(repeated.get());

        requireBranch(request.getBusinessId(), request.getBranchId());
        StockMovement original = movementRepository.findByIdAndBusinessIdAndBranchId(
                        request.getOriginalMovementId(), request.getBusinessId(), request.getBranchId())
                .orElseThrow(() -> new InventoryPostingException("Original Stock Movement was not found"));

        if (original.getSourceType() != StockSourceType.STOCK_IMPORT
                && original.getSourceType() != StockSourceType.STOCK_ADJUSTMENT
                && original.getSourceType() != StockSourceType.INVENTORY_LOSS) {
            throw new InventoryPostingException("Only Stock Import, Stock Adjustment or Inventory Loss movement may use this reversal");
        }
        if (movementRepository.existsByReversalOfMovementId(original.getId())) {
            throw new InventoryPostingException("Stock Movement has already been reversed");
        }

        BranchProductStock stock = stockRepository.findForUpdate(
                        request.getBusinessId(), request.getBranchId(), original.getProductVariantId())
                .orElseThrow(InventoryStockNotFoundException::new);

        BigDecimal inverseChange = original.getBaseQuantityChange().negate();
        BigDecimal quantityBefore = stock.getBaseQuantity();
        Instant now = Instant.now(clock);

        boolean nonSellableImportMovement = original.getSourceType() == StockSourceType.STOCK_IMPORT
                && original.getProductBatchId() != null
                && original.getQuantityBefore().compareTo(original.getQuantityAfter()) == 0
                && original.getBaseQuantityChange().signum() != 0;

        if (!nonSellableImportMovement) {
            if (inverseChange.signum() > 0) {
                stock.receive(inverseChange, original.getFinancialUnitCostSnapshot(), now);
            } else {
                if (inverseChange.abs().compareTo(stock.availableToReserve()) > 0) {
                    throw new InventoryPostingException("Reversal would consume stock reserved for an approved transfer");
                }
                try {
                    stock.removeAtPreservedCost(
                            inverseChange.abs(), original.getFinancialUnitCostSnapshot(), now);
                } catch (IllegalArgumentException ex) {
                    throw new InventoryPostingException(ex.getMessage());
                }
            }
        }
        stock = stockRepository.saveAndFlush(stock);

        ProductBatch batch = null;
        if (original.getProductBatchId() != null) {
            batch = batchRepository.findForUpdate(
                            request.getBusinessId(), request.getBranchId(), original.getProductBatchId())
                    .orElseThrow(() -> new InventoryPostingException("Original Stock Movement batch was not found"));
            ProductBatchStatus previousStatus = batch.getStatus();
            if (inverseChange.signum() < 0 && inverseChange.abs().compareTo(batch.availableToReserve()) > 0) {
                throw new InventoryPostingException("Reversal would consume batch stock reserved for an approved transfer");
            }
            try {
                batch.adjustAvailable(inverseChange, now);
                if (inverseChange.signum() > 0) {
                    batch.restoreStatusAfterReversal(previousStatus, LocalDate.now(clock), now);
                }
            } catch (IllegalArgumentException ex) {
                throw new InventoryPostingException(ex.getMessage());
            }
            batch = batchRepository.saveAndFlush(batch);
        }

        StockMovement reversal = StockMovement.change(
                request.getBusinessId(), request.getBranchId(), stock.getId(), original.getProductVariantId(),
                batch == null ? null : batch.getId(), StockMovementType.REVERSAL,
                original.getEnteredQuantity(), original.getEnteredUnitId(), original.getConversionFactor(),
                original.getBaseInventoryUnitId(), inverseChange, quantityBefore, stock.getBaseQuantity(),
                original.getFinancialUnitCostSnapshot(), original.getInventoryValueChange().negate(),
                StockSourceType.REVERSAL, request.getSourceReferenceId(), request.getSourceLineReference(),
                request.getPostingKey(), original.getId(), request.getActorType(), request.getActorId(),
                request.getReason(), request.getNotes(), now);
        reversal = movementRepository.saveAndFlush(reversal);

        return new InventoryPostingResponse(toResponse(stock), toResponse(batch), toResponse(reversal));
    }

    @Transactional
    public StockMovementResponse reverseSaleStock(SaleStockReversalRequest request) {
        Optional<StockMovement> repeated = movementRepository.findByBusinessIdAndBranchIdAndPostingKey(
                request.businessId(), request.branchId(), request.postingKey());
        if (repeated.isPresent()) return toResponse(repeated.get());
        StockMovement original = requireReversibleMovement(
                request.businessId(), request.branchId(), request.originalMovementId(), StockSourceType.SALE);
        BranchProductStock stock = stockRepository.findForUpdate(
                        request.businessId(), request.branchId(), request.productVariantId())
                .orElseThrow(InventoryStockNotFoundException::new);
        Instant now = Instant.now(clock);
        BigDecimal before = stock.getBaseQuantity();
        stock.receive(request.baseQuantity(), request.preservedFinancialCostSnapshot(), now);
        stockRepository.saveAndFlush(stock);
        for (SaleInventoryReversalBatchRequest value : request.batches() == null ? List.<SaleInventoryReversalBatchRequest>of() : request.batches()) {
            ProductBatch batch = batchRepository.findForUpdate(request.businessId(), request.branchId(), value.productBatchId())
                    .orElseThrow(() -> new InventoryPostingException("Original Sale batch was not found"));
            batch.adjustAvailable(value.baseQuantity(), now);
            batchRepository.saveAndFlush(batch);
        }
        BigDecimal inventoryValue = request.baseQuantity().multiply(request.preservedFinancialCostSnapshot())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        StockMovement reversal = StockMovement.change(
                request.businessId(), request.branchId(), stock.getId(), request.productVariantId(), null,
                StockMovementType.REVERSAL, request.baseQuantity(), request.baseInventoryUnitId(), ONE,
                request.baseInventoryUnitId(), request.baseQuantity(), before, stock.getBaseQuantity(),
                request.preservedFinancialCostSnapshot(), inventoryValue, StockSourceType.REVERSAL,
                String.valueOf(request.saleId()), String.valueOf(request.saleItemId()), request.postingKey(),
                original.getId(), InventoryActorType.OWNER, request.actorId(), request.reason(), request.notes(), now);
        return toResponse(movementRepository.saveAndFlush(reversal));
    }

    @Transactional
    public StockMovementResponse reverseSaleReturnStock(SaleReturnStockReversalRequest request) {
        Optional<StockMovement> repeated = movementRepository.findByBusinessIdAndBranchIdAndPostingKey(
                request.businessId(), request.branchId(), request.postingKey());
        if (repeated.isPresent()) return toResponse(repeated.get());
        StockMovement original = requireReversibleMovement(
                request.businessId(), request.branchId(), request.originalMovementId(), StockSourceType.SALE_RETURN);
        BranchProductStock stock = stockRepository.findForUpdate(
                        request.businessId(), request.branchId(), original.getProductVariantId())
                .orElseThrow(InventoryStockNotFoundException::new);
        Instant now = Instant.now(clock);
        BigDecimal before = stock.getBaseQuantity();
        boolean affectedSellableStock = original.getQuantityAfter().compareTo(original.getQuantityBefore()) > 0;
        BigDecimal inverseQuantity = original.getBaseQuantityChange().negate();
        BigDecimal inverseValue = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        if (affectedSellableStock) {
            try {
                inverseValue = stock.removeAtPreservedCost(
                        original.getBaseQuantityChange(), original.getFinancialUnitCostSnapshot(), now).negate();
            } catch (IllegalArgumentException ex) {
                throw new InventoryPostingException(ex.getMessage());
            }
            stockRepository.saveAndFlush(stock);
            if (original.getProductBatchId() != null) {
                ProductBatch batch = batchRepository.findForUpdate(
                                request.businessId(), request.branchId(), original.getProductBatchId())
                        .orElseThrow(() -> new InventoryPostingException("Original returned batch was not found"));
                batch.adjustAvailable(inverseQuantity, now);
                batchRepository.saveAndFlush(batch);
            }
        }
        StockMovement reversal = StockMovement.change(
                request.businessId(), request.branchId(), stock.getId(), original.getProductVariantId(),
                original.getProductBatchId(), StockMovementType.REVERSAL, original.getEnteredQuantity(),
                original.getEnteredUnitId(), original.getConversionFactor(), original.getBaseInventoryUnitId(),
                inverseQuantity, before, stock.getBaseQuantity(), original.getFinancialUnitCostSnapshot(), inverseValue,
                StockSourceType.REVERSAL, String.valueOf(request.saleReturnId()),
                String.valueOf(request.saleReturnItemId()), request.postingKey(), original.getId(),
                InventoryActorType.OWNER, request.actorId(), request.reason(), request.notes(), now);
        return toResponse(movementRepository.saveAndFlush(reversal));
    }

    private StockMovement requireReversibleMovement(Long businessId, Long branchId, Long movementId,
                                                     StockSourceType expectedSource) {
        StockMovement original = movementRepository.findByIdAndBusinessIdAndBranchId(movementId, businessId, branchId)
                .orElseThrow(() -> new InventoryPostingException("Original Stock Movement was not found"));
        if (original.getSourceType() != expectedSource) {
            throw new InventoryPostingException("Original Stock Movement source does not match reversal workflow");
        }
        if (movementRepository.existsByReversalOfMovementId(original.getId())) {
            throw new InventoryPostingException("Stock Movement has already been reversed");
        }
        return original;
    }

    private ProductBatch updateImportBatchIfRequired(StockImportPostingRequest request,
                                                     ProductVariantAccessResponse variant,
                                                     BigDecimal change,
                                                     BigDecimal landedCost,
                                                     Instant now,
                                                     ProductBatch lockedExistingBatch) {
        boolean requiresBatch = variant.trackExpiry() || variant.batchTrackingRequired();
        if (!requiresBatch) return null;

        ProductBatch batch;
        if (request.getExistingProductBatchId() != null) {
            batch = lockedExistingBatch;
            if (batch == null) {
                throw new InventoryPostingException("Stock Import batch lock is required before posting");
            }
            try {
                batch.adjustAvailable(change, now);
            } catch (IllegalArgumentException ex) {
                throw new InventoryPostingException(ex.getMessage());
            }
            return batchRepository.saveAndFlush(batch);
        }

        if (change.signum() < 0) {
            throw new InventoryPostingException("Batch-tracked stock reduction requires an existing batch");
        }

        String batchNumber = requiredText(request.getBatchNumber(),
                "Batch number is required for batch-tracked Stock Import");
        LocalDate manufacturingDate = request.getManufacturingDate();
        LocalDate expiryDate = request.getExpiryDate();

        if (variant.trackExpiry()) {
            boolean allowMissingExpiryInformation = allowMissingExpiryInformation(
                    request.getBusinessId(), request.getBranchId());
            if (!allowMissingExpiryInformation && (manufacturingDate == null || expiryDate == null)) {
                throw new InventoryPostingException(
                        "Manufacturing date and expiry date are required for expiry-controlled Stock Import");
            }
            if (manufacturingDate != null && expiryDate != null && !expiryDate.isAfter(manufacturingDate)) {
                throw new InventoryPostingException("Expiry date must be later than manufacturing date");
            }
            if (expiryDate != null && !expiryDate.isAfter(LocalDate.now(clock)) && !request.isNonSellableImport()) {
                throw new InventoryPostingException("Expired Stock Import must be posted as non-sellable inventory");
            }
        }

        ProductBatchStatus status = request.isNonSellableImport()
                ? ProductBatchStatus.EXPIRED : ProductBatchStatus.ACTIVE;
        batch = ProductBatch.create(
                request.getBusinessId(), request.getBranchId(), request.getProductVariantId(),
                null, null, batchNumber, manufacturingDate, expiryDate, change,
                request.getOriginalPurchaseUnitCost(), landedCost, status, now);
        return batchRepository.saveAndFlush(batch);
    }

    private InventoryPostingResponse originalPosting(StockMovement movement) {
        BranchProductStock stock = stockRepository.findById(movement.getBranchProductStockId())
                .orElseThrow(InventoryStockNotFoundException::new);
        ProductBatch batch = movement.getProductBatchId() == null ? null
                : batchRepository.findById(movement.getProductBatchId()).orElse(null);
        return new InventoryPostingResponse(toResponse(stock), toResponse(batch), toResponse(movement));
    }

    private ResolvedQuantity resolveQuantity(StockReceiptRequest request, ProductVariantAccessResponse variant) {
        BigDecimal enteredQuantity = request.getEnteredQuantity();
        if (enteredQuantity == null || enteredQuantity.signum() <= 0) {
            throw new InventoryPostingException("Entered quantity must be greater than zero");
        }

        if (request.getEnteredUnitId().equals(variant.baseInventoryUnitId())) {
            return new ResolvedQuantity(ONE, normalizeQuantity(enteredQuantity));
        }

        LocalDate effectiveDate = request.getTransactionDate();
        ProductUnitConversionResponse conversion = productAccessService.findEffectiveConversion(
                        request.getBusinessId(), request.getBranchId(), request.getProductVariantId(),
                        request.getEnteredUnitId(), effectiveDate)
                .orElseThrow(() -> new InventoryPostingException(
                        "No active Product Unit Conversion exists for this receipt"));

        if (!conversion.targetUnitId().equals(variant.baseInventoryUnitId())) {
            throw new InventoryPostingException("Product Unit Conversion does not target the Base Inventory Unit");
        }
        if (conversion.conversionFactor() == null || conversion.conversionFactor().signum() <= 0) {
            throw new InventoryPostingException("Product Unit Conversion factor must be greater than zero");
        }
        if (decimalPlaces(enteredQuantity) > conversion.decimalPrecision()) {
            throw new InventoryPostingException(
                    "Entered quantity uses more decimal places than the Product Unit Conversion allows");
        }

        BigDecimal baseQuantity = enteredQuantity.multiply(conversion.conversionFactor());
        return new ResolvedQuantity(conversion.conversionFactor(), normalizeQuantity(baseQuantity));
    }

    private ProductBatch createPurchaseBatchIfRequired(StockReceiptRequest request,
                                                       ProductVariantAccessResponse variant,
                                                       BigDecimal baseQuantity,
                                                       Instant now) {
        boolean requiresBatch = variant.trackExpiry() || variant.batchTrackingRequired();
        if (!requiresBatch) return null;

        String batchNumber = requiredText(request.getBatchNumber(),
                "Batch number is required for this Product Variant");
        validateTextLength(batchNumber, 100, "batchNumber");
        if (request.getSupplierId() == null || request.getSupplierId() <= 0) {
            throw new InventoryPostingException("supplierId is required for a purchase batch");
        }
        if (request.getSourcePurchaseItemId() == null || request.getSourcePurchaseItemId() <= 0) {
            throw new InventoryPostingException("sourcePurchaseItemId is required for a purchase batch");
        }
        if (request.getOriginalPurchaseUnitCost() == null) {
            throw new InventoryPostingException("originalPurchaseUnitCost is required for a purchase batch");
        }

        LocalDate manufacturingDate = request.getManufacturingDate();
        LocalDate expiryDate = request.getExpiryDate();

        if (variant.trackExpiry()) {
            boolean allowMissingExpiryInformation = allowMissingExpiryInformation(
                    request.getBusinessId(), request.getBranchId());
            if (!allowMissingExpiryInformation && manufacturingDate == null) {
                throw new InventoryPostingException(
                        "Manufacturing date is required for an expiry-controlled Product Variant");
            }
            if (!allowMissingExpiryInformation && expiryDate == null) {
                throw new InventoryPostingException(
                        "Expiry date is required for an expiry-controlled Product Variant");
            }
            if (manufacturingDate != null && expiryDate != null && !expiryDate.isAfter(manufacturingDate)) {
                throw new InventoryPostingException("Expiry date must be later than manufacturing date");
            }
            if (expiryDate != null && !expiryDate.isAfter(request.getTransactionDate())) {
                throw new InventoryPostingException("Expiry date must be later than purchase date");
            }
        }

        ProductBatchStatus status = expiryDate != null && !expiryDate.isAfter(request.getTransactionDate())
                ? ProductBatchStatus.EXPIRED
                : ProductBatchStatus.ACTIVE;

        return ProductBatch.create(
                request.getBusinessId(), request.getBranchId(), request.getProductVariantId(),
                request.getSupplierId(), request.getSourcePurchaseItemId(), batchNumber,
                manufacturingDate, expiryDate, baseQuantity, request.getOriginalPurchaseUnitCost(),
                request.getLandedBaseUnitCost(), status, now);
    }

    private boolean allowMissingExpiryInformation(Long businessId, Long branchId) {
        return branchSettingsAccessService.findByBusinessIdAndBranchId(businessId, branchId)
                .map(settings -> Boolean.TRUE.equals(settings.allowMissingExpiryInformation()))
                .orElse(false);
    }

    private void validateRequiredRequest(StockReceiptRequest request) {
        if (request == null) throw new InventoryPostingException("Stock receipt request is required");
        positiveId(request.getBusinessId(), "businessId");
        positiveId(request.getBranchId(), "branchId");
        positiveId(request.getProductVariantId(), "productVariantId");
        positiveId(request.getEnteredUnitId(), "enteredUnitId");
        positiveId(request.getActorId(), "actorId");
        if (request.getTransactionDate() == null) throw new InventoryPostingException("transactionDate is required");
        if (request.getMovementType() == null) throw new InventoryPostingException("movementType is required");
        if (request.getSourceType() == null) throw new InventoryPostingException("sourceType is required");
        if (request.getMovementType() != StockMovementType.PURCHASE_RECEIPT
                || request.getSourceType() != StockSourceType.PURCHASE) {
            throw new InventoryPostingException(
                    "Purchase receipt must use PURCHASE_RECEIPT from PURCHASE");
        }
        if (request.getActorType() == null) throw new InventoryPostingException("actorType is required");
        validateTextLength(requiredText(request.getSourceReferenceId(), "sourceReferenceId is required"),
                100, "sourceReferenceId");
        validateTextLength(requiredText(request.getSourceLineReference(), "sourceLineReference is required"),
                100, "sourceLineReference");
        validateTextLength(requiredText(request.getPostingKey(), "postingKey is required"),
                120, "postingKey");
        validateOptionalTextLength(request.getReason(), 240, "reason");
        validateOptionalTextLength(request.getNotes(), 1000, "notes");
    }

    private void validateSaleStockRequest(SaleStockPostingRequest request) {
        if (request == null) throw new InventoryPostingException("Sale stock request is required");
        positiveId(request.businessId(), "businessId");
        positiveId(request.branchId(), "branchId");
        positiveId(request.productVariantId(), "productVariantId");
        positiveId(request.enteredUnitId(), "enteredUnitId");
        positiveId(request.baseInventoryUnitId(), "baseInventoryUnitId");
        positiveId(request.saleId(), "saleId");
        positiveId(request.saleItemId(), "saleItemId");
        positiveId(request.actorId(), "actorId");
        normalizeQuantity(request.baseQuantity());
        if (request.baseQuantity().signum() <= 0) throw new InventoryPostingException("Sale quantity must be positive");
        validateTextLength(request.postingKey(), 120, "postingKey");
    }

    private void validateSaleReturnRequest(SaleReturnStockPostingRequest request) {
        if (request == null) throw new InventoryPostingException("Sale Return stock request is required");
        positiveId(request.businessId(), "businessId");
        positiveId(request.branchId(), "branchId");
        positiveId(request.productVariantId(), "productVariantId");
        positiveId(request.returnUnitId(), "returnUnitId");
        positiveId(request.baseInventoryUnitId(), "baseInventoryUnitId");
        positiveId(request.saleReturnId(), "saleReturnId");
        positiveId(request.saleReturnItemId(), "saleReturnItemId");
        positiveId(request.actorId(), "actorId");
        normalizeQuantity(request.baseQuantity());
        validateCost(request.preservedFinancialCostSnapshot(), "preservedFinancialCostSnapshot");
        validateTextLength(request.postingKey(), 120, "postingKey");
    }

    private void validatePurchaseReturnRequest(PurchaseReturnStockRequest request) {
        if (request == null) throw new InventoryPostingException("Purchase return stock request is required");
        positiveId(request.getBusinessId(), "businessId");
        positiveId(request.getBranchId(), "branchId");
        positiveId(request.getProductVariantId(), "productVariantId");
        positiveId(request.getReturnUnitId(), "returnUnitId");
        positiveId(request.getBaseInventoryUnitIdSnapshot(), "baseInventoryUnitIdSnapshot");
        positiveId(request.getActorId(), "actorId");
        if (request.getActorType() == null) throw new InventoryPostingException("actorType is required");
        if (request.getEnteredReturnQuantity() == null || request.getEnteredReturnQuantity().signum() <= 0)
            throw new InventoryPostingException("enteredReturnQuantity must be greater than zero");
        if (request.getConversionFactorSnapshot() == null || request.getConversionFactorSnapshot().signum() <= 0)
            throw new InventoryPostingException("conversionFactorSnapshot must be greater than zero");
        validateTextLength(requiredText(request.getSourceReferenceId(), "sourceReferenceId is required"), 100, "sourceReferenceId");
        validateTextLength(requiredText(request.getSourceLineReference(), "sourceLineReference is required"), 100, "sourceLineReference");
        validateTextLength(requiredText(request.getPostingKey(), "postingKey is required"), 120, "postingKey");
    }

    private void validateStockImportRequest(StockImportPostingRequest request) {
        if (request == null) throw new InventoryPostingException("Stock Import posting request is required");
        positiveId(request.getBusinessId(), "businessId");
        positiveId(request.getBranchId(), "branchId");
        positiveId(request.getProductVariantId(), "productVariantId");
        positiveId(request.getEnteredUnitId(), "enteredUnitId");
        positiveId(request.getBaseInventoryUnitIdSnapshot(), "baseInventoryUnitIdSnapshot");
        positiveId(request.getActorId(), "actorId");
        if (request.getActorType() == null) throw new InventoryPostingException("actorType is required");
        if (request.getEnteredQuantity() == null || request.getEnteredQuantity().signum() < 0)
            throw new InventoryPostingException("enteredQuantity must not be negative");
        if (request.getConversionFactorSnapshot() == null || request.getConversionFactorSnapshot().signum() <= 0)
            throw new InventoryPostingException("conversionFactorSnapshot must be greater than zero");
        if (request.getBaseQuantityChange() == null)
            throw new InventoryPostingException("baseQuantityChange is required");
        if (request.getLandedBaseUnitCost() != null) validateCost(request.getLandedBaseUnitCost(), "landedBaseUnitCost");
        if (request.getOriginalPurchaseUnitCost() != null) validateCost(request.getOriginalPurchaseUnitCost(), "originalPurchaseUnitCost");
        validateTextLength(requiredText(request.getSourceReferenceId(), "sourceReferenceId is required"), 100, "sourceReferenceId");
        validateTextLength(requiredText(request.getSourceLineReference(), "sourceLineReference is required"), 100, "sourceLineReference");
        validateTextLength(requiredText(request.getPostingKey(), "postingKey is required"), 120, "postingKey");
    }

    private void validateReversalRequest(StockMovementReversalRequest request) {
        if (request == null) throw new InventoryPostingException("Stock Movement reversal request is required");
        positiveId(request.getBusinessId(), "businessId");
        positiveId(request.getBranchId(), "branchId");
        positiveId(request.getOriginalMovementId(), "originalMovementId");
        positiveId(request.getActorId(), "actorId");
        if (request.getActorType() == null) throw new InventoryPostingException("actorType is required");
        validateTextLength(requiredText(request.getSourceReferenceId(), "sourceReferenceId is required"), 100, "sourceReferenceId");
        validateTextLength(requiredText(request.getSourceLineReference(), "sourceLineReference is required"), 100, "sourceLineReference");
        validateTextLength(requiredText(request.getPostingKey(), "postingKey is required"), 120, "postingKey");
    }

    private void requireBranch(Long businessId, Long branchId) {
        BranchAccessResponse branch = branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId)
                .orElseThrow(InventoryAccessDeniedException::new);
        if (!branch.businessId().equals(businessId)) throw new InventoryAccessDeniedException();
    }

    private ProductVariantAccessResponse requireVariant(Long businessId, Long branchId, Long variantId) {
        return productAccessService.findActiveVariantForBranch(businessId, branchId, variantId)
                .orElseThrow(InventoryAccessDeniedException::new);
    }

    private void ensureBaseUnit(BranchProductStock stock, Long baseInventoryUnitId) {
        if (!stock.getBaseInventoryUnitId().equals(baseInventoryUnitId)) {
            throw new InventoryPostingException("Stored base inventory unit does not match the Product Variant");
        }
    }

    private void validateTextLength(String value, int maxLength, String field) {
        if (value.length() > maxLength) {
            throw new InventoryPostingException(field + " exceeds the maximum length of " + maxLength);
        }
    }

    private void validateOptionalTextLength(String value, int maxLength, String field) {
        if (value != null && !value.isBlank()) validateTextLength(value.trim(), maxLength, field);
    }

    private void positiveId(Long value, String field) {
        if (value == null || value <= 0) throw new InventoryPostingException(field + " must be a positive id");
    }

    private void validateCost(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new InventoryPostingException(field + " must not be negative");
        }
    }

    private String requiredText(String value, String message) {
        if (value == null || value.isBlank()) throw new InventoryPostingException(message);
        return value.trim();
    }

    private BigDecimal normalizeQuantity(BigDecimal value) {
        if (value == null) throw new InventoryPostingException("Base Quantity is required");
        try {
            return value.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new InventoryPostingException("Converted Base Quantity exceeds the supported quantity precision");
        }
    }

    private BigDecimal normalizeSignedQuantity(BigDecimal value) {
        return normalizeQuantity(value);
    }

    private int decimalPlaces(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return Math.max(normalized.scale(), 0);
    }

    private BranchProductStockResponse toResponse(BranchProductStock stock) {
        return new BranchProductStockResponse(
                stock.getId(), stock.getBusinessId(), stock.getBranchId(), stock.getProductVariantId(),
                stock.getBaseInventoryUnitId(), stock.getBaseQuantity(), stock.getReservedBaseQuantity(), stock.availableToReserve(), stock.getWeightedAverageCost(),
                stock.getInventoryValue(), stock.getUpdatedAt());
    }

    private ProductBatchResponse toResponse(ProductBatch batch) {
        if (batch == null) return null;
        return new ProductBatchResponse(
                batch.getId(), batch.getBusinessId(), batch.getBranchId(), batch.getProductVariantId(),
                batch.getSupplierId(), batch.getSourcePurchaseItemId(), batch.getBatchNumber(),
                batch.getManufacturingDate(), batch.getExpiryDate(), batch.getReceivedBaseQuantity(),
                batch.getAvailableBaseQuantity(), batch.getReservedBaseQuantity(), batch.availableToReserve(), batch.getOriginalPurchaseUnitCost(),
                batch.getAllocatedLandedUnitCost(), batch.getStatus(), batch.getCreatedAt());
    }

    private StockMovementResponse toResponse(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(), movement.getBusinessId(), movement.getBranchId(),
                movement.getBranchProductStockId(), movement.getProductVariantId(), movement.getProductBatchId(),
                movement.getMovementType(), movement.getEnteredQuantity(), movement.getEnteredUnitId(),
                movement.getConversionFactor(), movement.getBaseInventoryUnitId(), movement.getBaseQuantityChange(),
                movement.getQuantityBefore(), movement.getQuantityAfter(), movement.getFinancialUnitCostSnapshot(),
                movement.getInventoryValueChange(), movement.getSourceType(), movement.getSourceReferenceId(),
                movement.getSourceLineReference(), movement.getPostingKey(), movement.getReversalOfMovementId(),
                movement.getReason(), movement.getNotes(), movement.getPostedByActorType(),
                movement.getPostedByActorId(), movement.getPostedAt());
    }

    private record ResolvedQuantity(BigDecimal conversionFactor, BigDecimal baseQuantity) {
    }
}
