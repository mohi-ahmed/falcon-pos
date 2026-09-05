package com.spark.falcon.inventory.service;

import com.spark.falcon.inventory.dto.BranchProductStockResponse;
import com.spark.falcon.inventory.dto.ProductBatchResponse;
import com.spark.falcon.inventory.dto.StockMovementResponse;
import com.spark.falcon.inventory.dto.InventorySellableStockResponse;
import com.spark.falcon.inventory.dto.StockMovementFilter;
import com.spark.falcon.inventory.entity.ProductBatchStatus;
import com.spark.falcon.inventory.entity.BranchProductStock;
import com.spark.falcon.inventory.entity.ProductBatch;
import com.spark.falcon.inventory.entity.StockMovement;
import com.spark.falcon.inventory.repository.BranchProductStockRepository;
import com.spark.falcon.inventory.repository.ProductBatchRepository;
import com.spark.falcon.inventory.repository.StockMovementRepository;
import com.spark.falcon.inventory.repository.InventorySearchRepository;
import com.spark.falcon.product.service.ProductAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Set;
import java.util.HashSet;
import com.spark.falcon.inventory.entity.StockMovementType;
import com.spark.falcon.inventory.entity.StockSourceType;

@Service
@RequiredArgsConstructor
public class InventoryQueryService {

    private final BranchProductStockRepository stockRepository;
    private final ProductBatchRepository batchRepository;
    private final StockMovementRepository movementRepository;
    private final InventorySearchRepository searchRepository;
    private final ProductAccessService productAccessService;

