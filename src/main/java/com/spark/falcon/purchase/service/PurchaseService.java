package com.spark.falcon.purchase.service;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.inventory.dto.InventoryPostingResponse;
import com.spark.falcon.inventory.dto.StockReceiptRequest;
import com.spark.falcon.inventory.entity.InventoryActorType;
import com.spark.falcon.inventory.entity.StockMovementType;
import com.spark.falcon.inventory.entity.StockSourceType;
import com.spark.falcon.inventory.service.InventoryPostingService;
import com.spark.falcon.product.dto.response.ProductUnitConversionResponse;
import com.spark.falcon.product.dto.response.ProductVariantAccessResponse;
import com.spark.falcon.product.service.ProductAccessService;
import com.spark.falcon.payment.dto.InitialSupplierPaymentCommand;
import com.spark.falcon.payment.service.PaymentPostingService;
import com.spark.falcon.payment.service.PaymentService;
import com.spark.falcon.purchase.dto.command.ConfirmPurchaseCommand;
import com.spark.falcon.purchase.dto.command.CreatePurchaseCommand;
import com.spark.falcon.purchase.dto.command.PurchaseItemCommand;
import com.spark.falcon.purchase.dto.response.PurchaseResponse;
import com.spark.falcon.purchase.dto.response.PurchaseAuditResponse;
import com.spark.falcon.purchase.dto.response.PurchaseDetailsResponse;
import com.spark.falcon.purchase.dto.response.PurchaseLogResponse;
import com.spark.falcon.purchase.entity.Purchase;
import com.spark.falcon.purchase.entity.PurchaseAuditEvent;
import com.spark.falcon.purchase.entity.PurchaseItem;
import com.spark.falcon.purchase.entity.enumtype.PurchaseActorType;
import com.spark.falcon.purchase.entity.enumtype.PurchaseAuditAction;
import com.spark.falcon.purchase.exception.PurchaseAccessDeniedException;
import com.spark.falcon.purchase.exception.PurchaseNotFoundException;
import com.spark.falcon.purchase.exception.PurchaseStateException;
import com.spark.falcon.purchase.exception.PurchaseValidationException;
import com.spark.falcon.purchase.mapper.PurchaseMapper;
import com.spark.falcon.purchase.repository.PurchaseAuditEventRepository;
import com.spark.falcon.purchase.repository.PurchaseItemRepository;
import com.spark.falcon.purchase.repository.PurchaseRepository;
import com.spark.falcon.purchase.validation.PurchaseValidator;
import com.spark.falcon.supplier.service.SupplierService;
import com.spark.falcon.settings.dto.response.PaymentMethodResponse;
import com.spark.falcon.settings.dto.response.TaxRateResponse;
import com.spark.falcon.settings.service.BranchSettingsAccessService;
import com.spark.falcon.settings.service.PaymentMethodAccessService;
import com.spark.falcon.settings.service.TaxRateAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private static final BigDecimal ONE = BigDecimal.ONE.setScale(PurchaseValidator.QUANTITY_SCALE);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository itemRepository;
    private final PurchaseAuditEventRepository auditRepository;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final SupplierService supplierService;
    private final ProductAccessService productAccessService;
    private final BranchSettingsAccessService branchSettingsAccessService;
    private final TaxRateAccessService taxRateAccessService;
    private final PaymentMethodAccessService paymentMethodAccessService;
    private final InventoryPostingService inventoryPostingService;
    private final PaymentPostingService paymentPostingService;
    private final PaymentService paymentService;
    private final PurchaseMapper mapper;
    private final PurchaseValidator validator;
    private final Clock clock;

    @Transactional
    public PurchaseResponse createDraft(CreatePurchaseCommand command) {
        validator.validate(command);
        BusinessAccessResponse business = requireBusiness(command.ownerId());
        return createDraftInternal(business.businessId(), command);
    }

    @Transactional
    public PurchaseResponse createAndConfirm(CreatePurchaseCommand command) {
        validator.validate(command);
        BusinessAccessResponse business = requireBusiness(command.ownerId());

        Purchase existing = purchaseRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                business.businessId(), command.branchId(), command.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (existing.isDraft()) {
                Purchase locked = purchaseRepository.findForUpdate(
                                business.businessId(), command.branchId(), existing.getId())
                        .orElseThrow(PurchaseNotFoundException::new);
                if (!locked.isDraft()) return response(locked);
                return confirmInternal(locked, itemRepository.findByPurchaseIdOrderByIdAsc(locked.getId()),
                        command.ownerId(), validator.money(command.paidAmount()), command.paymentMethodId(),
                        command.transactionReference(), command.cashLocationId(), command.registerId(),
                        command.cashierShiftId());
            }
            return response(existing);
        }

        PurchaseResponse draft = createDraftInternal(business.businessId(), command);
        Purchase purchase = purchaseRepository.findForUpdate(
                        business.businessId(), command.branchId(), draft.id())
                .orElseThrow(PurchaseNotFoundException::new);

        return confirmInternal(
                purchase, itemRepository.findByPurchaseIdOrderByIdAsc(purchase.getId()),
                command.ownerId(), validator.money(command.paidAmount()), command.paymentMethodId(),
                command.transactionReference(), command.cashLocationId(), command.registerId(),
                command.cashierShiftId());
    }

    @Transactional
    public PurchaseResponse updateDraft(Long purchaseId, CreatePurchaseCommand command) {
        validator.validate(command);
        BusinessAccessResponse business = requireBusiness(command.ownerId());
        requireBranch(business.businessId(), command.branchId());
        requireSupplier(business.businessId(), command.branchId(), command.supplierId());

        Purchase purchase = purchaseRepository.findForUpdate(
                        business.businessId(), command.branchId(), purchaseId)
                .orElseThrow(PurchaseNotFoundException::new);
        if (!purchase.isDraft()) throw new PurchaseStateException("Only Draft purchase may be edited");

        PreparedPurchase prepared = preparePurchase(business.businessId(), command);
        Instant now = Instant.now(clock);

        purchase.reviseDraft(
                command.supplierId(), command.purchaseDate(), command.supplierInvoiceReference(), command.notes(),
                command.attachmentReference(), prepared.subtotal(), prepared.itemTaxTotal(),
                prepared.itemDiscountTotal(), prepared.orderTax(), prepared.shippingCharges(),
                prepared.otherCharges(), prepared.purchaseDiscount(), prepared.totalPayable(), now);
        purchase = purchaseRepository.saveAndFlush(purchase);

        itemRepository.deleteByPurchaseId(purchase.getId());
        itemRepository.flush();
        savePreparedItems(purchase.getId(), prepared.items(), now);

        auditRepository.save(PurchaseAuditEvent.record(
                business.businessId(), command.branchId(), purchase.getId(),
                PurchaseAuditAction.PURCHASE_DRAFT_UPDATED, "PURCHASE", purchase.getId(),
                PurchaseActorType.OWNER, command.ownerId(), null, now));

        return response(purchase);
    }

    @Transactional
    public PurchaseResponse confirmDraft(ConfirmPurchaseCommand command) {
        validator.validate(command);
        BusinessAccessResponse business = requireBusiness(command.ownerId());

        Purchase purchase = purchaseRepository.findByIdAndBusinessId(command.purchaseId(), business.businessId())
                .orElseThrow(PurchaseNotFoundException::new);

        purchase = purchaseRepository.findForUpdate(
                        business.businessId(), purchase.getBranchId(), purchase.getId())
                .orElseThrow(PurchaseNotFoundException::new);

        if (!purchase.isDraft()) return response(purchase);

        return confirmInternal(
                purchase, itemRepository.findByPurchaseIdOrderByIdAsc(purchase.getId()),
                command.ownerId(), validator.money(command.paidAmount()), command.paymentMethodId(),
                command.transactionReference(), command.cashLocationId(), command.registerId(),
                command.cashierShiftId());
    }

    @Transactional
    public void deleteDraft(Long ownerId, Long purchaseId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        Purchase purchase = purchaseRepository.findByIdAndBusinessId(purchaseId, business.businessId())
                .orElseThrow(PurchaseNotFoundException::new);
        purchase = purchaseRepository.findForUpdate(
                        business.businessId(), purchase.getBranchId(), purchaseId)
                .orElseThrow(PurchaseNotFoundException::new);

        if (!purchase.isDraft()) {
            throw new PurchaseStateException(
                    "Confirmed or posted Purchase cannot be deleted; use the documented correction workflow");
        }

        Instant now = Instant.now(clock);
        auditRepository.save(PurchaseAuditEvent.record(
                business.businessId(), purchase.getBranchId(), purchase.getId(),
                PurchaseAuditAction.PURCHASE_DRAFT_DELETED, "PURCHASE", purchase.getId(),
                PurchaseActorType.OWNER, ownerId, null, now));

        itemRepository.deleteByPurchaseId(purchase.getId());
        purchaseRepository.delete(purchase);
    }

    @Transactional(readOnly = true)
    public PurchaseResponse findByOwnerAndId(Long ownerId, Long purchaseId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        Purchase purchase = purchaseRepository.findByIdAndBusinessId(purchaseId, business.businessId())
                .orElseThrow(PurchaseNotFoundException::new);
        requireBranch(business.businessId(), purchase.getBranchId());
        return response(purchase);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseResponse> search(Long ownerId, Long branchId, Long supplierId,
                                         com.spark.falcon.purchase.entity.enumtype.PurchaseStatus status,
                                         com.spark.falcon.purchase.entity.enumtype.PurchasePaymentStatus paymentStatus,
                                         LocalDate fromDate, LocalDate toDate, String keyword, Pageable pageable) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        String safeKeyword = keyword == null ? "" : keyword.trim();
        return purchaseRepository.search(business.businessId(), branchId, supplierId, status, paymentStatus,
                        fromDate, toDate, safeKeyword, pageable)
                .map(this::response);
    }

    @Transactional(readOnly = true)
    public PurchaseDetailsResponse findDetails(Long ownerId, Long purchaseId) {
        PurchaseResponse purchase = findByOwnerAndId(ownerId, purchaseId);
        List<PurchaseAuditResponse> audit = auditRepository.findByPurchaseIdOrderByCreatedAtAscIdAsc(purchaseId)
                .stream().map(mapper::toResponse).toList();
        return new PurchaseDetailsResponse(purchase, audit);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseLogResponse> findLogs(Long ownerId, Long branchId, Pageable pageable) {
        return findLogs(ownerId, branchId, null, "", pageable);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseLogResponse> findLogs(Long ownerId, Long branchId, Long supplierId, String query, Pageable pageable) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        return auditRepository.search(business.businessId(), branchId, supplierId, query == null ? "" : query.trim(), pageable)
                .map(event -> {
                    Purchase purchase = event.getPurchaseId() == null ? null
                            : purchaseRepository.findByIdAndBusinessIdAndBranchId(
                                    event.getPurchaseId(), business.businessId(), branchId).orElse(null);
                    String paymentMethod = purchase == null ? null
                            : paymentService.findLatestPurchasePaymentMethod(purchase.getId()).orElse(null);
                    return new PurchaseLogResponse(
                            event.getId(), event.getCreatedAt(), event.getAction(), event.getPurchaseId(),
                            purchase == null ? null : purchase.getSupplierId(), event.getActorId(),
                            purchase == null ? null : purchase.getTotalPayable(), paymentMethod,
                            event.getSubjectType(), event.getSubjectId(), event.getDetails());
                });
    }

    private PurchaseResponse createDraftInternal(Long businessId, CreatePurchaseCommand command) {
        requireBranch(businessId, command.branchId());
        requireSupplier(businessId, command.branchId(), command.supplierId());

        Purchase existing = purchaseRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                businessId, command.branchId(), command.idempotencyKey()).orElse(null);
        if (existing != null) return response(existing);

        PreparedPurchase prepared = preparePurchase(businessId, command);
        Instant now = Instant.now(clock);

        Purchase purchase = Purchase.draft(
                businessId, command.branchId(), command.supplierId(), command.purchaseDate(),
                command.supplierInvoiceReference(), command.notes(), command.attachmentReference(),
                command.idempotencyKey(), prepared.subtotal(), prepared.itemTaxTotal(),
                prepared.itemDiscountTotal(), prepared.orderTax(), prepared.shippingCharges(),
                prepared.otherCharges(), prepared.purchaseDiscount(), prepared.totalPayable(),
                PurchaseActorType.OWNER, command.ownerId(), now);

        try {
            purchase = purchaseRepository.saveAndFlush(purchase);
        } catch (DataIntegrityViolationException ex) {
            Purchase repeated = purchaseRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                    businessId, command.branchId(), command.idempotencyKey()).orElse(null);
            if (repeated != null) return response(repeated);
            throw ex;
        }

        savePreparedItems(purchase.getId(), prepared.items(), now);

        auditRepository.save(PurchaseAuditEvent.record(
                businessId, command.branchId(), purchase.getId(),
                PurchaseAuditAction.PURCHASE_DRAFT_CREATED, "PURCHASE", purchase.getId(),
                PurchaseActorType.OWNER, command.ownerId(), null, now));

        return response(purchase);
    }

    private PurchaseResponse confirmInternal(Purchase purchase, List<PurchaseItem> items, Long actorId,
                                             BigDecimal tenderedAmount, Long paymentMethodId,
                                             String transactionReference, Long cashLocationId,
                                             Long registerId, Long cashierShiftId) {
        if (!purchase.isDraft()) return response(purchase);
        if (items.isEmpty()) throw new PurchaseValidationException("Purchase cannot be confirmed without items");

        requireBranch(purchase.getBusinessId(), purchase.getBranchId());
        requireSupplier(purchase.getBusinessId(), purchase.getBranchId(), purchase.getSupplierId());

        revalidateSnapshots(purchase, items);

        Map<Long, BigDecimal> weightedAverageCostAfterConfirmation = new HashMap<>();
        for (PurchaseItem item : items) {
            StockReceiptRequest stockRequest = new StockReceiptRequest();
            stockRequest.setBusinessId(purchase.getBusinessId());
            stockRequest.setBranchId(purchase.getBranchId());
            stockRequest.setProductVariantId(item.getProductVariantId());
            stockRequest.setEnteredQuantity(item.getEnteredQuantity());
            stockRequest.setEnteredUnitId(item.getEnteredUnitId());
            stockRequest.setTransactionDate(purchase.getPurchaseDate());
            stockRequest.setLandedBaseUnitCost(item.getBaseUnitLandedCost());
            stockRequest.setOriginalPurchaseUnitCost(item.getUnitCost());
            stockRequest.setSupplierId(purchase.getSupplierId());
            stockRequest.setSourcePurchaseItemId(item.getId());
            stockRequest.setBatchNumber(item.getBatchNumber());
            stockRequest.setManufacturingDate(item.getManufacturingDate());
            stockRequest.setExpiryDate(item.getExpiryDate());
            stockRequest.setMovementType(StockMovementType.PURCHASE_RECEIPT);
            stockRequest.setSourceType(StockSourceType.PURCHASE);
            stockRequest.setSourceReferenceId(String.valueOf(purchase.getId()));
            stockRequest.setSourceLineReference(String.valueOf(item.getId()));
            stockRequest.setPostingKey(stockPostingKey(purchase.getId(), item.getId()));
            stockRequest.setActorType(InventoryActorType.OWNER);
            stockRequest.setActorId(actorId);
            stockRequest.setNotes(purchase.getNotes());

            InventoryPostingResponse posting = inventoryPostingService.receiveStock(stockRequest);
            item.attachInventoryPosting(
                    posting.getBatch() == null ? null : posting.getBatch().getId(),
                    posting.getMovement().getId());
            weightedAverageCostAfterConfirmation.put(
                    item.getProductVariantId(), posting.getStock().getWeightedAverageCost());
            itemRepository.saveAndFlush(item);
        }

        for (PurchaseItem item : items) {
            BigDecimal finalWeightedAverageCost = weightedAverageCostAfterConfirmation.get(item.getProductVariantId());
            if (finalWeightedAverageCost == null) {
                throw new PurchaseValidationException("Weighted Average Cost snapshot is unavailable after inventory posting");
            }
            item.captureWeightedAverageCostAfterConfirmation(finalWeightedAverageCost);
            itemRepository.saveAndFlush(item);
        }

        BigDecimal tendered = validator.money(tenderedAmount);
        PaymentMethodResponse paymentMethod = null;
        if (tendered.signum() > 0) {
            if (paymentMethodId == null) {
                throw new PurchaseValidationException("Payment method is required when Paid Amount is greater than zero");
            }
            paymentMethod = paymentMethodAccessService.findActiveForBranch(
                            purchase.getBusinessId(), purchase.getBranchId(), paymentMethodId)
                    .orElseThrow(() -> new PurchaseValidationException(
                            "Payment method is inactive or unavailable for this Branch"));
            if (!paymentMethod.cash() && tendered.compareTo(purchase.getTotalPayable()) > 0) {
                throw new PurchaseValidationException(
                        "Non-cash Paid Amount cannot exceed the Purchase Total Payable");
            }
        }
        BigDecimal appliedPayment = tendered.min(purchase.getTotalPayable());
        BigDecimal change = paymentMethod != null && paymentMethod.cash()
                ? tendered.subtract(appliedPayment)
                : moneyZero();

        Instant now = Instant.now(clock);
        try {
            purchase.confirm(BigDecimal.ZERO.setScale(PurchaseValidator.MONEY_SCALE), change, now);
        } catch (IllegalStateException ex) {
            throw new PurchaseStateException(ex.getMessage());
        }
        purchase = purchaseRepository.saveAndFlush(purchase);

        if (appliedPayment.signum() > 0) {
            paymentPostingService.postInitialSupplierPayment(new InitialSupplierPaymentCommand(
                    purchase.getBusinessId(),
                    purchase.getBranchId(),
                    actorId,
                    purchase.getSupplierId(),
                    purchase.getId(),
                    appliedPayment,
                    paymentMethodId,
                    transactionReference,
                    cashLocationId,
                    registerId,
                    cashierShiftId,
                    purchase.getNotes()
            ));
            purchase = purchaseRepository.findForUpdate(
                            purchase.getBusinessId(), purchase.getBranchId(), purchase.getId())
                    .orElseThrow(PurchaseNotFoundException::new);
        }

        auditRepository.save(PurchaseAuditEvent.record(
                purchase.getBusinessId(), purchase.getBranchId(), purchase.getId(),
                PurchaseAuditAction.PURCHASE_CONFIRMED, "PURCHASE", purchase.getId(),
                PurchaseActorType.OWNER, actorId,
                "Purchase, inventory, supplier due and applicable payment/cash effects posted atomically", now));

        return response(purchase);
    }

    private PreparedPurchase preparePurchase(Long businessId, CreatePurchaseCommand command) {
        List<PreparedBaseItem> baseItems = new ArrayList<>();
        BigDecimal subtotal = moneyZero();
        BigDecimal itemTaxTotal = moneyZero();
        BigDecimal itemDiscountTotal = moneyZero();
        BigDecimal linePayableTotal = moneyZero();

        for (PurchaseItemCommand item : command.items()) {
            ProductVariantAccessResponse variant = productAccessService.findActiveVariantForBranch(
                            businessId, command.branchId(), item.productVariantId())
                    .orElseThrow(() -> new PurchaseValidationException(
                            "Product Variant is inactive or unavailable for this Branch"));

            ResolvedQuantity quantity = resolveQuantity(
                    businessId, command.branchId(), command.purchaseDate(), item, variant);
            validateBatch(businessId, command.branchId(), item, variant, command.purchaseDate());

            BigDecimal enteredQuantity = validator.quantity(item.enteredQuantity());
            BigDecimal unitCost = validator.money(item.unitCost());
            BigDecimal rawAmount = enteredQuantity.multiply(unitCost)
                    .setScale(PurchaseValidator.MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal itemDiscount = validator.money(item.itemDiscount());
            BigDecimal discountedAmount = rawAmount.subtract(itemDiscount)
                    .setScale(PurchaseValidator.MONEY_SCALE, RoundingMode.HALF_UP);
            if (discountedAmount.signum() < 0) {
                throw new PurchaseValidationException("Item discount cannot exceed the Purchase line amount");
            }

            TaxCalculation taxCalculation = calculateTax(businessId, variant, discountedAmount, item.itemTax());
            BigDecimal itemTax = taxCalculation.taxAmount();
            BigDecimal linePayable = taxCalculation.linePayable();

            subtotal = subtotal.add(rawAmount);
            itemTaxTotal = itemTaxTotal.add(itemTax);
            itemDiscountTotal = itemDiscountTotal.add(itemDiscount);
            linePayableTotal = linePayableTotal.add(linePayable);

            baseItems.add(new PreparedBaseItem(
                    item, variant, enteredQuantity, quantity.conversionFactor(), quantity.baseQuantity(),
                    unitCost, itemTax, itemDiscount, linePayable));
        }

        BigDecimal orderTax = validator.money(command.orderTax());
        BigDecimal shipping = validator.money(command.shippingCharges());
        BigDecimal other = validator.money(command.otherCharges());
        BigDecimal purchaseDiscount = validator.money(command.discount());

        BigDecimal totalPayable = linePayableTotal.add(orderTax).add(shipping).add(other).subtract(purchaseDiscount)
                .setScale(PurchaseValidator.MONEY_SCALE, RoundingMode.HALF_UP);
        if (totalPayable.signum() < 0) {
            throw new PurchaseValidationException("Purchase discount cannot make Total Payable negative");
        }

        List<BigDecimal> orderTaxAllocations = allocate(orderTax, baseItems, linePayableTotal);
        List<BigDecimal> shippingAllocations = allocate(shipping, baseItems, linePayableTotal);
        List<BigDecimal> otherAllocations = allocate(other, baseItems, linePayableTotal);
        List<BigDecimal> discountAllocations = allocate(purchaseDiscount, baseItems, linePayableTotal);

        List<PreparedItem> preparedItems = new ArrayList<>();
        for (int i = 0; i < baseItems.size(); i++) {
            PreparedBaseItem base = baseItems.get(i);
            BigDecimal landedAmount = base.linePayable()
                    .add(orderTaxAllocations.get(i))
                    .add(shippingAllocations.get(i))
                    .add(otherAllocations.get(i))
                    .subtract(discountAllocations.get(i))
                    .setScale(PurchaseValidator.MONEY_SCALE, RoundingMode.HALF_UP);

            if (landedAmount.signum() < 0) {
                throw new PurchaseValidationException(
                        "Allocated Purchase discount cannot make an item's landed inventory amount negative");
            }

            BigDecimal baseUnitLandedCost = base.baseQuantity().signum() == 0
                    ? moneyZero()
                    : landedAmount.divide(
                            base.baseQuantity(), PurchaseValidator.MONEY_SCALE, RoundingMode.HALF_UP);

            preparedItems.add(new PreparedItem(
                    base.command(), base.variant(), base.enteredQuantity(), base.conversionFactor(),
                    base.baseQuantity(), base.unitCost(), base.itemTax(), base.itemDiscount(), base.linePayable(),
                    orderTaxAllocations.get(i), discountAllocations.get(i), shippingAllocations.get(i),
                    otherAllocations.get(i), landedAmount, baseUnitLandedCost));
        }

        return new PreparedPurchase(
                subtotal, itemTaxTotal, itemDiscountTotal, orderTax, shipping, other,
                purchaseDiscount, totalPayable, preparedItems);
    }

    private TaxCalculation calculateTax(Long businessId, ProductVariantAccessResponse variant,
                                        BigDecimal discountedAmount, BigDecimal enteredItemTax) {
        if (variant.taxRateId() == null) {
            BigDecimal manualTax = validator.money(enteredItemTax);
            return new TaxCalculation(manualTax,
                    discountedAmount.add(manualTax).setScale(PurchaseValidator.MONEY_SCALE, RoundingMode.HALF_UP));
        }

        TaxRateResponse taxRate = taxRateAccessService.findActive(businessId, variant.taxRateId())
                .orElseThrow(() -> new PurchaseValidationException("Product Tax Rate is inactive or unavailable"));
        BigDecimal rate = taxRate.rate() == null
                ? moneyZero()
                : taxRate.rate().setScale(PurchaseValidator.MONEY_SCALE, RoundingMode.HALF_UP);
        if (rate.signum() == 0) {
            return new TaxCalculation(moneyZero(), discountedAmount);
        }

        String method = variant.taxCalculationMethod() == null
                ? "" : variant.taxCalculationMethod().trim().toUpperCase();
        if ("EXCLUSIVE".equals(method)) {
            BigDecimal tax = discountedAmount.multiply(rate)
                    .divide(HUNDRED, PurchaseValidator.MONEY_SCALE, RoundingMode.HALF_UP);
            return new TaxCalculation(tax,
                    discountedAmount.add(tax).setScale(PurchaseValidator.MONEY_SCALE, RoundingMode.HALF_UP));
        }
        if ("INCLUSIVE".equals(method)) {
            BigDecimal tax = discountedAmount.multiply(rate)
                    .divide(HUNDRED.add(rate), PurchaseValidator.MONEY_SCALE, RoundingMode.HALF_UP);
            return new TaxCalculation(tax, discountedAmount);
        }
        throw new PurchaseValidationException(
                "Tax calculation method must be EXCLUSIVE or INCLUSIVE for a taxable Product");
    }

    private List<BigDecimal> allocate(BigDecimal amount, List<PreparedBaseItem> items, BigDecimal weightTotal) {
        List<BigDecimal> values = new ArrayList<>();
        if (items.isEmpty()) return values;

        BigDecimal allocated = moneyZero();
        for (int i = 0; i < items.size(); i++) {
            BigDecimal value;
            if (i == items.size() - 1) {
                value = amount.subtract(allocated).setScale(PurchaseValidator.MONEY_SCALE, RoundingMode.HALF_UP);
            } else if (amount.signum() == 0) {
                value = moneyZero();
            } else if (weightTotal.signum() > 0) {
                value = amount.multiply(items.get(i).linePayable())
                        .divide(weightTotal, PurchaseValidator.MONEY_SCALE, RoundingMode.HALF_UP);
            } else {
                value = amount.divide(
                        BigDecimal.valueOf(items.size()), PurchaseValidator.MONEY_SCALE, RoundingMode.HALF_UP);
            }
            values.add(value);
            allocated = allocated.add(value);
        }
        return values;
    }

    private List<PurchaseItem> savePreparedItems(Long purchaseId, List<PreparedItem> preparedItems, Instant now) {
        List<PurchaseItem> items = new ArrayList<>();
        for (PreparedItem prepared : preparedItems) {
            PurchaseItemCommand command = prepared.command();
            PurchaseItem item = PurchaseItem.create(
                    purchaseId, command.productVariantId(), prepared.enteredQuantity(), command.enteredUnitId(),
                    prepared.conversionFactor(), prepared.variant().baseInventoryUnitId(), prepared.baseQuantity(),
                    prepared.unitCost(), nullableMoney(command.intendedSellingPrice()), prepared.itemTax(),
                    prepared.itemDiscount(), prepared.linePayable(), prepared.allocatedOrderTax(),
                    prepared.allocatedPurchaseDiscount(), prepared.allocatedShipping(),
                    prepared.allocatedOtherCharges(), prepared.landedAmount(), prepared.baseUnitLandedCost(),
                    command.batchNumber(), command.manufacturingDate(), command.expiryDate(), now);
            items.add(itemRepository.save(item));
        }
        itemRepository.flush();
        return items;
    }

    private void revalidateSnapshots(Purchase purchase, List<PurchaseItem> items) {
        for (PurchaseItem item : items) {
            ProductVariantAccessResponse variant = productAccessService.findActiveVariantForBranch(
                            purchase.getBusinessId(), purchase.getBranchId(), item.getProductVariantId())
                    .orElseThrow(() -> new PurchaseValidationException(
                            "Product Variant became inactive or unavailable before Purchase confirmation"));

            if (!variant.baseInventoryUnitId().equals(item.getBaseInventoryUnitId())) {
                throw new PurchaseValidationException(
                        "Product Variant Base Inventory Unit changed before Purchase confirmation");
            }

            if (item.getEnteredUnitId().equals(item.getBaseInventoryUnitId())) {
                if (item.getConversionFactor().compareTo(BigDecimal.ONE) != 0) {
                    throw new PurchaseValidationException("Stored Purchase conversion snapshot is invalid");
                }
                continue;
            }

            ProductUnitConversionResponse conversion = productAccessService.findEffectiveConversion(
                            purchase.getBusinessId(), purchase.getBranchId(), item.getProductVariantId(),
                            item.getEnteredUnitId(), purchase.getPurchaseDate())
                    .orElseThrow(() -> new PurchaseValidationException(
                            "Purchase Unit Conversion became inactive or invalid before confirmation"));

            if (!conversion.targetUnitId().equals(item.getBaseInventoryUnitId())
                    || conversion.conversionFactor().compareTo(item.getConversionFactor()) != 0) {
                throw new PurchaseValidationException(
                        "Purchase Unit Conversion no longer matches the saved Draft snapshot");
            }
        }
    }

    private ResolvedQuantity resolveQuantity(Long businessId, Long branchId, LocalDate purchaseDate,
                                             PurchaseItemCommand item, ProductVariantAccessResponse variant) {
        BigDecimal enteredQuantity = validator.quantity(item.enteredQuantity());

        if (item.enteredUnitId().equals(variant.baseInventoryUnitId())) {
            return new ResolvedQuantity(ONE, enteredQuantity);
        }

        ProductUnitConversionResponse conversion = productAccessService.findEffectiveConversion(
                        businessId, branchId, variant.variantId(), item.enteredUnitId(), purchaseDate)
                .orElseThrow(() -> new PurchaseValidationException(
                        "No active Product Unit Conversion exists for the Purchase date"));

        if (!conversion.targetUnitId().equals(variant.baseInventoryUnitId())) {
            throw new PurchaseValidationException(
                    "Product Unit Conversion must target the Product Variant Base Inventory Unit");
        }
        if (conversion.conversionFactor() == null || conversion.conversionFactor().signum() <= 0) {
            throw new PurchaseValidationException("Product Unit Conversion factor must be greater than zero");
        }

        int decimals = Math.max(item.enteredQuantity().stripTrailingZeros().scale(), 0);
        if (decimals > conversion.decimalPrecision()) {
            throw new PurchaseValidationException(
                    "Entered Purchase quantity exceeds the Product Unit Conversion decimal precision");
        }

        BigDecimal baseQuantity = validator.quantity(
                item.enteredQuantity().multiply(conversion.conversionFactor()));
        return new ResolvedQuantity(conversion.conversionFactor(), baseQuantity);
    }

    private void validateBatch(Long businessId, Long branchId, PurchaseItemCommand item,
                               ProductVariantAccessResponse variant, LocalDate purchaseDate) {
        boolean batchRequired = variant.trackExpiry() || variant.batchTrackingRequired();
        if (!batchRequired) return;

        if (item.batchNumber() == null || item.batchNumber().isBlank()) {
            throw new PurchaseValidationException("Batch/Lot number is required for this Product Variant");
        }

        if (variant.trackExpiry()) {
            boolean allowMissingExpiryInformation = branchSettingsAccessService
                    .findByBusinessIdAndBranchId(businessId, branchId)
                    .map(settings -> Boolean.TRUE.equals(settings.allowMissingExpiryInformation()))
                    .orElse(false);
            if (!allowMissingExpiryInformation
                    && (item.manufacturingDate() == null || item.expiryDate() == null)) {
                throw new PurchaseValidationException(
                        "Manufacturing date and Expiry/Best-Before date are required for expiry-controlled Product");
            }
            if (item.manufacturingDate() != null && item.expiryDate() != null
                    && !item.expiryDate().isAfter(item.manufacturingDate())) {
                throw new PurchaseValidationException("Expiry date must be later than Manufacturing date");
            }
            if (item.expiryDate() != null && !item.expiryDate().isAfter(purchaseDate)) {
                throw new PurchaseValidationException("Expiry date must be later than Purchase date");
            }
        }
    }

    private PurchaseResponse response(Purchase purchase) {
        return mapper.toResponse(
                purchase, itemRepository.findByPurchaseIdOrderByIdAsc(purchase.getId()));
    }

    private BusinessAccessResponse requireBusiness(Long ownerId) {
        return businessAccessService.findByOwnerId(ownerId)
                .orElseThrow(PurchaseAccessDeniedException::new);
    }

    private void requireBranch(Long businessId, Long branchId) {
        if (branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId).isEmpty()) {
            throw new PurchaseAccessDeniedException();
        }
    }

    private void requireSupplier(Long businessId, Long branchId, Long supplierId) {
        if (supplierService.findActiveForBranch(businessId, branchId, supplierId).isEmpty()) {
            throw new PurchaseValidationException("Supplier is inactive or unavailable for this Branch");
        }
    }

    private BigDecimal nullableMoney(BigDecimal value) {
        return value == null ? null : validator.money(value);
    }

    private BigDecimal moneyZero() {
        return BigDecimal.ZERO.setScale(PurchaseValidator.MONEY_SCALE);
    }

    private String stockPostingKey(Long purchaseId, Long itemId) {
        return "PURCHASE:" + purchaseId + ":ITEM:" + itemId;
    }

    private record TaxCalculation(BigDecimal taxAmount, BigDecimal linePayable) {
    }

    private record ResolvedQuantity(BigDecimal conversionFactor, BigDecimal baseQuantity) {
    }

    private record PreparedBaseItem(
            PurchaseItemCommand command,
            ProductVariantAccessResponse variant,
            BigDecimal enteredQuantity,
            BigDecimal conversionFactor,
            BigDecimal baseQuantity,
            BigDecimal unitCost,
            BigDecimal itemTax,
            BigDecimal itemDiscount,
            BigDecimal linePayable) {
    }

    private record PreparedItem(
            PurchaseItemCommand command,
            ProductVariantAccessResponse variant,
            BigDecimal enteredQuantity,
            BigDecimal conversionFactor,
            BigDecimal baseQuantity,
            BigDecimal unitCost,
            BigDecimal itemTax,
            BigDecimal itemDiscount,
            BigDecimal linePayable,
            BigDecimal allocatedOrderTax,
            BigDecimal allocatedPurchaseDiscount,
            BigDecimal allocatedShipping,
            BigDecimal allocatedOtherCharges,
            BigDecimal landedAmount,
            BigDecimal baseUnitLandedCost) {
    }

    private record PreparedPurchase(
            BigDecimal subtotal,
            BigDecimal itemTaxTotal,
            BigDecimal itemDiscountTotal,
            BigDecimal orderTax,
            BigDecimal shippingCharges,
            BigDecimal otherCharges,
            BigDecimal purchaseDiscount,
            BigDecimal totalPayable,
            List<PreparedItem> items) {
    }
}
