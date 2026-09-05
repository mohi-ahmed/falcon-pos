package com.spark.falcon.purchase.mapper;

import com.spark.falcon.purchase.dto.command.*;
import com.spark.falcon.purchase.dto.request.*;
import com.spark.falcon.purchase.dto.response.*;
import com.spark.falcon.purchase.entity.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PurchaseMapper {

    public CreatePurchaseCommand toCreateCommand(Long ownerId, CreatePurchaseRequest request) {
        List<PurchaseItemCommand> items = request.getItems().stream()
                .map(item -> new PurchaseItemCommand(
                        item.getProductVariantId(), item.getEnteredUnitId(), item.getEnteredQuantity(),
                        item.getUnitCost(), item.getIntendedSellingPrice(), item.getItemTax(), item.getItemDiscount(),
                        item.getBatchNumber(), item.getManufacturingDate(), item.getExpiryDate()))
                .toList();

        return new CreatePurchaseCommand(
                ownerId, request.getBranchId(), request.getSupplierId(), request.getPurchaseDate(),
                request.getSupplierInvoiceReference(), request.getNotes(), request.getAttachmentReference(),
                items, request.getOrderTax(), request.getShippingCharges(), request.getOtherCharges(),
                request.getDiscount(), request.getPaidAmount(), request.getPaymentMethodId(),
                request.getTransactionReference(), request.getCashLocationId(), request.getRegisterId(),
                request.getCashierShiftId(), request.getIdempotencyKey());
    }


    public ConfirmPurchaseCommand toConfirmCommand(Long ownerId, Long purchaseId, ConfirmPurchaseRequest request) {
        return new ConfirmPurchaseCommand(
                ownerId, purchaseId, request.getPaidAmount(), request.getPaymentMethodId(),
                request.getTransactionReference(), request.getCashLocationId(), request.getRegisterId(),
                request.getCashierShiftId());
    }

    public SupplierPaymentCommand toPaymentCommand(Long ownerId, SupplierPaymentRequest request) {
        List<SupplierPaymentAllocationCommand> allocations = request.getAllocations().stream()
                .map(value -> new SupplierPaymentAllocationCommand(value.getPurchaseId(), value.getAmount()))
                .toList();

        return new SupplierPaymentCommand(
                ownerId, request.getBranchId(), request.getSupplierId(), request.getPaymentMethodId(),
                request.getAmount(), request.getTransactionReference(), request.getCashLocationId(),
                request.getRegisterId(), request.getCashierShiftId(), allocations,
                request.isAutomaticOldestDueFirst(), request.getIdempotencyKey(), request.getNotes());
    }

    public PurchaseReturnCommand toReturnCommand(Long ownerId, PurchaseReturnRequest request) {
        List<PurchaseReturnItemCommand> items = request.getItems().stream()
                .map(value -> new PurchaseReturnItemCommand(
                        value.getPurchaseItemId(), value.getReturnUnitId(), value.getReturnQuantity()))
                .toList();

        return new PurchaseReturnCommand(
                ownerId, request.getBranchId(), request.getPurchaseId(), request.getReferenceNumber(),
                request.getReturnDate(), request.getNotes(), request.getSettlementType(),
                request.getPaymentMethodId(), request.getTransactionReference(), request.getCashLocationId(),
                request.getRegisterId(), request.getCashierShiftId(), items, request.getIdempotencyKey());
    }

    public PurchaseResponse toResponse(Purchase purchase, List<PurchaseItem> items) {
        return new PurchaseResponse(
                purchase.getId(), purchase.getBusinessId(), purchase.getBranchId(), purchase.getSupplierId(),
                purchase.getPurchaseDate(), purchase.getSupplierInvoiceReference(), purchase.getNotes(),
                purchase.getAttachmentReference(), purchase.getStatus(), purchase.getPaymentStatus(),
                purchase.getSubtotal(), purchase.getItemTaxTotal(), purchase.getItemDiscountTotal(),
                purchase.getOrderTax(), purchase.getShippingCharges(), purchase.getOtherCharges(),
                purchase.getDiscount(), purchase.getTotalPayable(), purchase.getPaidAmount(),
                purchase.getDueAmount(), purchase.getChangeAmount(), purchase.getReturnedAmount(),
                purchase.getCreatedByActorId(), purchase.getConfirmedAt(), purchase.getCreatedAt(),
                items.stream().map(this::toResponse).toList());
    }

    public PurchaseItemResponse toResponse(PurchaseItem item) {
        return new PurchaseItemResponse(
                item.getId(), item.getPurchaseId(), item.getProductVariantId(), item.getEnteredQuantity(),
                item.getEnteredUnitId(), item.getConversionFactor(), item.getBaseInventoryUnitId(),
                item.getBaseQuantity(), item.getUnitCost(), item.getIntendedSellingPrice(), item.getItemTax(),
                item.getItemDiscount(), item.getLineAmount(), item.getAllocatedOrderTax(),
                item.getAllocatedPurchaseDiscount(), item.getAllocatedShipping(), item.getAllocatedOtherCharges(),
                item.getLandedInventoryAmount(), item.getBaseUnitLandedCost(), item.getBatchNumber(),
                item.getManufacturingDate(), item.getExpiryDate(), item.getProductBatchId(),
                item.getStockMovementId(), item.getWeightedAverageCostAfterConfirmation());
    }

    public SupplierPaymentResponse toResponse(SupplierPayment payment, List<SupplierPaymentAllocation> allocations) {
        return new SupplierPaymentResponse(
                payment.getId(), payment.getBusinessId(), payment.getBranchId(), payment.getSupplierId(),
                payment.getPaymentMethodId(), payment.getPaymentMethodNameSnapshot(),
                payment.getPaymentMethodCodeSnapshot(), payment.isCashPayment(), payment.getAmount(),
                payment.getTransactionReference(), payment.getCashMovementId(), payment.getStatus(),
                payment.getCreatedByActorId(), payment.getCreatedAt(),
                allocations.stream().map(this::toResponse).toList());
    }

    public SupplierPaymentAllocationResponse toResponse(SupplierPaymentAllocation value) {
        return new SupplierPaymentAllocationResponse(
                value.getId(), value.getPurchaseId(), value.getAmount(),
                value.getDueBefore(), value.getDueAfter());
    }

    public PurchaseReturnResponse toResponse(PurchaseReturn value, List<PurchaseReturnItem> items) {
        return new PurchaseReturnResponse(
                value.getId(), value.getBusinessId(), value.getBranchId(), value.getPurchaseId(),
                value.getSupplierId(), value.getReferenceNumber(), value.getReturnDate(), value.getNotes(),
                value.getSettlementType(), value.getPaymentMethodId(), value.getTransactionReference(),
                value.getTotalReturnAmount(), value.getSupplierDueReduction(), value.getSupplierCreditAmount(),
                value.getRefundAmount(), value.getCashMovementId(), value.getStatus(),
                value.getCreatedByActorId(), value.getConfirmedAt(), value.getCreatedAt(),
                items.stream().map(this::toResponse).toList());
    }

    public PurchaseReturnItemResponse toResponse(PurchaseReturnItem item) {
        return new PurchaseReturnItemResponse(
                item.getId(), item.getPurchaseItemId(), item.getProductVariantId(), item.getProductBatchId(),
                item.getEnteredReturnQuantity(), item.getReturnUnitId(), item.getConversionFactor(),
                item.getBaseInventoryUnitId(), item.getBaseQuantity(), item.getSupplierUnitCostSnapshot(),
                item.getAllocatedLandedUnitCostSnapshot(), item.getReturnAmount(), item.getStockMovementId());
    }

    public PurchaseAuditResponse toResponse(PurchaseAuditEvent event) {
        return new PurchaseAuditResponse(
                event.getId(), event.getPurchaseId(), event.getAction(), event.getSubjectType(),
                event.getSubjectId(), event.getActorType(), event.getActorId(), event.getDetails(),
                event.getCreatedAt());
    }

    public StockImportBatchResponse toResponse(StockImportBatch batch, List<StockImportRow> rows) {
        return new StockImportBatchResponse(
                batch.getId(), batch.getBusinessId(), batch.getBranchId(), batch.getImportPurpose(),
                batch.getFileName(), batch.getFileFingerprint(), batch.getTotalRows(), batch.getValidRows(),
                batch.getFailedRows(), batch.getPostedMovementCount(), batch.getErrorSummary(), batch.getStatus(),
                batch.getUploadedByActorId(), batch.getConfirmedByActorId(), batch.getUploadedAt(),
                batch.getConfirmedAt(), batch.getReversedAt(), rows.stream().map(this::toResponse).toList());
    }

    public StockImportRowResponse toResponse(StockImportRow row) {
        return new StockImportRowResponse(
                row.getId(), row.getRowNumber(), row.getProductVariantId(), row.getVariantSku(),
                row.getEnteredUnitId(), row.getEnteredQuantity(), row.getConversionFactor(),
                row.getBaseInventoryUnitId(), row.getBaseQuantity(), row.getCurrentSystemQuantity(),
                row.getQuantityDifference(), row.getOriginalPurchaseUnitCost(), row.getLandedBaseUnitCost(),
                row.getBatchNumber(), row.getManufacturingDate(), row.getExpiryDate(), row.getStatus(),
                row.getValidationMessage(), row.getPostedMovementId(), row.getResultingQuantity(),
                row.getInventoryValueChange());
    }
}
