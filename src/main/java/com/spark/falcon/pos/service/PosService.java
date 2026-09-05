package com.spark.falcon.pos.service;

import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.cashmanagement.dto.CashierShiftResponse;
import com.spark.falcon.cashmanagement.entity.CashLocationStatus;
import com.spark.falcon.cashmanagement.entity.CashierShiftStatus;
import com.spark.falcon.cashmanagement.entity.RegisterStatus;
import com.spark.falcon.cashmanagement.service.CashManagementAccessService;
import com.spark.falcon.customer.dto.CustomerAccessResponse;
import com.spark.falcon.customer.dto.CustomerDuplicateCandidateResponse;
import com.spark.falcon.customer.dto.CustomerRequest;
import com.spark.falcon.customer.dto.CustomerResponse;
import com.spark.falcon.customer.service.CustomerAccessService;
import com.spark.falcon.identity.security.CurrentActorService;
import com.spark.falcon.inventory.dto.InventorySaleBatchAllocationResponse;
import com.spark.falcon.inventory.dto.InventorySellableStockResponse;
import com.spark.falcon.inventory.dto.SaleStockPostingRequest;
import com.spark.falcon.inventory.dto.SaleStockPostingResponse;
import com.spark.falcon.inventory.service.InventoryPostingService;
import com.spark.falcon.inventory.service.InventoryQueryService;
import com.spark.falcon.payment.dto.PaymentResponse;
import com.spark.falcon.payment.dto.SalePaymentCommand;
import com.spark.falcon.payment.service.PaymentPostingService;
import com.spark.falcon.pos.dto.*;
import com.spark.falcon.pos.exception.PosAccessDeniedException;
import com.spark.falcon.pos.exception.PosValidationException;
import com.spark.falcon.product.dto.response.ProductBarcodeAccessResponse;
import com.spark.falcon.product.dto.response.ProductUnitConversionResponse;
import com.spark.falcon.product.dto.response.ProductVariantAccessResponse;
import com.spark.falcon.product.service.ProductAccessService;
import com.spark.falcon.sale.dto.*;
import com.spark.falcon.sale.service.SalePostingService;
import com.spark.falcon.settings.dto.response.PaymentMethodResponse;
import com.spark.falcon.settings.dto.response.TaxRateResponse;
import com.spark.falcon.settings.service.PaymentMethodAccessService;
import com.spark.falcon.settings.service.TaxRateAccessService;
import com.spark.falcon.settings.service.UnitAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PosService {

    private static final int MONEY_SCALE = 4;
    private static final int QUANTITY_SCALE = 8;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final ProductAccessService productAccessService;
    private final InventoryQueryService inventoryQueryService;
    private final InventoryPostingService inventoryPostingService;
    private final CustomerAccessService customerAccessService;
    private final PaymentMethodAccessService paymentMethodAccessService;
    private final PaymentPostingService paymentPostingService;
    private final TaxRateAccessService taxRateAccessService;
    private final UnitAccessService unitAccessService;
    private final CashManagementAccessService cashManagementAccessService;
    private final SalePostingService salePostingService;
    private final CurrentActorService currentActorService;
    private final Clock clock;

    public List<PosProductResponse> findProducts(Long ownerId, Long branchId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        BranchAccessResponse branch = requireBranch(business.businessId(), branchId);
        LocalDate saleDate = branchDate(branch);
        return productAccessService.findActiveVariantsForBranch(business.businessId(), branchId).stream()
                .map(variant -> toPosProduct(business.businessId(), branchId, saleDate, variant))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<PaymentMethodResponse> findPaymentMethods(Long ownerId, Long branchId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        return paymentMethodAccessService.findActiveForBranch(business.businessId(), branchId);
    }

    /**
     * Builds the POS cash-checkout read model and applies only deterministic UI defaults.
     * Cash Management remains authoritative; checkout validation still re-checks the selected
     * shift/register/location before any financial posting.
     */
    public PosCashContextResponse prepareCashContext(Long ownerId, PosRequest request) {
        RequiredContext context = requireContext(ownerId, request);
        Long businessId = context.business().businessId();
        Long branchId = context.branch().branchId();

        var openShifts = cashManagementAccessService.findShifts(businessId, branchId).stream()
                .filter(shift -> shift.getStatus() == CashierShiftStatus.OPEN)
                .toList();
        var activeRegisters = cashManagementAccessService.findRegisters(businessId, branchId).stream()
                .filter(register -> register.getStatus() == RegisterStatus.ACTIVE)
                .toList();
        var activeLocations = cashManagementAccessService.findCashLocations(businessId, branchId).stream()
                .filter(location -> location.getStatus() == CashLocationStatus.ACTIVE)
                .toList();

        CashierShiftResponse selectedShift = null;
        if (request.getCashierShiftId() != null) {
            selectedShift = openShifts.stream()
                    .filter(shift -> request.getCashierShiftId().equals(shift.getId()))
                    .findFirst().orElse(null);
        } else if (openShifts.size() == 1) {
            selectedShift = openShifts.getFirst();
            request.setCashierShiftId(selectedShift.getId());
        }
        if (selectedShift != null) {
            request.setRegisterId(selectedShift.getRegisterId());
        }
        if (request.getCashLocationId() == null && activeLocations.size() == 1) {
            request.setCashLocationId(activeLocations.getFirst().getId());
        }

        var registerNames = activeRegisters.stream().collect(java.util.stream.Collectors.toMap(
                register -> register.getId(), register -> register.getName(), (left, right) -> left));
        var shiftOptions = openShifts.stream().map(shift -> new PosCashContextResponse.ShiftOption(
                shift.getId(), shift.getRegisterId(), shift.getShiftCode(),
                registerNames.getOrDefault(shift.getRegisterId(), "Register #" + shift.getRegisterId())))
                .toList();
        var locationOptions = activeLocations.stream().map(location -> new PosCashContextResponse.CashLocationOption(
                location.getId(), location.getName(), location.getType().name())).toList();
        return new PosCashContextResponse(shiftOptions, locationOptions);
    }

    public List<CustomerAccessResponse> searchCustomers(Long ownerId, String keyword, int limit) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        return customerAccessService.searchActive(business.businessId(), keyword, Math.max(1, Math.min(limit, 25)));
    }

    public CustomerResponse createCustomer(Long ownerId, CustomerRequest request) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        return customerAccessService.createForBusiness(business.businessId(), request);
    }

    public List<CustomerDuplicateCandidateResponse> findCustomerDuplicates(
            Long ownerId, String phone, String email) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        return customerAccessService.findProbableDuplicatesByBusiness(
                business.businessId(), phone, email, null);
    }

    public PosCartResponse prepareCart(Long ownerId, PosRequest request) {
        RequiredContext context = requireContext(ownerId, request);
        return prepareCart(context, request);
    }

    public PosCartResponse scanBarcode(Long ownerId, PosRequest request) {
        RequiredContext context = requireContext(ownerId, request);
        if (request.getBarcode() == null || request.getBarcode().isBlank()) {
            throw new PosValidationException("Barcode is required");
        }
        ProductBarcodeAccessResponse barcode = productAccessService.resolveActiveBarcode(
                        context.business().businessId(), context.branch().branchId(), request.getBarcode().trim())
                .orElseThrow(() -> new PosValidationException(
                        "Barcode is unknown, inactive, deleted or unavailable for this branch"));

        ProductVariantAccessResponse variant = barcode.variant();
        Long expectedSellingUnit = sellingUnit(variant);
        if (!barcode.unitId().equals(expectedSellingUnit)) {
            throw new PosValidationException("Barcode unit does not match the Product Variant selling unit");
        }

        if (request.getItems() == null) request.setItems(new ArrayList<>());
        PosCartLineRequest existing = request.getItems().stream()
                .filter(line -> variant.variantId().equals(line.getProductVariantId())
                        && barcode.unitId().equals(line.getUnitId()))
                .findFirst().orElse(null);
        if (existing == null) {
            PosCartLineRequest line = new PosCartLineRequest();
            line.setProductVariantId(variant.variantId());
            line.setUnitId(barcode.unitId());
            line.setQuantity(BigDecimal.ONE);
            line.setDiscount(BigDecimal.ZERO);
            request.getItems().add(line);
        } else {
            BigDecimal quantity = existing.getQuantity() == null ? BigDecimal.ZERO : existing.getQuantity();
            existing.setQuantity(quantity.add(BigDecimal.ONE));
        }
        request.setBarcode(null);
        return prepareCart(context, request);
    }

    @Transactional
    public SaleResponse hold(Long ownerId, PosRequest request) {
        RequiredContext context = requireContext(ownerId, request);
        SaleResponse repeated = salePostingService.findByIdempotency(
                context.business().businessId(), context.branch().branchId(), request.getIdempotencyKey());
        if (repeated != null) return repeated;

        PosCartResponse cart = prepareCart(context, request);
        CustomerAccessResponse customer = resolveCustomerForHold(context.business().businessId(), request.getCustomerId());
        SaleResponse draft = salePostingService.createDraft(
                createSaleCommand(context, request, cart, customer.id()), createSaleItems(cart));
        return salePostingService.hold(context.business().businessId(), context.branch().branchId(), draft.id());
    }

    @Transactional
    public SaleResponse updateDraftOrHeld(Long ownerId, Long saleId, PosRequest request) {
        RequiredContext context = requireContext(ownerId, request);
        PosCartResponse cart = prepareCart(context, request);
        CustomerAccessResponse customer = resolveCustomerForHold(
                context.business().businessId(), request.getCustomerId());
        return salePostingService.updateDraftOrHeld(
                context.business().businessId(), context.branch().branchId(), saleId,
                createSaleCommand(context, request, cart, customer.id()), createSaleItems(cart));
    }

    @Transactional
    public PosCheckoutResponse checkout(Long ownerId, PosRequest request) {
        prepareCashContext(ownerId, request);
        RequiredContext context = requireContext(ownerId, request);
        SaleResponse repeated = salePostingService.findByIdempotency(
                context.business().businessId(), context.branch().branchId(), request.getIdempotencyKey());
        if (repeated != null) return new PosCheckoutResponse(repeated, null);

        PosCartResponse cart = prepareCart(context, request);
        BigDecimal tendered = money(request.getPaidNow());
        BigDecimal appliedPayment = tendered.min(cart.totalPayable()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal change = tendered.subtract(appliedPayment).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal remainingDue = cart.totalPayable().subtract(appliedPayment).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        CustomerAccessResponse customer = resolveCheckoutCustomer(
                context.business().businessId(), request.getCustomerId(), remainingDue);
        PaymentMethodResponse paymentMethod = validatePaymentBeforePosting(context, request, appliedPayment);

        SaleResponse draft = salePostingService.createDraft(
                createSaleCommand(context, request, cart, customer.id()), createSaleItems(cart));
        List<SaleItemResponse> saleItems = draft.items();
        if (saleItems.size() != cart.items().size()) {
            throw new PosValidationException("Sale Item count does not match the prepared POS cart");
        }

        LocalDate saleDate = branchDate(context.branch());
        for (int i = 0; i < saleItems.size(); i++) {
            SaleItemResponse saleItem = saleItems.get(i);
            PosPreparedLine line = cart.items().get(i);
            SaleStockPostingResponse stockPosting = inventoryPostingService.postSaleStock(
                    new SaleStockPostingRequest(
                            context.business().businessId(), context.branch().branchId(), line.productVariantId(),
                            line.enteredQuantity(), line.unitId(), line.conversionFactor(), line.baseInventoryUnitId(),
                            line.baseQuantity(), saleDate, draft.id(), saleItem.id(), stockPostingKey(draft.id(), saleItem.id()),
                            currentActorService.actorId(ownerId), request.getNotes()));
            List<SaleBatchAllocationCommand> allocations = stockPosting.batchAllocations().stream()
                    .map(this::toSaleAllocation)
                    .toList();
            salePostingService.attachInventoryPosting(
                    context.business().businessId(), context.branch().branchId(), draft.id(), saleItem.id(),
                    stockPosting.movement().getId(), stockPosting.weightedAverageCostSnapshot(), allocations);
        }

        SaleResponse confirmed = salePostingService.confirm(
                context.business().businessId(), context.branch().branchId(), draft.id(), change);
        PaymentResponse payment = null;
        if (appliedPayment.signum() > 0) {
            payment = paymentPostingService.postSalePayment(new SalePaymentCommand(
                    context.business().businessId(), context.branch().branchId(), ownerId, customer.id(),
                    confirmed.id(), appliedPayment, paymentMethod.id(), request.getTransactionReference(),
                    request.getAccountReference(), request.getCashLocationId(), request.getRegisterId(),
                    request.getCashierShiftId(), paymentPostingKey(confirmed.id()), request.getNotes()));
            confirmed = salePostingService.findById(
                    context.business().businessId(), context.branch().branchId(), confirmed.id());
        }
        return new PosCheckoutResponse(confirmed, payment);
    }

    public SaleResponse findSale(Long ownerId, Long branchId, Long saleId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        return salePostingService.findById(business.businessId(), branchId, saleId);
    }

    private PosCartResponse prepareCart(RequiredContext context, PosRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new PosValidationException("POS cart is empty");
        }
        LocalDate saleDate = branchDate(context.branch());
        List<PosPreparedLine> prepared = new ArrayList<>();
        Set<String> uniqueLines = new HashSet<>();
        BigDecimal grossTotal = zero();
        BigDecimal discountTotal = zero();
        BigDecimal taxTotal = zero();
        BigDecimal itemPayableTotal = zero();
        BigDecimal totalEnteredQuantity = BigDecimal.ZERO.setScale(QUANTITY_SCALE);

        for (PosCartLineRequest line : request.getItems()) {
            if (line == null) throw new PosValidationException("POS cart contains an invalid item");
            if (line.getProductVariantId() == null || line.getProductVariantId() <= 0) {
                throw new PosValidationException("Product Variant is required");
            }
            if (line.getUnitId() == null || line.getUnitId() <= 0) {
                throw new PosValidationException("Selling Unit is required");
            }
            String key = line.getProductVariantId() + ":" + line.getUnitId();
            if (!uniqueLines.add(key)) {
                throw new PosValidationException("The same Product Variant and selling unit cannot appear twice in the cart");
            }
            PosPreparedLine item = prepareLine(context, saleDate, line);
            prepared.add(item);
            grossTotal = grossTotal.add(item.grossAmount());
            discountTotal = discountTotal.add(item.discountAmount());
            taxTotal = taxTotal.add(item.taxAmount());
            itemPayableTotal = itemPayableTotal.add(item.linePayable());
            totalEnteredQuantity = totalEnteredQuantity.add(item.enteredQuantity());
        }

        BigDecimal orderDiscount = money(request.getOrderDiscount());
        BigDecimal shipping = money(request.getShippingCharge());
        BigDecimal other = money(request.getOtherCharge());
        BigDecimal beforeOrderDiscount = itemPayableTotal.add(shipping).add(other);
        if (orderDiscount.compareTo(beforeOrderDiscount) > 0) {
            throw new PosValidationException("Order discount cannot exceed the payable amount before order discount");
        }
        BigDecimal totalPayable = beforeOrderDiscount.subtract(orderDiscount)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new PosCartResponse(
                context.business().businessId(), context.branch().branchId(), List.copyOf(prepared), prepared.size(),
                totalEnteredQuantity.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP),
                money(grossTotal), money(discountTotal), money(taxTotal), money(itemPayableTotal),
                orderDiscount, shipping, other, totalPayable);
    }

    private PosPreparedLine prepareLine(RequiredContext context, LocalDate saleDate, PosCartLineRequest line) {
        ProductVariantAccessResponse variant = productAccessService.findActiveVariantForBranch(
                        context.business().businessId(), context.branch().branchId(), line.getProductVariantId())
                .orElseThrow(() -> new PosValidationException(
                        "Product Variant is inactive, deleted or unavailable for this branch"));
        Long sellingUnit = sellingUnit(variant);
        if (!sellingUnit.equals(line.getUnitId())) {
            throw new PosValidationException("Selected unit does not match the Product Variant selling unit");
        }
        if (unitAccessService.findActiveForBranch(
                context.business().businessId(), context.branch().branchId(), line.getUnitId()).isEmpty()) {
            throw new PosValidationException("Selling Unit is inactive or unavailable for this branch");
        }

        BigDecimal quantity = positiveQuantity(line.getQuantity());
        ResolvedConversion conversion = resolveConversion(context, variant, line.getUnitId(), quantity, saleDate);
        boolean batchControlled = variant.trackExpiry() || variant.batchTrackingRequired();
        InventorySellableStockResponse stock = inventoryQueryService.findSellableStock(
                        context.business().businessId(), context.branch().branchId(), variant.variantId(),
                        batchControlled, variant.trackExpiry(), saleDate)
                .orElseThrow(() -> new PosValidationException("Product Variant is out of stock"));
        if (conversion.baseQuantity().compareTo(stock.sellableBaseQuantity()) > 0) {
            throw new PosValidationException(
                    variant.productName() + " sellable stock is lower than the cart quantity");
        }

        BigDecimal unitPrice = money(variant.sellingPrice());
        BigDecimal gross = unitPrice.multiply(quantity).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal discount = money(line.getDiscount());
        if (discount.compareTo(gross) > 0) {
            throw new PosValidationException("Item discount cannot exceed the item gross amount");
        }
        BigDecimal afterDiscount = gross.subtract(discount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        TaxCalculation tax = calculateTax(context.business().businessId(), variant, afterDiscount);

        return new PosPreparedLine(
                variant.variantId(), variant.productName(), safeVariantName(variant), variant.referenceCode(),
                variant.thumbnailReference(), line.getUnitId(), quantity, conversion.factor(),
                variant.baseInventoryUnitId(), conversion.baseQuantity(), stock.sellableBaseQuantity(), unitPrice,
                gross, discount, tax.rate(), tax.method(), tax.taxAmount(), tax.linePayable(), batchControlled);
    }

    private ResolvedConversion resolveConversion(RequiredContext context,
                                                 ProductVariantAccessResponse variant,
                                                 Long unitId,
                                                 BigDecimal quantity,
                                                 LocalDate saleDate) {
        if (unitId.equals(variant.baseInventoryUnitId())) {
            if (decimalPlaces(quantity) > QUANTITY_SCALE) {
                throw new PosValidationException("Quantity exceeds supported precision");
            }
            return new ResolvedConversion(BigDecimal.ONE.setScale(QUANTITY_SCALE),
                    quantity.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY));
        }
        ProductUnitConversionResponse conversion = productAccessService.findEffectiveConversion(
                        context.business().businessId(), context.branch().branchId(), variant.variantId(), unitId, saleDate)
                .orElseThrow(() -> new PosValidationException(
                        "Selling Unit Conversion is inactive, missing or conflicting"));
        if (!conversion.targetUnitId().equals(variant.baseInventoryUnitId())) {
            throw new PosValidationException("Selling Unit Conversion does not target the Base Inventory Unit");
        }
        if (decimalPlaces(quantity) > conversion.decimalPrecision()) {
            throw new PosValidationException("Quantity exceeds the allowed decimal precision for this unit conversion");
        }
        BigDecimal factor = conversion.conversionFactor().setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
        BigDecimal baseQuantity = quantity.multiply(factor).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
        return new ResolvedConversion(factor, baseQuantity);
    }

    private TaxCalculation calculateTax(Long businessId,
                                        ProductVariantAccessResponse variant,
                                        BigDecimal discountedAmount) {
        if (variant.taxRateId() == null) {
            return new TaxCalculation(zero(), variant.taxCalculationMethod(), zero(), discountedAmount);
        }
        TaxRateResponse taxRate = taxRateAccessService.findActive(businessId, variant.taxRateId())
                .orElseThrow(() -> new PosValidationException("Product Tax Rate is inactive or unavailable"));
        BigDecimal rate = taxRate.rate() == null ? zero() : taxRate.rate().setScale(4, RoundingMode.HALF_UP);
        if (rate.signum() == 0) {
            return new TaxCalculation(rate, variant.taxCalculationMethod(), zero(), discountedAmount);
        }
        String method = variant.taxCalculationMethod() == null ? "" : variant.taxCalculationMethod().trim().toUpperCase();
        if ("EXCLUSIVE".equals(method)) {
            BigDecimal tax = discountedAmount.multiply(rate)
                    .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
            return new TaxCalculation(rate, method, tax,
                    discountedAmount.add(tax).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        }
        if ("INCLUSIVE".equals(method)) {
            BigDecimal tax = discountedAmount.multiply(rate)
                    .divide(HUNDRED.add(rate), MONEY_SCALE, RoundingMode.HALF_UP);
            return new TaxCalculation(rate, method, tax, discountedAmount);
        }
        throw new PosValidationException("Tax calculation method must be EXCLUSIVE or INCLUSIVE for a taxable product");
    }

    private CustomerAccessResponse resolveCustomerForHold(Long businessId, Long customerId) {
        if (customerId == null) {
            return customerAccessService.getOrCreateWalkInCustomer(businessId);
        }
        return customerAccessService.findActiveByBusinessIdAndCustomerId(businessId, customerId)
                .orElseThrow(() -> new PosValidationException("Customer is inactive or unavailable"));
    }

    private CustomerAccessResponse resolveCheckoutCustomer(Long businessId, Long customerId, BigDecimal remainingDue) {
        if (remainingDue.signum() > 0) {
            if (customerId == null) {
                throw new PosValidationException("A due Sale requires an identifiable active Customer");
            }
            try {
                return customerAccessService.requireDueEligibleCustomer(businessId, customerId);
            } catch (RuntimeException ex) {
                throw new PosValidationException(ex.getMessage());
            }
        }
        if (customerId == null) {
            return customerAccessService.getOrCreateWalkInCustomer(businessId);
        }
        return customerAccessService.findActiveByBusinessIdAndCustomerId(businessId, customerId)
                .orElseThrow(() -> new PosValidationException("Customer is inactive or unavailable"));
    }

    private PaymentMethodResponse validatePaymentBeforePosting(
            RequiredContext context, PosRequest request, BigDecimal appliedPayment) {
        if (appliedPayment.signum() == 0) return null;
        if (request.getPaymentMethodId() == null || request.getPaymentMethodId() <= 0) {
            throw new PosValidationException("Payment Method is required when Paid Now is greater than zero");
        }
        PaymentMethodResponse method = paymentMethodAccessService.findActiveForBranch(
                        context.business().businessId(), context.branch().branchId(), request.getPaymentMethodId())
                .orElseThrow(() -> new PosValidationException(
                        "Payment Method is inactive or unavailable for this branch"));
        if (method.transactionReferenceRequired()
                && (request.getTransactionReference() == null || request.getTransactionReference().isBlank())) {
            throw new PosValidationException("Transaction reference is required for this Payment Method");
        }
        if (method.cash()) {
            requirePositiveId(request.getCashLocationId(), "Cash Location");
            requirePositiveId(request.getRegisterId(), "Register");
            requirePositiveId(request.getCashierShiftId(), "Cashier Shift");
            CashierShiftResponse shift = cashManagementAccessService.findShift(
                    context.business().businessId(), context.branch().branchId(), request.getCashierShiftId());
            if (shift == null || shift.getStatus() != CashierShiftStatus.OPEN) {
                throw new PosValidationException("An eligible Open Cashier Shift is required for a cash Sale");
            }
            if (!request.getRegisterId().equals(shift.getRegisterId())) {
                throw new PosValidationException("Cashier Shift and Register do not match");
            }
        }
        return method;
    }

    private CreateSaleCommand createSaleCommand(
            RequiredContext context, PosRequest request, PosCartResponse cart, Long customerId) {
        return new CreateSaleCommand(
                context.business().businessId(), context.branch().branchId(), customerId, currentActorService.actorId(context.ownerId()),
                request.getIdempotencyKey().trim(), cart.grossItemTotal(), cart.itemDiscountTotal(),
                cart.itemTaxTotal(), cart.itemPayableTotal(), cart.orderDiscount(), cart.shippingCharge(),
                cart.otherCharge(), cart.totalPayable(), request.getDueDate(), request.getNotes());
    }

    private List<CreateSaleItemCommand> createSaleItems(PosCartResponse cart) {
        return cart.items().stream().map(item -> new CreateSaleItemCommand(
                item.productVariantId(), item.productName(), item.variantName(), item.productCode(),
                item.enteredQuantity(), item.unitId(), item.conversionFactor(), item.baseInventoryUnitId(),
                item.baseQuantity(), item.unitPrice(), item.grossAmount(), item.discountAmount(), item.taxRate(),
                item.taxMethod(), item.taxAmount(), item.linePayable())).toList();
    }

    private PosProductResponse toPosProduct(
            Long businessId, Long branchId, LocalDate saleDate, ProductVariantAccessResponse variant) {
        boolean batchControlled = variant.trackExpiry() || variant.batchTrackingRequired();
        InventorySellableStockResponse stock = inventoryQueryService.findSellableStock(
                businessId, branchId, variant.variantId(), batchControlled, variant.trackExpiry(), saleDate).orElse(null);
        BigDecimal available = stock == null ? BigDecimal.ZERO.setScale(QUANTITY_SCALE) : stock.sellableBaseQuantity();
        return new PosProductResponse(
                variant.productId(), variant.variantId(), variant.productName(), safeVariantName(variant),
                variant.referenceCode(), variant.sku(), variant.thumbnailReference(), sellingUnit(variant),
                money(variant.sellingPrice()), available, variant.baseInventoryUnitId(), batchControlled);
    }

    private SaleBatchAllocationCommand toSaleAllocation(InventorySaleBatchAllocationResponse value) {
        return new SaleBatchAllocationCommand(
                value.productBatchId(), value.batchNumber(), value.expiryDate(), value.baseQuantity());
    }

    private RequiredContext requireContext(Long ownerId, PosRequest request) {
        if (request == null) throw new PosValidationException("POS request is required");
        if (request.getBranchId() == null || request.getBranchId() <= 0) {
            throw new PosValidationException("Branch is required");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new PosValidationException("Idempotency key is required");
        }
        if (request.getIdempotencyKey().trim().length() > 100) {
            throw new PosValidationException("Idempotency key is too long");
        }
        BusinessAccessResponse business = requireBusiness(ownerId);
        BranchAccessResponse branch = requireBranch(business.businessId(), request.getBranchId());
        return new RequiredContext(ownerId, business, branch);
    }

    private BusinessAccessResponse requireBusiness(Long ownerId) {
        if (ownerId == null || ownerId <= 0) throw new PosAccessDeniedException();
        return businessAccessService.findByOwnerId(ownerId).orElseThrow(PosAccessDeniedException::new);
    }

    private BranchAccessResponse requireBranch(Long businessId, Long branchId) {
        return branchAccessService.findActiveByBusinessIdAndBranchId(businessId, branchId)
                .orElseThrow(PosAccessDeniedException::new);
    }

    private LocalDate branchDate(BranchAccessResponse branch) {
        return LocalDate.now(clock.withZone(ZoneId.of(branch.timeZone())));
    }

    private Long sellingUnit(ProductVariantAccessResponse variant) {
        return variant.sellingUnitId() == null ? variant.baseInventoryUnitId() : variant.sellingUnitId();
    }

    private String safeVariantName(ProductVariantAccessResponse variant) {
        return variant.variantName() == null || variant.variantName().isBlank()
                ? variant.productName() : variant.variantName();
    }

    private BigDecimal positiveQuantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new PosValidationException("Cart quantity must be greater than zero");
        }
        if (decimalPlaces(value) > QUANTITY_SCALE) {
            throw new PosValidationException("Cart quantity exceeds supported precision");
        }
        return value.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) return zero();
        if (value.signum() < 0) throw new PosValidationException("Money values must not be negative");
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private int decimalPlaces(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return Math.max(normalized.scale(), 0);
    }

    private void requirePositiveId(Long value, String field) {
        if (value == null || value <= 0) {
            throw new PosValidationException(field + " is required");
        }
    }

    private String stockPostingKey(Long saleId, Long saleItemId) {
        return limitedKey("SALE:" + saleId + ":" + saleItemId);
    }

    private String paymentPostingKey(Long saleId) {
        return limitedKey("POS-PAY:" + saleId);
    }

    private String limitedKey(String value) {
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    private record RequiredContext(Long ownerId, BusinessAccessResponse business, BranchAccessResponse branch) {
    }

    private record ResolvedConversion(BigDecimal factor, BigDecimal baseQuantity) {
    }

    private record TaxCalculation(BigDecimal rate, String method, BigDecimal taxAmount, BigDecimal linePayable) {
    }
}
