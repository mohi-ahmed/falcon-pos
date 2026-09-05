package com.spark.falcon.purchase.validation;

import com.spark.falcon.purchase.dto.command.*;
import com.spark.falcon.purchase.exception.PurchaseValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class PurchaseValidator {

    public static final int MONEY_SCALE = 4;
    public static final int QUANTITY_SCALE = 8;

    public void validate(CreatePurchaseCommand command) {
        if (command == null) throw new PurchaseValidationException("Purchase command is required");
        positiveId(command.ownerId(), "ownerId");
        positiveId(command.branchId(), "branchId");
        positiveId(command.supplierId(), "supplierId");
        if (command.purchaseDate() == null) throw new PurchaseValidationException("Purchase date is required");
        requiredText(command.idempotencyKey(), 100, "Idempotency key");
        optionalText(command.supplierInvoiceReference(), 120, "Supplier invoice reference");
        optionalText(command.notes(), 1000, "Notes");
        optionalText(command.attachmentReference(), 500, "Attachment reference");

        List<PurchaseItemCommand> items = command.items();
        if (items == null || items.isEmpty()) throw new PurchaseValidationException("At least one purchase item is required");

        for (PurchaseItemCommand item : items) validate(item);

        notNegative(command.orderTax(), "Order tax");
        notNegative(command.shippingCharges(), "Shipping charges");
        notNegative(command.otherCharges(), "Other charges");
        notNegative(command.discount(), "Discount");
        notNegative(command.paidAmount(), "Paid amount");

        if (command.paidAmount().signum() > 0) {
            positiveId(command.paymentMethodId(), "paymentMethodId");
        }
        optionalText(command.transactionReference(), 160, "Transaction reference");
    }

    public void validate(PurchaseItemCommand item) {
        if (item == null) throw new PurchaseValidationException("Purchase item is required");
        positiveId(item.productVariantId(), "productVariantId");
        positiveId(item.enteredUnitId(), "enteredUnitId");
        positive(item.enteredQuantity(), "Entered quantity");
        notNegative(item.unitCost(), "Unit cost");
        if (item.intendedSellingPrice() != null) notNegative(item.intendedSellingPrice(), "Intended selling price");
        notNegative(zeroIfNull(item.itemTax()), "Item tax");
        notNegative(zeroIfNull(item.itemDiscount()), "Item discount");
        optionalText(item.batchNumber(), 100, "Batch number");
    }

    public void validate(ConfirmPurchaseCommand command) {
        if (command == null) throw new PurchaseValidationException("Confirm Purchase command is required");
        positiveId(command.ownerId(), "ownerId");
        positiveId(command.purchaseId(), "purchaseId");
        notNegative(zeroIfNull(command.paidAmount()), "Paid amount");
        if (zeroIfNull(command.paidAmount()).signum() > 0) positiveId(command.paymentMethodId(), "paymentMethodId");
        optionalText(command.transactionReference(), 160, "Transaction reference");
    }

    public void validate(SupplierPaymentCommand command) {
        if (command == null) throw new PurchaseValidationException("Supplier payment command is required");
        positiveId(command.ownerId(), "ownerId");
        positiveId(command.branchId(), "branchId");
        positiveId(command.supplierId(), "supplierId");
        positiveId(command.paymentMethodId(), "paymentMethodId");
        positive(command.amount(), "Payment amount");
        requiredText(command.idempotencyKey(), 100, "Idempotency key");
        optionalText(command.transactionReference(), 160, "Transaction reference");
        optionalText(command.notes(), 1000, "Notes");

        List<SupplierPaymentAllocationCommand> allocations = command.allocations() == null
                ? List.of() : command.allocations();

        if (!command.automaticOldestDueFirst() && allocations.isEmpty()) {
            throw new PurchaseValidationException("Manual supplier payment requires invoice allocations");
        }
        if (command.automaticOldestDueFirst() && !allocations.isEmpty()) {
            throw new PurchaseValidationException("Choose either automatic allocation or manual allocation, not both");
        }

        Set<Long> purchaseIds = new HashSet<>();
        for (SupplierPaymentAllocationCommand allocation : allocations) {
            if (allocation == null) throw new PurchaseValidationException("Supplier payment allocation is required");
            positiveId(allocation.purchaseId(), "purchaseId");
            positive(allocation.amount(), "Allocation amount");
            if (!purchaseIds.add(allocation.purchaseId())) {
                throw new PurchaseValidationException("The same Purchase invoice cannot be allocated twice");
            }
        }
    }

    public void validate(PurchaseReturnCommand command) {
        if (command == null) throw new PurchaseValidationException("Purchase return command is required");
        positiveId(command.ownerId(), "ownerId");
        positiveId(command.branchId(), "branchId");
        positiveId(command.purchaseId(), "purchaseId");
        requiredText(command.referenceNumber(), 100, "Purchase return reference");
        if (command.returnDate() == null) throw new PurchaseValidationException("Return date is required");
        if (command.settlementType() == null) throw new PurchaseValidationException("Return settlement type is required");
        requiredText(command.idempotencyKey(), 100, "Idempotency key");
        optionalText(command.notes(), 1000, "Notes");
        optionalText(command.transactionReference(), 160, "Transaction reference");

        if (command.items() == null || command.items().isEmpty()) {
            throw new PurchaseValidationException("At least one Purchase Return item is required");
        }

        Set<Long> itemIds = new HashSet<>();
        for (PurchaseReturnItemCommand item : command.items()) {
            if (item == null) throw new PurchaseValidationException("Purchase Return item is required");
            positiveId(item.purchaseItemId(), "purchaseItemId");
            positiveId(item.returnUnitId(), "returnUnitId");
            positive(item.returnQuantity(), "Return quantity");
            if (!itemIds.add(item.purchaseItemId())) {
                throw new PurchaseValidationException("The same Purchase item cannot be returned twice in one request");
            }
        }
    }

    public BigDecimal money(BigDecimal value) {
        BigDecimal safe = zeroIfNull(value);
        try {
            return safe.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        } catch (ArithmeticException ex) {
            throw new PurchaseValidationException("Invalid money precision");
        }
    }

    public BigDecimal quantity(BigDecimal value) {
        if (value == null) throw new PurchaseValidationException("Quantity is required");
        try {
            return value.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new PurchaseValidationException("Quantity exceeds supported precision");
        }
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) throw new PurchaseValidationException(field + " must be greater than zero");
    }

    private void notNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) throw new PurchaseValidationException(field + " must not be negative");
    }

    private void positiveId(Long value, String field) {
        if (value == null || value <= 0) throw new PurchaseValidationException(field + " must be a positive id");
    }

    private String requiredText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) throw new PurchaseValidationException(field + " is required");
        String result = value.trim();
        if (result.length() > maxLength) throw new PurchaseValidationException(field + " exceeds " + maxLength + " characters");
        return result;
    }

    private void optionalText(String value, int maxLength, String field) {
        if (value != null && !value.isBlank() && value.trim().length() > maxLength) {
            throw new PurchaseValidationException(field + " exceeds " + maxLength + " characters");
        }
    }
}
