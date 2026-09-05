package com.spark.falcon.sale.service;

import com.spark.falcon.sale.dto.*;
import com.spark.falcon.sale.entity.*;
import com.spark.falcon.sale.exception.SaleNotFoundException;
import com.spark.falcon.sale.exception.SaleStateException;
import com.spark.falcon.sale.exception.SaleValidationException;
import com.spark.falcon.sale.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SalePostingService implements SalePaymentAccessService {

    private static final int MONEY_SCALE = 4;
    private static final int QUANTITY_SCALE = 8;

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final SaleItemBatchAllocationRepository allocationRepository;
    private final SaleAuditEventRepository auditRepository;
    private final Clock clock;

    @Transactional
    public SaleResponse createDraft(CreateSaleCommand command, List<CreateSaleItemCommand> itemCommands) {
        validateCreate(command, itemCommands);
        Sale repeated = saleRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                command.businessId(), command.branchId(), command.idempotencyKey().trim()).orElse(null);
        if (repeated != null) return response(repeated);

        Instant now = now();
        Sale sale = Sale.draft(
                command.businessId(), command.branchId(), command.customerId(), command.idempotencyKey(),
                command.grossItemTotal(), command.itemDiscountTotal(), command.itemTaxTotal(),
                command.itemPayableTotal(), command.orderDiscount(), command.shippingCharge(),
                command.otherCharge(), command.totalPayable(), command.dueDate(), command.notes(), command.actorId(), now);
        sale = saleRepository.saveAndFlush(sale);

        for (CreateSaleItemCommand item : itemCommands) {
            saleItemRepository.save(SaleItem.create(
                    sale.getId(), item.productVariantId(), item.productName(), item.variantName(), item.productCode(),
                    item.enteredQuantity(), item.enteredUnitId(), item.conversionFactor(), item.baseInventoryUnitId(),
                    item.baseQuantity(), item.unitPrice(), item.grossAmount(), item.discountAmount(), item.taxRate(),
                    item.taxMethod(), item.taxAmount(), item.linePayable()));
        }
        saleItemRepository.flush();
        auditRepository.save(SaleAuditEvent.record(
                sale.getBusinessId(), sale.getBranchId(), sale.getId(), SaleAuditAction.SALE_DRAFT_CREATED,
                command.actorId(), "Sale Draft created with " + itemCommands.size() + " item(s)", now));
        auditRepository.flush();
        return response(sale);
    }

    @Transactional
    public SaleResponse updateDraftOrHeld(Long businessId,
                                          Long branchId,
                                          Long saleId,
                                          CreateSaleCommand command,
                                          List<CreateSaleItemCommand> itemCommands) {
        validateCreate(command, itemCommands);
        if (!businessId.equals(command.businessId()) || !branchId.equals(command.branchId())) {
            throw new SaleValidationException("Sale update context does not match the Sale branch");
        }
        Sale sale = lockSale(businessId, branchId, saleId);
        if (!sale.isDraftOrHeld()) throw new SaleStateException("Only a Draft or Held Sale can be edited");
        List<SaleItem> currentItems = saleItemRepository.findBySaleIdOrderByIdAsc(saleId);
        if (currentItems.stream().anyMatch(item -> item.getStockMovementId() != null
                || item.getWeightedAverageCostSnapshot() != null)) {
            throw new SaleStateException("A Sale with posted inventory cannot be edited");
        }
        allocationRepository.deleteAll(currentItems.stream()
                .flatMap(item -> allocationRepository.findBySaleItemIdOrderByIdAsc(item.getId()).stream()).toList());
        saleItemRepository.deleteAll(currentItems);
        saleItemRepository.flush();
        try {
            sale.updateDraftOrHeld(command.customerId(), command.grossItemTotal(), command.itemDiscountTotal(),
                    command.itemTaxTotal(), command.itemPayableTotal(), command.orderDiscount(),
                    command.shippingCharge(), command.otherCharge(), command.totalPayable(), command.dueDate(),
                    command.notes(), now());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new SaleStateException(ex.getMessage());
        }
        saleRepository.saveAndFlush(sale);
        for (CreateSaleItemCommand item : itemCommands) {
            saleItemRepository.save(SaleItem.create(
                    saleId, item.productVariantId(), item.productName(), item.variantName(), item.productCode(),
                    item.enteredQuantity(), item.enteredUnitId(), item.conversionFactor(), item.baseInventoryUnitId(),
                    item.baseQuantity(), item.unitPrice(), item.grossAmount(), item.discountAmount(), item.taxRate(),
                    item.taxMethod(), item.taxAmount(), item.linePayable()));
        }
        saleItemRepository.flush();
        auditRepository.saveAndFlush(SaleAuditEvent.record(
                businessId, branchId, saleId, SaleAuditAction.SALE_DRAFT_UPDATED, command.actorId(),
                "Draft/Held Sale revalidated and updated with " + itemCommands.size() + " item(s)", now()));
        return response(sale);
    }

    @Transactional
    public SaleResponse hold(Long businessId, Long branchId, Long saleId) {
        Sale sale = lockSale(businessId, branchId, saleId);
        try {
            sale.hold(now());
        } catch (IllegalStateException ex) {
            throw new SaleStateException(ex.getMessage());
        }
        saleRepository.saveAndFlush(sale);
        auditRepository.saveAndFlush(SaleAuditEvent.record(
                businessId, branchId, saleId, SaleAuditAction.SALE_HELD,
                sale.getCreatedByActorId(), "Sale moved to Held status", now()));
        return response(sale);
    }

    @Transactional
    public SaleResponse attachInventoryPosting(Long businessId,
                                               Long branchId,
                                               Long saleId,
                                               Long saleItemId,
                                               Long stockMovementId,
                                               BigDecimal weightedAverageCostSnapshot,
                                               List<SaleBatchAllocationCommand> allocations) {
        Sale sale = lockSale(businessId, branchId, saleId);
        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new SaleStateException("Inventory posting can be attached only to a Draft Sale");
        }
        SaleItem item = saleItemRepository.findForUpdate(saleId, saleItemId)
                .orElseThrow(() -> new SaleValidationException("Sale Item was not found in this Sale"));
        requirePositiveId(stockMovementId, "stockMovementId");
        BigDecimal cost = money(weightedAverageCostSnapshot);

        List<SaleBatchAllocationCommand> safeAllocations = allocations == null ? List.of() : allocations;
        if (!safeAllocations.isEmpty()) {
            BigDecimal allocated = BigDecimal.ZERO.setScale(QUANTITY_SCALE);
            Set<Long> batchIds = new HashSet<>();
            for (SaleBatchAllocationCommand allocation : safeAllocations) {
                if (allocation == null) throw new SaleValidationException("Sale batch allocation is required");
                requirePositiveId(allocation.productBatchId(), "productBatchId");
                if (!batchIds.add(allocation.productBatchId())) {
                    throw new SaleValidationException("The same Product Batch cannot be allocated twice to one Sale Item");
                }
                BigDecimal quantity = positiveQuantity(allocation.baseQuantity(), "batch allocation Base Quantity");
                allocated = allocated.add(quantity);
            }
            if (allocated.compareTo(item.getBaseQuantity()) != 0) {
                throw new SaleValidationException("Sale batch allocations must equal the Sale Item Base Quantity");
            }
        }

        try {
            item.attachInventoryPosting(stockMovementId, cost);
            sale.attachCogs(item.getCogsAmount(), now());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new SaleStateException(ex.getMessage());
        }
        saleItemRepository.saveAndFlush(item);
        saleRepository.saveAndFlush(sale);

        for (SaleBatchAllocationCommand allocation : safeAllocations) {
            allocationRepository.save(SaleItemBatchAllocation.create(
                    item.getId(), allocation.productBatchId(), allocation.batchNumber(), allocation.expiryDate(),
                    allocation.baseQuantity()));
        }
        allocationRepository.flush();
        auditRepository.saveAndFlush(SaleAuditEvent.record(
                businessId, branchId, saleId, SaleAuditAction.SALE_INVENTORY_POSTED,
                sale.getCreatedByActorId(), "Inventory and COGS snapshot attached to Sale Item " + saleItemId, now()));
        return response(sale);
    }

    @Transactional
    public SaleResponse confirm(Long businessId, Long branchId, Long saleId, BigDecimal changeAmount) {
        Sale sale = lockSale(businessId, branchId, saleId);
        List<SaleItem> items = saleItemRepository.findBySaleIdOrderByIdAsc(saleId);
        if (items.isEmpty()) throw new SaleValidationException("Sale cannot be confirmed without Sale Items");
        if (items.stream().anyMatch(item -> item.getStockMovementId() == null
                || item.getWeightedAverageCostSnapshot() == null)) {
            throw new SaleStateException("Every Sale Item must have a confirmed inventory posting before Sale confirmation");
        }
        try {
            sale.confirm(money(changeAmount), now());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new SaleStateException(ex.getMessage());
        }
        sale = saleRepository.saveAndFlush(sale);
        auditRepository.saveAndFlush(SaleAuditEvent.record(
                businessId, branchId, saleId, SaleAuditAction.SALE_CONFIRMED,
                sale.getCreatedByActorId(), "Sale confirmed with preserved stock allocations and COGS snapshots", now()));
        return response(sale);
    }

    public SaleResponse findByIdempotency(Long businessId, Long branchId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return null;
        return saleRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                        businessId, branchId, idempotencyKey.trim())
                .map(this::response)
                .orElse(null);
    }

    public SaleResponse findById(Long businessId, Long branchId, Long saleId) {
        Sale sale = saleRepository.findByIdAndBusinessIdAndBranchId(saleId, businessId, branchId)
                .orElseThrow(SaleNotFoundException::new);
        return response(sale);
    }

    @Override
    @Transactional
    public SalePaymentInvoiceResponse lockEligibleCustomerInvoice(
            Long businessId, Long branchId, Long customerId, Long saleId) {
        Sale sale = lockSale(businessId, branchId, saleId);
        validateCustomerInvoice(sale, customerId);
        return invoiceResponse(sale);
    }

    @Override
    public List<SalePaymentInvoiceResponse> findEligibleCustomerInvoicesOldestFirst(
            Long businessId, Long branchId, Long customerId) {
        return saleRepository.findDueInvoicesOldestFirst(
                        businessId, branchId, customerId,
                        List.of(SaleStatus.CONFIRMED, SaleStatus.PARTIALLY_RETURNED))
                .stream()
                .filter(Sale::isDuePaymentEligible)
                .map(this::invoiceResponse)
                .sorted(java.util.Comparator.comparing(SalePaymentInvoiceResponse::agingDate)
                        .thenComparing(SalePaymentInvoiceResponse::createdAt)
                        .thenComparing(SalePaymentInvoiceResponse::saleId))
                .toList();
    }

    @Override
    @Transactional
    public SalePaymentAllocationResult applyCustomerPayment(
            Long businessId, Long branchId, Long customerId, Long saleId,
            BigDecimal amount, Long paymentId, Long actorId) {
        requirePositiveId(paymentId, "paymentId");
        requirePositiveId(actorId, "actorId");
        Sale sale = lockSale(businessId, branchId, saleId);
        validateCustomerInvoice(sale, customerId);
        BigDecimal normalized = positiveMoney(amount, "Customer Payment amount");
        Sale.PaymentChange change;
        try {
            change = sale.applyCustomerPayment(normalized, now());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new SaleStateException(ex.getMessage());
        }
        saleRepository.saveAndFlush(sale);
        auditRepository.saveAndFlush(SaleAuditEvent.record(
                businessId, branchId, saleId, SaleAuditAction.CUSTOMER_PAYMENT_APPLIED, actorId,
                "Payment " + paymentId + " applied to Sale invoice", now()));
        return new SalePaymentAllocationResult(
                saleId, normalized, change.dueBefore(), change.dueAfter(), sale.getPaymentStatus());
    }

    @Override
    @Transactional
    public SalePaymentAllocationResult reverseCustomerPayment(
            Long businessId, Long branchId, Long customerId, Long saleId,
            BigDecimal amount, Long reversalPaymentId, Long actorId) {
        requirePositiveId(reversalPaymentId, "reversalPaymentId");
        requirePositiveId(actorId, "actorId");
        Sale sale = lockSale(businessId, branchId, saleId);
        if (!sale.getCustomerId().equals(customerId)) {
            throw new SaleValidationException("Payment Customer does not match the Sale Customer");
        }
        BigDecimal normalized = positiveMoney(amount, "Customer Payment reversal amount");
        Sale.PaymentChange change;
        try {
            change = sale.reverseCustomerPayment(normalized, now());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new SaleStateException(ex.getMessage());
        }
        saleRepository.saveAndFlush(sale);
        auditRepository.saveAndFlush(SaleAuditEvent.record(
                businessId, branchId, saleId, SaleAuditAction.CUSTOMER_PAYMENT_REVERSED, actorId,
                "Customer Payment reversed by Payment " + reversalPaymentId, now()));
        return new SalePaymentAllocationResult(
                saleId, normalized, change.dueBefore(), change.dueAfter(), sale.getPaymentStatus());
    }

    private void validateCustomerInvoice(Sale sale, Long customerId) {
        requirePositiveId(customerId, "customerId");
        if (!sale.getCustomerId().equals(customerId)) {
            throw new SaleValidationException("Customer does not match the Sale invoice");
        }
        if (!sale.isDuePaymentEligible()) {
            throw new SaleStateException("Sale invoice has no eligible Outstanding Due");
        }
    }

    private Sale lockSale(Long businessId, Long branchId, Long saleId) {
        requirePositiveId(businessId, "businessId");
        requirePositiveId(branchId, "branchId");
        requirePositiveId(saleId, "saleId");
        return saleRepository.findForUpdate(businessId, branchId, saleId)
                .orElseThrow(SaleNotFoundException::new);
    }

    private void validateCreate(CreateSaleCommand command, List<CreateSaleItemCommand> itemCommands) {
        if (command == null) throw new SaleValidationException("Create Sale command is required");
        requirePositiveId(command.businessId(), "businessId");
        requirePositiveId(command.branchId(), "branchId");
        requirePositiveId(command.customerId(), "customerId");
        requirePositiveId(command.actorId(), "actorId");
        requireText(command.idempotencyKey(), 100, "Idempotency key");
        if (itemCommands == null || itemCommands.isEmpty()) {
            throw new SaleValidationException("Sale requires at least one Sale Item");
        }
        money(command.grossItemTotal());
        money(command.itemDiscountTotal());
        money(command.itemTaxTotal());
        money(command.itemPayableTotal());
        money(command.orderDiscount());
        money(command.shippingCharge());
        money(command.otherCharge());
        money(command.totalPayable());
        if (command.notes() != null && command.notes().trim().length() > 1000) {
            throw new SaleValidationException("Sale notes are too long");
        }
        for (CreateSaleItemCommand item : itemCommands) validateItem(item);
    }

    private void validateItem(CreateSaleItemCommand item) {
        if (item == null) throw new SaleValidationException("Sale Item is required");
        requirePositiveId(item.productVariantId(), "productVariantId");
        requirePositiveId(item.enteredUnitId(), "enteredUnitId");
        requirePositiveId(item.baseInventoryUnitId(), "baseInventoryUnitId");
        requireText(item.productName(), 180, "Product name");
        requireText(item.variantName(), 180, "Variant name");
        requireText(item.productCode(), 100, "Product code");
        positiveQuantity(item.enteredQuantity(), "Entered Quantity");
        positiveQuantity(item.conversionFactor(), "Conversion Factor");
        positiveQuantity(item.baseQuantity(), "Base Quantity");
        money(item.unitPrice());
        money(item.grossAmount());
        money(item.discountAmount());
        money(item.taxAmount());
        money(item.linePayable());
    }

    private SaleResponse response(Sale sale) {
        List<SaleItemResponse> items = saleItemRepository.findBySaleIdOrderByIdAsc(sale.getId()).stream()
                .map(this::itemResponse)
                .toList();
        return new SaleResponse(
                sale.getId(), sale.getBusinessId(), sale.getBranchId(), sale.getCustomerId(), sale.getStatus(),
                sale.getPaymentStatus(), sale.getGrossItemTotal(), sale.getItemDiscountTotal(), sale.getItemTaxTotal(),
                sale.getItemPayableTotal(), sale.getOrderDiscount(), sale.getShippingCharge(), sale.getOtherCharge(),
                sale.getTotalPayable(), sale.getPaidAmount(), sale.getDueAmount(), sale.getChangeAmount(),
                sale.getReturnedAmount(), sale.getCogsTotal(), sale.getDueDate(), sale.getNotes(),
                sale.getCreatedByActorId(), sale.getConfirmedAt(),
                sale.getCreatedAt(), sale.getUpdatedAt(), items);
    }

    private SaleItemResponse itemResponse(SaleItem item) {
        List<SaleBatchAllocationResponse> allocations = allocationRepository
                .findBySaleItemIdOrderByIdAsc(item.getId()).stream()
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

    private SalePaymentInvoiceResponse invoiceResponse(Sale sale) {
        return new SalePaymentInvoiceResponse(
                sale.getId(), sale.getCustomerId(), sale.getDueAmount(), sale.getStatus(),
                sale.getPaymentStatus(), sale.getDueDate() == null
                        ? sale.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        : sale.getDueDate(), sale.getCreatedAt());
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new SaleValidationException("Money value must not be negative");
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal positiveMoney(BigDecimal value, String field) {
        BigDecimal amount = money(value);
        if (amount.signum() <= 0) throw new SaleValidationException(field + " must be greater than zero");
        return amount;
    }

    private BigDecimal positiveQuantity(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) throw new SaleValidationException(field + " must be greater than zero");
        return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private void requirePositiveId(Long value, String field) {
        if (value == null || value <= 0) throw new SaleValidationException(field + " must be a positive ID");
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) throw new SaleValidationException(field + " is required");
        if (value.trim().length() > maxLength) throw new SaleValidationException(field + " is too long");
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
