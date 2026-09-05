package com.spark.falcon.sale.service;

import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.customer.dto.CustomerAccessResponse;
import com.spark.falcon.customer.service.CustomerAccessService;
import com.spark.falcon.inventory.dto.*;
import com.spark.falcon.inventory.service.InventoryPostingService;
import com.spark.falcon.payment.dto.CustomerRefundCommand;
import com.spark.falcon.payment.dto.PaymentResponse;
import com.spark.falcon.payment.service.PaymentPostingService;
import com.spark.falcon.payment.service.SalePaymentReadService;
import com.spark.falcon.sale.dto.*;
import com.spark.falcon.sale.entity.*;
import com.spark.falcon.sale.exception.*;
import com.spark.falcon.sale.repository.*;
import com.spark.falcon.identity.security.CurrentActorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleService {

    private static final int MONEY_SCALE = 4;
    private static final int QUANTITY_SCALE = 8;

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SaleItemBatchAllocationRepository allocationRepository;
    private final SaleReturnRepository saleReturnRepository;
    private final SaleReturnItemRepository saleReturnItemRepository;
    private final SaleAuditEventRepository auditRepository;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final CustomerAccessService customerAccessService;
    private final InventoryPostingService inventoryPostingService;
    private final PaymentPostingService paymentPostingService;
    private final SalePaymentReadService salePaymentReadService;
    private final CurrentActorService currentActorService;
    private final Clock clock;

    public Page<SaleResponse> findSellList(Long ownerId,
                                           Long branchId,
                                           Long customerId,
                                           String filter,
                                           String keyword,
                                           Pageable pageable) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        BranchAccessResponse branch = requireBranch(business.businessId(), branchId);
        String resolvedFilter = normalizeSellFilter(filter);
        Instant now = now();
        ZoneId zone = zone(branch);
        LocalDate today = LocalDate.ofInstant(now, zone);
        Instant fromTime = today.atStartOfDay(zone).toInstant();
        Instant toTime = today.plusDays(1).atStartOfDay(zone).toInstant();
        String search = normalizeSearch(keyword);
        Page<Sale> page = search == null
                ? saleRepository.searchSellListWithoutKeyword(
                        business.businessId(), branchId, customerId, resolvedFilter, fromTime, toTime, pageable)
                : saleRepository.searchSellList(
                        business.businessId(), branchId, customerId, resolvedFilter, fromTime, toTime, search, pageable);
        Map<Long, CustomerAccessResponse> customers = customersForSales(business.businessId(), page.getContent());
        return page.map(value -> saleResponse(value, customerName(customers, value.getCustomerId())));
    }

    public SaleResponse findDetails(Long ownerId, Long branchId, Long saleId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        Sale sale = requireSale(business.businessId(), branchId, saleId);
        String customerName = customerAccessService.findByBusinessIdAndCustomerId(
                        business.businessId(), sale.getCustomerId())
                .map(CustomerAccessResponse::name).orElse(null);
        return saleResponse(sale, customerName);
    }

    public Page<SaleReturnResponse> findReturnList(Long ownerId,
                                                   Long branchId,
                                                   Long customerId,
                                                   SaleReturnStatus status,
                                                   String keyword,
                                                   Pageable pageable) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        String search = normalizeSearch(keyword);
        Page<SaleReturn> page = search == null
                ? saleReturnRepository.searchWithoutKeyword(
                        business.businessId(), branchId, customerId, status, pageable)
                : saleReturnRepository.search(
                        business.businessId(), branchId, customerId, status, search, pageable);
        Map<Long, CustomerAccessResponse> customers = customerAccessService.findByBusinessIdAndCustomerIds(
                business.businessId(), page.getContent().stream().map(SaleReturn::getCustomerId).toList());
        return page.map(value -> returnResponse(value, customerName(customers, value.getCustomerId())));
    }

    public SaleReturnResponse findReturnDetails(Long ownerId, Long branchId, Long returnId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        SaleReturn saleReturn = saleReturnRepository
                .findByIdAndBusinessIdAndBranchId(returnId, business.businessId(), branchId)
                .orElseThrow(SaleReturnNotFoundException::new);
        String customerName = customerAccessService.findByBusinessIdAndCustomerId(
                        business.businessId(), saleReturn.getCustomerId())
                .map(CustomerAccessResponse::name).orElse(null);
        return returnResponse(saleReturn, customerName);
    }

    public Page<SaleLogResponse> findSellLog(Long ownerId, Long branchId, Pageable pageable) {
        return findSellLog(ownerId, branchId, null, pageable);
    }

    public Page<SaleLogResponse> findSellLog(Long ownerId, Long branchId, String keyword, Pageable pageable) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        String search = normalizeSearch(keyword);
        Page<SaleAuditEvent> page;
        if (search == null) {
            page = auditRepository.findByBusinessIdAndBranchId(
                    business.businessId(), branchId, pageable);
        } else {
            List<Long> paymentSaleIds = salePaymentReadService.findSaleIdsByPaymentMethodKeyword(
                    business.businessId(), branchId, search);
            page = auditRepository.search(
                    business.businessId(), branchId, search,
                    paymentSaleIds.isEmpty() ? List.of(-1L) : paymentSaleIds, pageable);
        }

        List<Long> saleIds = page.getContent().stream()
                .map(SaleAuditEvent::getSaleId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Sale> sales = saleIds.isEmpty()
                ? Map.of()
                : saleRepository.findByBusinessIdAndBranchIdAndIdIn(
                        business.businessId(), branchId, saleIds).stream()
                .collect(Collectors.toUnmodifiableMap(Sale::getId, Function.identity()));
        Map<Long, CustomerAccessResponse> customers = customerAccessService.findByBusinessIdAndCustomerIds(
                business.businessId(), sales.values().stream().map(Sale::getCustomerId).toList());
        Map<Long, String> paymentMethods = salePaymentReadService.paymentMethodSummaryForSales(
                business.businessId(), branchId, saleIds);

        return page.map(value -> {
            Sale sale = sales.get(value.getSaleId());
            Long customerId = sale == null ? null : sale.getCustomerId();
            return new SaleLogResponse(
                    value.getId(), value.getCreatedAt(), value.getAction(), value.getSaleId(),
                    customerId, customerName(customers, customerId), value.getActorId(),
                    sale == null ? null : sale.getTotalPayable(), paymentMethods.get(value.getSaleId()),
                    value.getDetails());
        });
    }

    @Transactional
    public SaleReturnResponse createReturn(Long ownerId, Long saleId, SaleReturnRequest request) {
        validateReturnRequest(request);
        BusinessAccessResponse business = requireBusiness(ownerId);
        BranchAccessResponse branch = requireBranch(business.businessId(), request.getBranchId());

        SaleReturn repeated = saleReturnRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                business.businessId(), branch.branchId(), request.getIdempotencyKey().trim()).orElse(null);
        if (repeated != null) return returnResponse(repeated);

        Sale sale = saleRepository.findForUpdate(business.businessId(), branch.branchId(), saleId)
                .orElseThrow(SaleNotFoundException::new);
        if (!sale.isReturnEligible()) throw new SaleStateException("Sale is not eligible for a Sales Return");

        List<PreparedReturnItem> prepared = prepareReturnItems(sale, request.getItems());
        boolean willFullyReturnSale = willFullyReturnSale(sale, prepared);
        if (willFullyReturnSale) {
            prepared = adjustFullReturnAmount(sale, prepared);
        }
        BigDecimal totalReturnAmount = prepared.stream()
                .map(PreparedReturnItem::returnAmount)
                .reduce(zeroMoney(), BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (totalReturnAmount.signum() <= 0) throw new SaleValidationException("Sales Return amount must be greater than zero");

        Instant now = now();
        SaleReturn saleReturn = SaleReturn.draft(
                business.businessId(), branch.branchId(), sale.getId(), sale.getCustomerId(),
                buildReturnReference(sale.getId(), now), request.getSettlementType(), totalReturnAmount,
                request.getIdempotencyKey(), request.getNotes(), actor(ownerId), now);
        saleReturn = saleReturnRepository.saveAndFlush(saleReturn);
        auditRepository.saveAndFlush(SaleAuditEvent.record(
                business.businessId(), branch.branchId(), sale.getId(), SaleAuditAction.SALE_RETURN_DRAFT_CREATED,
                actor(ownerId), "Sales Return " + saleReturn.getReferenceNumber() + " created", now));

        LocalDate returnDate = LocalDate.ofInstant(now, zone(branch));
        List<SaleReturnItem> persistedReturnItems = new ArrayList<>();
        for (PreparedReturnItem preparedItem : prepared) {
            SaleItem saleItem = preparedItem.saleItem();
            try {
                saleItem.registerReturn(preparedItem.baseQuantity());
            } catch (IllegalArgumentException ex) {
                throw new SaleStateException(ex.getMessage());
            }
            saleItemRepository.save(saleItem);

            if (preparedItem.batchParts().isEmpty()) {
                SaleReturnItem returnItem = createReturnItem(
                        saleReturn.getId(), saleItem, null, preparedItem.baseQuantity(),
                        preparedItem.returnAmount(), preparedItem.request());
                returnItem = saleReturnItemRepository.saveAndFlush(returnItem);
                SaleReturnStockPostingResponse stock = inventoryPostingService.postSaleReturnStock(
                        returnStockRequest(business.businessId(), branch.branchId(), saleReturn, returnItem,
                                returnDate, actor(ownerId), request.getNotes()));
                returnItem.attachStockMovement(stock.movement().getId());
                persistedReturnItems.add(saleReturnItemRepository.saveAndFlush(returnItem));
            } else {
                BigDecimal remainingAmount = preparedItem.returnAmount();
                for (int index = 0; index < preparedItem.batchParts().size(); index++) {
                    BatchPart part = preparedItem.batchParts().get(index);
                    try {
                        part.allocation().registerReturn(part.baseQuantity());
                    } catch (IllegalArgumentException ex) {
                        throw new SaleStateException(ex.getMessage());
                    }
                    allocationRepository.save(part.allocation());
                    BigDecimal partAmount = index == preparedItem.batchParts().size() - 1
                            ? remainingAmount
                            : preparedItem.returnAmount().multiply(part.baseQuantity())
                                    .divide(preparedItem.baseQuantity(), MONEY_SCALE, RoundingMode.HALF_UP);
                    remainingAmount = remainingAmount.subtract(partAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                    SaleReturnItem returnItem = createReturnItem(
                            saleReturn.getId(), saleItem, part.allocation(), part.baseQuantity(),
                            partAmount, preparedItem.request());
                    returnItem = saleReturnItemRepository.saveAndFlush(returnItem);
                    SaleReturnStockPostingResponse stock = inventoryPostingService.postSaleReturnStock(
                            returnStockRequest(business.businessId(), branch.branchId(), saleReturn, returnItem,
                                    returnDate, actor(ownerId), request.getNotes()));
                    returnItem.attachStockMovement(stock.movement().getId());
                    persistedReturnItems.add(saleReturnItemRepository.saveAndFlush(returnItem));
                }
            }
        }
        saleItemRepository.flush();
        allocationRepository.flush();

        boolean fullyReturned = willFullyReturnSale;
        Sale.ReturnChange returnChange;
        try {
            returnChange = sale.applyReturn(totalReturnAmount, fullyReturned, now);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new SaleStateException(ex.getMessage());
        }
        BigDecimal excess = totalReturnAmount.subtract(returnChange.dueReduction())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        Settlement settlement = settleReturnExcess(
                business.businessId(), branch, sale, saleReturn, request, ownerId,
                returnChange.dueReduction(), excess);
        try {
            saleReturn.confirm(returnChange.dueReduction(), settlement.refundAmount(),
                    settlement.customerCreditAmount(), settlement.refundPaymentId(), now);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new SaleStateException(ex.getMessage());
        }
        saleRepository.saveAndFlush(sale);
        saleReturn = saleReturnRepository.saveAndFlush(saleReturn);
        auditRepository.saveAndFlush(SaleAuditEvent.record(
                business.businessId(), branch.branchId(), sale.getId(), SaleAuditAction.SALE_RETURN_CONFIRMED,
                actor(ownerId), "Sales Return " + saleReturn.getReferenceNumber() + " confirmed", now));
        return returnResponse(saleReturn, persistedReturnItems);
    }

    @Transactional
    public SaleReturnResponse reverseReturn(Long ownerId,
                                            Long branchId,
                                            Long returnId,
                                            SaleCorrectionRequest request) {
        validateCorrectionRequest(request);
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);

        SaleReturn saleReturn = saleReturnRepository.findForUpdate(business.businessId(), branchId, returnId)
                .orElseThrow(SaleReturnNotFoundException::new);
        if (saleReturn.getStatus() == SaleReturnStatus.REVERSED) return returnResponse(saleReturn);
        if (saleReturn.getStatus() != SaleReturnStatus.CONFIRMED) {
            throw new SaleStateException("Only a Confirmed Sales Return can be reversed");
        }
        Sale sale = saleRepository.findForUpdate(business.businessId(), branchId, saleReturn.getSaleId())
                .orElseThrow(SaleNotFoundException::new);

        if (saleReturn.getRefundPaymentId() != null) {
            paymentPostingService.reversePayment(
                    ownerId, branchId, saleReturn.getRefundPaymentId(), request.getReason(),
                    limitedKey("SR-PAY-REV:" + saleReturn.getId() + ":" + request.getIdempotencyKey()));
        }

        List<SaleReturnItem> returnItems = saleReturnItemRepository.findBySaleReturnIdOrderByIdAsc(returnId);
        for (SaleReturnItem returnItem : returnItems) {
            if (returnItem.getStockMovementId() == null) {
                throw new SaleStateException("Sales Return Item is missing its Stock Movement");
            }
            inventoryPostingService.reverseSaleReturnStock(new SaleReturnStockReversalRequest(
                    business.businessId(), branchId, saleReturn.getId(), returnItem.getId(),
                    returnItem.getStockMovementId(),
                    limitedKey("SR-STOCK-REV:" + returnItem.getId() + ":" + request.getIdempotencyKey()),
                    actor(ownerId), request.getReason(), saleReturn.getNotes()));

            SaleItem saleItem = saleItemRepository.findForUpdate(sale.getId(), returnItem.getSaleItemId())
                    .orElseThrow(() -> new SaleStateException("Original Sale Item was not found"));
            try {
                saleItem.reverseReturn(returnItem.getBaseQuantity());
            } catch (IllegalArgumentException ex) {
                throw new SaleStateException(ex.getMessage());
            }
            saleItemRepository.save(saleItem);

            if (returnItem.getSaleItemBatchAllocationId() != null) {
                SaleItemBatchAllocation allocation = allocationRepository
                        .findForUpdateBySaleItemId(saleItem.getId()).stream()
                        .filter(value -> value.getId().equals(returnItem.getSaleItemBatchAllocationId()))
                        .findFirst()
                        .orElseThrow(() -> new SaleStateException("Original Sale batch allocation was not found"));
                try {
                    allocation.reverseReturn(returnItem.getBaseQuantity());
                } catch (IllegalArgumentException ex) {
                    throw new SaleStateException(ex.getMessage());
                }
                allocationRepository.save(allocation);
            }
        }
        saleItemRepository.flush();
        allocationRepository.flush();

        Long reversedReturnId = saleReturn.getId();
        boolean otherConfirmedReturns = saleReturnRepository.findBySaleIdAndStatusOrderByCreatedAtAsc(
                        sale.getId(), SaleReturnStatus.CONFIRMED).stream()
                .anyMatch(value -> !value.getId().equals(reversedReturnId));
        try {
            sale.reverseReturn(saleReturn.getTotalReturnAmount(), otherConfirmedReturns, now());
            saleReturn.markReversed(actor(ownerId), request.getReason(), now());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new SaleStateException(ex.getMessage());
        }
        saleRepository.saveAndFlush(sale);
        saleReturn = saleReturnRepository.saveAndFlush(saleReturn);
        auditRepository.saveAndFlush(SaleAuditEvent.record(
                business.businessId(), branchId, sale.getId(), SaleAuditAction.SALE_RETURN_REVERSED,
                actor(ownerId), "Sales Return " + saleReturn.getReferenceNumber() + " reversed. Reason: "
                        + request.getReason().trim(), now()));
        return returnResponse(saleReturn);
    }

    @Transactional
    public SaleResponse voidSale(Long ownerId, Long branchId, Long saleId, SaleCorrectionRequest request) {
        return correctConfirmedSale(ownerId, branchId, saleId, request, true);
    }

    @Transactional
    public SaleResponse reverseSale(Long ownerId, Long branchId, Long saleId, SaleCorrectionRequest request) {
        return correctConfirmedSale(ownerId, branchId, saleId, request, false);
    }

    @Transactional
    public void deleteDraftOrHeld(Long ownerId, Long branchId, Long saleId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        Sale sale = saleRepository.findForUpdate(business.businessId(), branchId, saleId)
                .orElseThrow(SaleNotFoundException::new);
        if (!sale.isDraftOrHeld()) {
            throw new SaleStateException("Only Draft or Held Sales may be deleted or cancelled");
        }
        List<SaleItem> items = saleItemRepository.findBySaleIdOrderByIdAsc(saleId);
        if (items.stream().anyMatch(item -> item.getStockMovementId() != null)) {
            throw new SaleStateException("A Sale with posted inventory cannot be deleted");
        }
        for (SaleItem item : items) {
            allocationRepository.deleteAll(allocationRepository.findBySaleItemIdOrderByIdAsc(item.getId()));
        }
        saleItemRepository.deleteAll(items);
        auditRepository.deleteAll(auditRepository.findBySaleIdOrderByCreatedAtAscIdAsc(saleId));
        saleRepository.delete(sale);
    }

    private SaleResponse correctConfirmedSale(Long ownerId,
                                              Long branchId,
                                              Long saleId,
                                              SaleCorrectionRequest request,
                                              boolean voidSale) {
        validateCorrectionRequest(request);
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        Sale sale = saleRepository.findForUpdate(business.businessId(), branchId, saleId)
                .orElseThrow(SaleNotFoundException::new);
        if (voidSale && sale.getStatus() == SaleStatus.VOIDED) return saleResponse(sale);
        if (!voidSale && sale.getStatus() == SaleStatus.REVERSED) return saleResponse(sale);
        if (sale.getPaidAmount().signum() > 0) {
            throw new SaleStateException("Reverse confirmed Customer Payments before voiding or reversing the Sale");
        }
        if (sale.getReturnedAmount().signum() > 0) {
            throw new SaleStateException("Reverse confirmed Sales Returns before correcting the original Sale");
        }
        if (sale.getStatus() != SaleStatus.CONFIRMED) {
            throw new SaleStateException("Only a Confirmed Sale can be voided or reversed");
        }

        List<SaleItem> items = saleItemRepository.findBySaleIdOrderByIdAsc(saleId);
        for (SaleItem item : items) {
            if (item.getStockMovementId() == null || item.getWeightedAverageCostSnapshot() == null) {
                throw new SaleStateException("Sale Item is missing its confirmed inventory cost snapshot");
            }
            List<SaleInventoryReversalBatchRequest> batches = allocationRepository
                    .findBySaleItemIdOrderByIdAsc(item.getId()).stream()
                    .map(value -> new SaleInventoryReversalBatchRequest(
                            value.getProductBatchId(), value.getBaseQuantity()))
                    .toList();
            inventoryPostingService.reverseSaleStock(new SaleStockReversalRequest(
                    business.businessId(), branchId, saleId, item.getId(), item.getStockMovementId(),
                    item.getProductVariantId(), item.getBaseInventoryUnitId(), item.getBaseQuantity(),
                    item.getWeightedAverageCostSnapshot(), batches,
                    limitedKey((voidSale ? "SALE-VOID:" : "SALE-REV:") + item.getId() + ":" + request.getIdempotencyKey()),
                    actor(ownerId), request.getReason(), sale.getNotes()));
        }

        try {
            if (voidSale) sale.voidSale(actor(ownerId), request.getReason(), now());
            else sale.reverseSale(actor(ownerId), request.getReason(), now());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new SaleStateException(ex.getMessage());
        }
        sale = saleRepository.saveAndFlush(sale);
        auditRepository.saveAndFlush(SaleAuditEvent.record(
                business.businessId(), branchId, saleId,
                voidSale ? SaleAuditAction.SALE_VOIDED : SaleAuditAction.SALE_REVERSED,
                actor(ownerId), (voidSale ? "Sale voided. Reason: " : "Sale reversed. Reason: ")
                        + request.getReason().trim(), now()));
        return saleResponse(sale);
    }

    private boolean willFullyReturnSale(Sale sale, List<PreparedReturnItem> prepared) {
        var requested = prepared.stream().collect(Collectors.toMap(
                value -> value.saleItem().getId(), PreparedReturnItem::baseQuantity));
        return saleItemRepository.findBySaleIdOrderByIdAsc(sale.getId()).stream()
                .allMatch(item -> item.remainingReturnableBaseQuantity()
                        .subtract(requested.getOrDefault(item.getId(), BigDecimal.ZERO.setScale(QUANTITY_SCALE)))
                        .signum() == 0);
    }

    private List<PreparedReturnItem> adjustFullReturnAmount(Sale sale, List<PreparedReturnItem> prepared) {
        if (prepared.isEmpty()) return prepared;
        BigDecimal expected = sale.netPayableAfterReturns();
        BigDecimal calculated = prepared.stream().map(PreparedReturnItem::returnAmount)
                .reduce(zeroMoney(), BigDecimal::add).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal difference = expected.subtract(calculated).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (difference.signum() == 0) return prepared;
        List<PreparedReturnItem> adjusted = new ArrayList<>(prepared);
        int lastIndex = adjusted.size() - 1;
        PreparedReturnItem last = adjusted.get(lastIndex);
        BigDecimal adjustedAmount = last.returnAmount().add(difference).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (adjustedAmount.signum() < 0) {
            throw new SaleStateException("Sales Return financial allocation cannot be reconciled with the Sale total");
        }
        adjusted.set(lastIndex, new PreparedReturnItem(
                last.saleItem(), last.request(), last.enteredQuantity(), last.baseQuantity(),
                adjustedAmount, last.batchParts()));
        return List.copyOf(adjusted);
    }

    private List<PreparedReturnItem> prepareReturnItems(Sale sale, List<SaleReturnItemRequest> requests) {
        Set<Long> itemIds = new HashSet<>();
        List<PreparedReturnItem> prepared = new ArrayList<>();
        for (SaleReturnItemRequest request : requests) {
            if (request == null || request.getSaleItemId() == null || request.getSaleItemId() <= 0) {
                throw new SaleValidationException("Sales Return Item must identify a Sale Item");
            }
            if (!itemIds.add(request.getSaleItemId())) {
                throw new SaleValidationException("The same Sale Item cannot be returned twice in one request");
            }
            if (request.getQuantity() == null || request.getQuantity().signum() <= 0) {
                throw new SaleValidationException("Sales Return quantity must be greater than zero");
            }
            if (request.getCondition() == null) throw new SaleValidationException("Sales Return item condition is required");
            if (request.getCondition() != SaleReturnItemCondition.SELLABLE
                    && (request.getReason() == null || request.getReason().isBlank())) {
                throw new SaleValidationException("Damaged, Expired or Quarantined returns require a reason");
            }
            SaleItem item = saleItemRepository.findForUpdate(sale.getId(), request.getSaleItemId())
                    .orElseThrow(() -> new SaleValidationException("Sale Item does not belong to this Sale"));
            BigDecimal enteredQuantity = quantity(request.getQuantity());
            BigDecimal baseQuantity = enteredQuantity.multiply(item.getConversionFactor())
                    .setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
            if (baseQuantity.compareTo(item.remainingReturnableBaseQuantity()) > 0) {
                throw new SaleValidationException("Sales Return quantity exceeds the remaining sold quantity");
            }
            BigDecimal returnAmount = item.calculateReturnAmount(baseQuantity);
            if (sale.getItemPayableTotal().signum() > 0) {
                returnAmount = returnAmount.multiply(sale.getTotalPayable())
                        .divide(sale.getItemPayableTotal(), MONEY_SCALE, RoundingMode.HALF_UP);
            }
            List<SaleItemBatchAllocation> allocations = allocationRepository.findForUpdateBySaleItemId(item.getId());
            List<BatchPart> parts = allocateReturnedBatches(allocations, baseQuantity);
            prepared.add(new PreparedReturnItem(item, request, enteredQuantity, baseQuantity,
                    returnAmount, parts));
        }
        return prepared;
    }

    private List<BatchPart> allocateReturnedBatches(List<SaleItemBatchAllocation> allocations, BigDecimal baseQuantity) {
        if (allocations == null || allocations.isEmpty()) return List.of();
        BigDecimal remaining = baseQuantity;
        List<BatchPart> parts = new ArrayList<>();
        for (SaleItemBatchAllocation allocation : allocations) {
            if (remaining.signum() == 0) break;
            BigDecimal eligible = allocation.remainingReturnableBaseQuantity();
            if (eligible.signum() <= 0) continue;
            BigDecimal part = remaining.min(eligible).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
            parts.add(new BatchPart(allocation, part));
            remaining = remaining.subtract(part).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
        }
        if (remaining.signum() > 0) {
            throw new SaleStateException("Original Sale batch allocation cannot satisfy the Sales Return quantity");
        }
        return parts;
    }

    private SaleReturnItem createReturnItem(Long saleReturnId,
                                            SaleItem saleItem,
                                            SaleItemBatchAllocation allocation,
                                            BigDecimal baseQuantity,
                                            BigDecimal returnAmount,
                                            SaleReturnItemRequest request) {
        BigDecimal entered = baseQuantity.divide(saleItem.getConversionFactor(), QUANTITY_SCALE, RoundingMode.HALF_UP);
        return SaleReturnItem.create(
                saleReturnId, saleItem.getId(), allocation == null ? null : allocation.getId(),
                saleItem.getProductVariantId(), allocation == null ? null : allocation.getProductBatchId(),
                allocation == null ? null : allocation.getBatchNumberSnapshot(),
                allocation == null ? null : allocation.getExpiryDateSnapshot(), entered,
                saleItem.getEnteredUnitId(), saleItem.getConversionFactor(), saleItem.getBaseInventoryUnitId(),
                baseQuantity, returnAmount, saleItem.getWeightedAverageCostSnapshot(), request.getCondition(),
                request.getReason());
    }

    private SaleReturnStockPostingRequest returnStockRequest(Long businessId,
                                                             Long branchId,
                                                             SaleReturn saleReturn,
                                                             SaleReturnItem item,
                                                             LocalDate returnDate,
                                                             Long actorId,
                                                             String notes) {
        return new SaleReturnStockPostingRequest(
                businessId, branchId, item.getProductVariantId(), item.getProductBatchId(),
                item.getEnteredReturnQuantity(), item.getReturnUnitId(), item.getConversionFactor(),
                item.getBaseInventoryUnitId(), item.getBaseQuantity(), item.getPreservedFinancialCostSnapshot(),
                returnDate, saleReturn.getId(), item.getId(), item.getCondition() == SaleReturnItemCondition.SELLABLE,
                limitedKey("SALE-RETURN:" + saleReturn.getId() + ":" + item.getId()), actorId,
                item.getReason(), notes);
    }

    private Settlement settleReturnExcess(Long businessId,
                                          BranchAccessResponse branch,
                                          Sale sale,
                                          SaleReturn saleReturn,
                                          SaleReturnRequest request,
                                          Long ownerId,
                                          BigDecimal dueReduction,
                                          BigDecimal excess) {
        if (excess.signum() == 0) {
            if (request.getSettlementType() != SaleReturnSettlementType.REDUCE_DUE) {
                throw new SaleValidationException("A Sales Return fully absorbed by invoice due must use Reduce Due settlement");
            }
            return new Settlement(zeroMoney(), zeroMoney(), null);
        }
        if (request.getSettlementType() == SaleReturnSettlementType.REDUCE_DUE) {
            throw new SaleValidationException("Sales Return exceeds outstanding due; choose Customer Refund or Customer Credit");
        }
        if (request.getSettlementType() == SaleReturnSettlementType.CUSTOMER_CREDIT) {
            return new Settlement(zeroMoney(), excess, null);
        }
        if (request.getPaymentMethodId() == null || request.getPaymentMethodId() <= 0) {
            throw new SaleValidationException("Payment Method is required for Customer Refund settlement");
        }
        PaymentResponse refund = paymentPostingService.postCustomerRefund(new CustomerRefundCommand(
                businessId, branch.branchId(), ownerId, sale.getCustomerId(), sale.getId(), saleReturn.getId(),
                excess, request.getPaymentMethodId(), request.getTransactionReference(), request.getAccountReference(),
                request.getCashLocationId(), request.getRegisterId(), request.getCashierShiftId(),
                limitedKey("SALE-REFUND:" + saleReturn.getId() + ":" + request.getIdempotencyKey()), request.getNotes()));
        return new Settlement(excess, zeroMoney(), refund == null ? null : refund.id());
    }

    private SaleResponse saleResponse(Sale sale) {
        String customerName = customerAccessService.findByBusinessIdAndCustomerId(
                        sale.getBusinessId(), sale.getCustomerId())
                .map(CustomerAccessResponse::name).orElse(null);
        return saleResponse(sale, customerName);
    }

    private SaleResponse saleResponse(Sale sale, String customerName) {
        List<SaleItemResponse> items = saleItemRepository.findBySaleIdOrderByIdAsc(sale.getId()).stream()
                .map(this::saleItemResponse)
                .toList();
        return new SaleResponse(
                sale.getId(), sale.getBusinessId(), sale.getBranchId(), sale.getCustomerId(), customerName, sale.getStatus(),
                sale.getPaymentStatus(), sale.getGrossItemTotal(), sale.getItemDiscountTotal(), sale.getItemTaxTotal(),
                sale.getItemPayableTotal(), sale.getOrderDiscount(), sale.getShippingCharge(), sale.getOtherCharge(),
                sale.getTotalPayable(), sale.getPaidAmount(), sale.getDueAmount(), sale.getChangeAmount(),
                sale.getReturnedAmount(), sale.getCogsTotal(), sale.getDueDate(), sale.getNotes(),
                sale.getCreatedByActorId(), sale.getConfirmedAt(),
                sale.getCreatedAt(), sale.getUpdatedAt(), items);
    }

    private SaleItemResponse saleItemResponse(SaleItem item) {
        List<SaleBatchAllocationResponse> allocations = allocationRepository.findBySaleItemIdOrderByIdAsc(item.getId())
                .stream()
                .map(value -> new SaleBatchAllocationResponse(
                        value.getId(), value.getProductBatchId(), value.getBatchNumberSnapshot(),
                        value.getExpiryDateSnapshot(), value.getBaseQuantity(), value.getReturnedBaseQuantity()))
                .toList();
        return new SaleItemResponse(
                item.getId(), item.getProductVariantId(), item.getProductNameSnapshot(), item.getVariantNameSnapshot(),
                item.getProductCodeSnapshot(), item.getEnteredQuantity(), item.getEnteredUnitId(),
                item.getConversionFactor(), item.getBaseInventoryUnitId(), item.getBaseQuantity(), item.getUnitPrice(),
                item.getGrossAmount(), item.getDiscountAmount(), item.getTaxRate(), item.getTaxMethod(),
                item.getTaxAmount(), item.getLinePayable(), item.getStockMovementId(),
                item.getWeightedAverageCostSnapshot(), item.getCogsAmount(), item.getReturnedBaseQuantity(), allocations);
    }

    private SaleReturnResponse returnResponse(SaleReturn saleReturn) {
        String customerName = customerAccessService.findByBusinessIdAndCustomerId(
                        saleReturn.getBusinessId(), saleReturn.getCustomerId())
                .map(CustomerAccessResponse::name).orElse(null);
        return returnResponse(saleReturn, customerName);
    }

    private SaleReturnResponse returnResponse(SaleReturn saleReturn, List<SaleReturnItem> items) {
        String customerName = customerAccessService.findByBusinessIdAndCustomerId(
                        saleReturn.getBusinessId(), saleReturn.getCustomerId())
                .map(CustomerAccessResponse::name).orElse(null);
        return returnResponse(saleReturn, customerName, items);
    }

    private SaleReturnResponse returnResponse(SaleReturn saleReturn, String customerName) {
        return returnResponse(saleReturn, customerName,
                saleReturnItemRepository.findBySaleReturnIdOrderByIdAsc(saleReturn.getId()));
    }

    private SaleReturnResponse returnResponse(SaleReturn saleReturn, String customerName, List<SaleReturnItem> items) {
        return new SaleReturnResponse(
                saleReturn.getId(), saleReturn.getBusinessId(), saleReturn.getBranchId(), saleReturn.getSaleId(),
                saleReturn.getCustomerId(), customerName, saleReturn.getReferenceNumber(), saleReturn.getSettlementType(),
                saleReturn.getTotalReturnAmount(), saleReturn.getDueReductionAmount(), saleReturn.getRefundAmount(),
                saleReturn.getCustomerCreditAmount(), saleReturn.getRefundPaymentId(), saleReturn.getStatus(),
                saleReturn.getNotes(), saleReturn.getCreatedByActorId(), saleReturn.getConfirmedAt(),
                saleReturn.getCreatedAt(), items.stream().map(this::returnItemResponse).toList());
    }

    private SaleReturnItemResponse returnItemResponse(SaleReturnItem item) {
        return new SaleReturnItemResponse(
                item.getId(), item.getSaleItemId(), item.getSaleItemBatchAllocationId(), item.getProductVariantId(),
                item.getProductBatchId(), item.getBatchNumberSnapshot(), item.getExpiryDateSnapshot(),
                item.getEnteredReturnQuantity(), item.getReturnUnitId(), item.getConversionFactor(),
                item.getBaseInventoryUnitId(), item.getBaseQuantity(), item.getReturnAmount(),
                item.getPreservedFinancialCostSnapshot(), item.getCondition(), item.getReason(), item.getStockMovementId());
    }

    private Map<Long, CustomerAccessResponse> customersForSales(Long businessId, List<Sale> sales) {
        return customerAccessService.findByBusinessIdAndCustomerIds(
                businessId, sales.stream().map(Sale::getCustomerId).toList());
    }

    private String customerName(Map<Long, CustomerAccessResponse> customers, Long customerId) {
        CustomerAccessResponse customer = customerId == null ? null : customers.get(customerId);
        return customer == null ? null : customer.name();
    }

    private void validateReturnRequest(SaleReturnRequest request) {
        if (request == null) throw new SaleValidationException("Sales Return request is required");
        if (request.getBranchId() == null || request.getBranchId() <= 0) {
            throw new SaleValidationException("branchId must be a positive ID");
        }
        requireText(request.getIdempotencyKey(), 100, "Idempotency key");
        if (request.getSettlementType() == null) throw new SaleValidationException("Sales Return settlement is required");
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new SaleValidationException("Sales Return requires at least one item");
        }
        optionalText(request.getTransactionReference(), 160, "Transaction reference");
        optionalText(request.getAccountReference(), 160, "Account reference");
        optionalText(request.getNotes(), 1000, "Notes");
    }

    private void validateCorrectionRequest(SaleCorrectionRequest request) {
        if (request == null) throw new SaleValidationException("Sale correction request is required");
        requireText(request.getReason(), 500, "Correction reason");
        requireText(request.getIdempotencyKey(), 100, "Idempotency key");
    }

    private BusinessAccessResponse requireBusiness(Long ownerId) {
        if (ownerId == null || ownerId <= 0) throw new SaleAccessDeniedException();
        return businessAccessService.findByOwnerId(ownerId).orElseThrow(SaleAccessDeniedException::new);
    }

    private BranchAccessResponse requireBranch(Long businessId, Long branchId) {
        if (branchId == null || branchId <= 0) throw new SaleAccessDeniedException();
        return branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId)
                .orElseThrow(SaleAccessDeniedException::new);
    }

    private Sale requireSale(Long businessId, Long branchId, Long saleId) {
        if (saleId == null || saleId <= 0) throw new SaleNotFoundException();
        return saleRepository.findByIdAndBusinessIdAndBranchId(saleId, businessId, branchId)
                .orElseThrow(SaleNotFoundException::new);
    }

    private String normalizeSellFilter(String filter) {
        if (filter == null || filter.isBlank()) return "ALL";
        String value = filter.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (value) {
            case "TODAY", "TODAY_INVOICE", "TODAY_INVOICES" -> "TODAY";
            case "DUE", "DUE_INVOICE", "DUE_INVOICES", "ALL_DUE", "ALL_DUE_INVOICE", "ALL_DUE_INVOICES" -> "DUE";
            case "PAID", "PAID_INVOICE", "PAID_INVOICES" -> "PAID";
            case "INACTIVE", "INACTIVE_INVOICE", "INACTIVE_INVOICES" -> "INACTIVE";
            default -> "ALL";
        };
    }

    private String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new SaleValidationException("Quantity must be greater than zero");
        return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) throw new SaleValidationException(field + " is required");
        if (value.trim().length() > maxLength) throw new SaleValidationException(field + " is too long");
    }

    private void optionalText(String value, int maxLength, String field) {
        if (value != null && value.trim().length() > maxLength) throw new SaleValidationException(field + " is too long");
    }

    private String buildReturnReference(Long saleId, Instant time) {
        return "SR-" + saleId + "-" + time.toEpochMilli();
    }

    private String limitedKey(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private ZoneId zone(BranchAccessResponse branch) {
        try {
            return branch.timeZone() == null || branch.timeZone().isBlank()
                    ? clock.getZone() : ZoneId.of(branch.timeZone());
        } catch (RuntimeException ex) {
            return clock.getZone();
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private record BatchPart(SaleItemBatchAllocation allocation, BigDecimal baseQuantity) {}

    private record PreparedReturnItem(
            SaleItem saleItem,
            SaleReturnItemRequest request,
            BigDecimal enteredQuantity,
            BigDecimal baseQuantity,
            BigDecimal returnAmount,
            List<BatchPart> batchParts) {}

    private record Settlement(BigDecimal refundAmount, BigDecimal customerCreditAmount, Long refundPaymentId) {}

    private Long actor(Long ownerId) { return currentActorService.actorId(ownerId); }
}