    public Optional<InventorySellableStockResponse> findSellableStock(
            Long businessId, Long branchId, Long productVariantId,
            boolean batchControlled, boolean expiryControlled, LocalDate saleDate) {
        Optional<BranchProductStock> stock = stockRepository
                .findByBusinessIdAndBranchIdAndProductVariantId(businessId, branchId, productVariantId);
        if (stock.isEmpty()) return Optional.empty();
        BigDecimal sellable = stock.get().availableToReserve();
        if (batchControlled) {
            sellable = batchRepository
                    .findByBusinessIdAndBranchIdAndProductVariantIdOrderByExpiryDateAscIdAsc(
                            businessId, branchId, productVariantId).stream()
                    .filter(batch -> batch.getStatus() == ProductBatchStatus.ACTIVE)
                    .filter(batch -> batch.getAvailableBaseQuantity().signum() > 0)
                    .filter(batch -> !expiryControlled || batch.getExpiryDate() == null
                            || !batch.getExpiryDate().isBefore(saleDate))
                    .map(ProductBatch::availableToReserve)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return Optional.of(new InventorySellableStockResponse(
                productVariantId, sellable, stock.get().getWeightedAverageCost()));
    }

    public Optional<BranchProductStockResponse> findStock(Long businessId, Long branchId, Long productVariantId) {
        return stockRepository.findByBusinessIdAndBranchIdAndProductVariantId(businessId, branchId, productVariantId)
                .map(this::toResponse);
    }

    public Optional<ProductBatchResponse> findBatch(Long businessId, Long branchId, Long batchId) {
        return batchRepository.findByIdAndBusinessIdAndBranchId(batchId, businessId, branchId)
                .map(this::toResponse);
    }

    public Optional<ProductBatchResponse> findBatchByNumber(
            Long businessId, Long branchId, Long productVariantId, String batchNumber) {
        if (batchNumber == null || batchNumber.isBlank()) return Optional.empty();
        return batchRepository.findByBusinessIdAndBranchIdAndProductVariantIdAndBatchNumberIgnoreCase(
                        businessId, branchId, productVariantId, batchNumber.trim())
                .map(this::toResponse);
    }

    public boolean hasPostedStockHistory(Long businessId, Long branchId, Long productVariantId) {
        return movementRepository.existsByBusinessIdAndBranchIdAndProductVariantId(
                businessId, branchId, productVariantId);
    }

    public Optional<StockMovementResponse> findMovement(Long businessId, Long branchId, Long movementId) {
        return movementRepository.findByIdAndBusinessIdAndBranchId(movementId, businessId, branchId)
                .map(this::toResponse);
    }

    public List<ProductBatchResponse> findBatches(Long businessId, Long branchId, Long productVariantId) {
        return batchRepository.findByBusinessIdAndBranchIdAndProductVariantIdOrderByExpiryDateAscIdAsc(
                        businessId, branchId, productVariantId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<BranchProductStockResponse> findBranchStocks(Long businessId, Long branchId) {
        return stockRepository.findByBusinessIdAndBranchIdOrderByProductVariantIdAsc(businessId, branchId).stream()
                .map(this::toResponse).toList();
    }

    public List<ProductBatchResponse> findBranchBatches(Long businessId, Long branchId) {
        return batchRepository.findByBusinessIdAndBranchIdOrderByExpiryDateAscIdAsc(businessId, branchId).stream()
                .map(this::toResponse).toList();
    }

    public List<StockMovementResponse> findBranchMovements(Long businessId, Long branchId) {
        return movementRepository.findByBusinessIdAndBranchIdOrderByPostedAtDescIdDesc(businessId, branchId).stream()
                .map(this::toResponse).toList();
    }

    public List<StockMovementResponse> filterMovements(Long businessId,Long branchId,Long movementId,
            Long productVariantId,Long batchId,StockMovementType movementType,StockSourceType sourceType,
            Long userId,Instant from,Instant to,Boolean reversal,String search){
        String q=search==null?"":search.trim().toLowerCase();
        Set<Long> matchingVariants=new HashSet<>();Set<Long> matchingBatches=new HashSet<>();
        if(!q.isEmpty()){
            productAccessService.findActiveVariantsForBranch(businessId,branchId).stream()
                    .filter(v->contains(v.productName(),q)||contains(v.referenceCode(),q)||contains(v.variantName(),q)||contains(v.sku(),q))
                    .map(v->v.variantId()).forEach(matchingVariants::add);
            productAccessService.resolveActiveBarcode(businessId,branchId,search.trim()).ifPresent(v->matchingVariants.add(v.variant().variantId()));
            batchRepository.findByBusinessIdAndBranchIdOrderByExpiryDateAscIdAsc(businessId,branchId).stream()
                    .filter(b->contains(b.getBatchNumber(),q)).map(ProductBatch::getId).forEach(matchingBatches::add);
        }
        return movementRepository.findByBusinessIdAndBranchIdOrderByPostedAtDescIdDesc(businessId,branchId).stream()
                .filter(m->movementId==null||m.getId().equals(movementId))
                .filter(m->productVariantId==null||m.getProductVariantId().equals(productVariantId))
                .filter(m->batchId==null||batchId.equals(m.getProductBatchId()))
                .filter(m->movementType==null||m.getMovementType()==movementType)
                .filter(m->sourceType==null||m.getSourceType()==sourceType)
                .filter(m->userId==null||m.getPostedByActorId().equals(userId))
                .filter(m->from==null||!m.getPostedAt().isBefore(from)).filter(m->to==null||!m.getPostedAt().isAfter(to))
                .filter(m->reversal==null||(reversal?m.getReversalOfMovementId()!=null:m.getReversalOfMovementId()==null))
                .filter(m->q.isEmpty()||String.valueOf(m.getId()).contains(q)||m.getSourceReferenceId().toLowerCase().contains(q)
                        ||m.getPostingKey().toLowerCase().contains(q)||String.valueOf(m.getReversalOfMovementId()).contains(q)
                        ||matchingVariants.contains(m.getProductVariantId())||matchingBatches.contains(m.getProductBatchId()))
                .map(this::toResponse).toList();
    }
    private boolean contains(String value,String q){return value!=null&&value.toLowerCase().contains(q);}


    public Page<StockMovementResponse> searchMovements(Long businessId, Long branchId, StockMovementFilter filter, Pageable pageable) {
        StockMovementFilter f = filter == null
                ? new StockMovementFilter(null, null, null, null, null, null, null, null, null, null, null, null)
                : filter;
        String query = f.query() == null || f.query().isBlank() ? null : f.query().trim();
        Long variantId = f.productVariantId();
        if (query != null) {
            var barcode = productAccessService.resolveActiveBarcode(businessId, branchId, query);
            if (barcode.isPresent() && variantId == null) {
                variantId = barcode.get().variant().variantId();
                query = null;
            }
        }
        Long queryMovementId = null;
        if (query != null) {
            try { queryMovementId = Long.valueOf(query); } catch (NumberFormatException ignored) { }
        }
        return searchRepository.searchMovements(businessId, branchId, f.movementId(), f.productId(), variantId,
                f.batchId(), f.movementType(), f.sourceType(), f.userId(), f.from(), f.to(), f.reversal(),
                f.reversalReferenceId(), queryMovementId, query, pageable).map(this::toResponse);
    }

    public Optional<StockMovementResponse> findLinkedReversal(Long originalMovementId) {
        return movementRepository.findFirstByReversalOfMovementId(originalMovementId).map(this::toResponse);
    }

    public List<StockMovementResponse> findMovements(Long businessId, Long branchId, Long productVariantId) {
        return movementRepository.findByBusinessIdAndBranchIdAndProductVariantIdOrderByPostedAtDescIdDesc(
                        businessId, branchId, productVariantId).stream()
                .map(this::toResponse)
                .toList();
    }

    private BranchProductStockResponse toResponse(BranchProductStock stock) {
        return new BranchProductStockResponse(
                stock.getId(), stock.getBusinessId(), stock.getBranchId(), stock.getProductVariantId(),
                stock.getBaseInventoryUnitId(), stock.getBaseQuantity(), stock.getReservedBaseQuantity(), stock.availableToReserve(), stock.getWeightedAverageCost(),
                stock.getInventoryValue(), stock.getUpdatedAt());
    }

    private ProductBatchResponse toResponse(ProductBatch batch) {
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
}
