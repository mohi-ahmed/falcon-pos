package com.spark.falcon.purchase.service;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.dto.response.BusinessAccessResponse;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.cashmanagement.dto.CashMovementRequest;
import com.spark.falcon.cashmanagement.dto.CashMovementResponse;
import com.spark.falcon.cashmanagement.entity.CashMovementDirection;
import com.spark.falcon.cashmanagement.entity.CashMovementType;
import com.spark.falcon.cashmanagement.entity.CashSourceModule;
import com.spark.falcon.cashmanagement.service.CashManagementPostingService;
import com.spark.falcon.inventory.dto.InventoryPostingResponse;
import com.spark.falcon.inventory.dto.PurchaseReturnStockRequest;
import com.spark.falcon.inventory.entity.InventoryActorType;
import com.spark.falcon.inventory.service.InventoryPostingService;
import com.spark.falcon.purchase.dto.command.PurchaseReturnCommand;
import com.spark.falcon.purchase.dto.command.PurchaseReturnItemCommand;
import com.spark.falcon.purchase.dto.response.PurchaseReturnResponse;
import com.spark.falcon.purchase.dto.response.PurchaseAuditResponse;
import com.spark.falcon.purchase.dto.response.PurchaseReturnDetailsResponse;
import com.spark.falcon.purchase.entity.*;
import com.spark.falcon.purchase.entity.enumtype.*;
import com.spark.falcon.purchase.exception.*;
import com.spark.falcon.purchase.mapper.PurchaseMapper;
import com.spark.falcon.purchase.repository.*;
import com.spark.falcon.purchase.validation.PurchaseValidator;
import com.spark.falcon.settings.dto.response.PaymentMethodResponse;
import com.spark.falcon.settings.service.PaymentMethodAccessService;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseReturnService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final PurchaseReturnRepository returnRepository;
    private final PurchaseReturnItemRepository returnItemRepository;
    private final PurchaseAuditEventRepository auditRepository;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final PaymentMethodAccessService paymentMethodAccessService;
    private final CashManagementPostingService cashManagementPostingService;
    private final InventoryPostingService inventoryPostingService;
    private final PurchaseMapper mapper;
    private final PurchaseValidator validator;
    private final Clock clock;

    @Transactional
    public PurchaseReturnResponse createDraft(PurchaseReturnCommand command) {
        validator.validate(command);
        BusinessAccessResponse business = requireBusiness(command.ownerId());
        return createDraftInternal(business.businessId(), command);
    }

    @Transactional
    public PurchaseReturnResponse createAndConfirm(PurchaseReturnCommand command) {
        validator.validate(command);
        BusinessAccessResponse business = requireBusiness(command.ownerId());

        PurchaseReturn repeated = returnRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                business.businessId(), command.branchId(), command.idempotencyKey()).orElse(null);
        if (repeated != null) {
            if (repeated.isDraft()) return confirmInternal(repeated, command.ownerId());
            return response(repeated);
        }

        PurchaseReturnResponse draft = createDraftInternal(business.businessId(), command);
        PurchaseReturn value = returnRepository.findForUpdate(
                        business.businessId(), command.branchId(), draft.id())
                .orElseThrow(PurchaseReturnNotFoundException::new);
        return confirmInternal(value, command.ownerId());
    }

    @Transactional
    public PurchaseReturnResponse updateDraft(Long returnId, PurchaseReturnCommand command) {
        validator.validate(command);
        BusinessAccessResponse business = requireBusiness(command.ownerId());
        requireBranch(business.businessId(), command.branchId());

        PurchaseReturn value = returnRepository.findForUpdate(
                        business.businessId(), command.branchId(), returnId)
                .orElseThrow(PurchaseReturnNotFoundException::new);
        if (!value.isDraft()) throw new PurchaseStateException("Only Draft purchase return may be edited");
        if (!value.getPurchaseId().equals(command.purchaseId())) {
            throw new PurchaseValidationException("Draft Purchase Return cannot be moved to another Purchase");
        }

        Purchase purchase = purchaseRepository.findForUpdate(
                        business.businessId(), command.branchId(), command.purchaseId())
                .orElseThrow(PurchaseNotFoundException::new);
        validateReturnablePurchase(purchase);

        if (returnRepository.existsByBusinessIdAndBranchIdAndReferenceNumberIgnoreCaseAndIdNot(
                business.businessId(), command.branchId(), command.referenceNumber(), value.getId())) {
            throw new PurchaseValidationException("Purchase Return reference number is already used");
        }

        PreparedReturn prepared = prepareReturn(purchase, command.items());
        validateSettlementBeforePosting(purchase, prepared.totalReturnAmount(), command.settlementType());

        Instant now = Instant.now(clock);
        value.reviseDraft(
                command.referenceNumber(), command.returnDate(), command.notes(), command.settlementType(),
                command.paymentMethodId(), command.transactionReference(), prepared.totalReturnAmount(), now);
        value = returnRepository.saveAndFlush(value);

        returnItemRepository.deleteByPurchaseReturnId(value.getId());
        returnItemRepository.flush();
        saveItems(value.getId(), prepared.items(), now);

        auditRepository.save(PurchaseAuditEvent.record(
                business.businessId(), command.branchId(), purchase.getId(),
                PurchaseAuditAction.PURCHASE_RETURN_DRAFT_UPDATED, "PURCHASE_RETURN", value.getId(),
                PurchaseActorType.OWNER, command.ownerId(), null, now));
        return response(value);
    }

    @Transactional
    public PurchaseReturnResponse confirm(Long ownerId, Long returnId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        PurchaseReturn value = returnRepository.findById(returnId)
                .filter(item -> item.getBusinessId().equals(business.businessId()))
                .orElseThrow(PurchaseReturnNotFoundException::new);
        value = returnRepository.findForUpdate(
                        business.businessId(), value.getBranchId(), returnId)
                .orElseThrow(PurchaseReturnNotFoundException::new);
        if (!value.isDraft()) return response(value);
        return confirmInternal(value, ownerId);
    }

    @Transactional
    public void deleteDraft(Long ownerId, Long returnId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        PurchaseReturn value = returnRepository.findById(returnId)
                .filter(item -> item.getBusinessId().equals(business.businessId()))
                .orElseThrow(PurchaseReturnNotFoundException::new);
        value = returnRepository.findForUpdate(
                        business.businessId(), value.getBranchId(), returnId)
                .orElseThrow(PurchaseReturnNotFoundException::new);

        if (!value.isDraft()) {
            throw new PurchaseStateException(
                    "Confirmed Purchase Return cannot be deleted; correction requires an authorized reversal");
        }

        Instant now = Instant.now(clock);
        auditRepository.save(PurchaseAuditEvent.record(
                business.businessId(), value.getBranchId(), value.getPurchaseId(),
                PurchaseAuditAction.PURCHASE_RETURN_DRAFT_DELETED, "PURCHASE_RETURN", value.getId(),
                PurchaseActorType.OWNER, ownerId, null, now));

        returnItemRepository.deleteByPurchaseReturnId(value.getId());
        returnRepository.delete(value);
    }

    @Transactional(readOnly = true)
    public PurchaseReturnResponse findByOwnerAndId(Long ownerId, Long returnId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        PurchaseReturn value = returnRepository.findById(returnId)
                .filter(item -> item.getBusinessId().equals(business.businessId()))
                .orElseThrow(PurchaseReturnNotFoundException::new);
        requireBranch(business.businessId(), value.getBranchId());
        return response(value);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseReturnResponse> search(Long ownerId, Long branchId, Long supplierId,
                                               PurchaseReturnStatus status, LocalDate fromDate, LocalDate toDate,
                                               String keyword, Pageable pageable) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        String safeKeyword = keyword == null ? "" : keyword.trim();
        return returnRepository.search(business.businessId(), branchId, supplierId, status, fromDate, toDate,
                        safeKeyword, pageable)
                .map(this::response);
    }

    @Transactional(readOnly = true)
    public PurchaseReturnDetailsResponse findDetails(Long ownerId, Long returnId) {
        PurchaseReturnResponse value = findByOwnerAndId(ownerId, returnId);
        List<PurchaseAuditResponse> audit = auditRepository.findByPurchaseIdOrderByCreatedAtAscIdAsc(value.purchaseId())
                .stream()
                .filter(event -> "PURCHASE_RETURN".equals(event.getSubjectType())
                        && value.id().equals(event.getSubjectId()))
                .map(mapper::toResponse)
                .toList();
        return new PurchaseReturnDetailsResponse(value, audit);
    }

    private PurchaseReturnResponse createDraftInternal(Long businessId, PurchaseReturnCommand command) {
        requireBranch(businessId, command.branchId());

        PurchaseReturn repeated = returnRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                businessId, command.branchId(), command.idempotencyKey()).orElse(null);
        if (repeated != null) return response(repeated);

        if (returnRepository.existsByBusinessIdAndBranchIdAndReferenceNumberIgnoreCase(
                businessId, command.branchId(), command.referenceNumber())) {
            throw new PurchaseValidationException("Purchase Return reference number is already used");
        }

        Purchase purchase = purchaseRepository.findForUpdate(
                        businessId, command.branchId(), command.purchaseId())
                .orElseThrow(PurchaseNotFoundException::new);
        validateReturnablePurchase(purchase);

        PreparedReturn prepared = prepareReturn(purchase, command.items());
        validateSettlementBeforePosting(purchase, prepared.totalReturnAmount(), command.settlementType());

        Instant now = Instant.now(clock);
        PurchaseReturn value = PurchaseReturn.draft(
                businessId, command.branchId(), purchase.getId(), purchase.getSupplierId(),
                command.referenceNumber(), command.returnDate(), command.notes(), command.settlementType(),
                command.paymentMethodId(), command.transactionReference(), prepared.totalReturnAmount(),
                command.idempotencyKey(), PurchaseActorType.OWNER, command.ownerId(), now);

        try {
            value = returnRepository.saveAndFlush(value);
        } catch (DataIntegrityViolationException ex) {
            PurchaseReturn existing = returnRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                    businessId, command.branchId(), command.idempotencyKey()).orElse(null);
            if (existing != null) return response(existing);
            throw ex;
        }

        saveItems(value.getId(), prepared.items(), now);

        auditRepository.save(PurchaseAuditEvent.record(
                businessId, command.branchId(), purchase.getId(),
                PurchaseAuditAction.PURCHASE_RETURN_DRAFT_CREATED, "PURCHASE_RETURN", value.getId(),
                PurchaseActorType.OWNER, command.ownerId(), null, now));

        return response(value);
    }

    private PurchaseReturnResponse confirmInternal(PurchaseReturn value, Long actorId) {
        if (!value.isDraft()) return response(value);

        Purchase purchase = purchaseRepository.findForUpdate(
                        value.getBusinessId(), value.getBranchId(), value.getPurchaseId())
                .orElseThrow(PurchaseNotFoundException::new);
        validateReturnablePurchase(purchase);

        List<PurchaseReturnItem> items = returnItemRepository.findByPurchaseReturnIdOrderByIdAsc(value.getId());
        if (items.isEmpty()) throw new PurchaseValidationException("Purchase Return cannot be confirmed without items");

        revalidateEligibleQuantities(items);
        validateSettlementBeforePosting(purchase, value.getTotalReturnAmount(), value.getSettlementType());

        PaymentMethodResponse refundMethod = null;
        BigDecimal dueBefore = purchase.getDueAmount();
        BigDecimal expectedDueReduction = value.getTotalReturnAmount().min(dueBefore);
        BigDecimal expectedRemainder = value.getTotalReturnAmount().subtract(expectedDueReduction);

        if (value.getSettlementType() == PurchaseReturnSettlementType.REFUND
                && expectedRemainder.signum() > 0) {
            refundMethod = requirePaymentMethod(
                    value.getBusinessId(), value.getBranchId(), value.getPaymentMethodId(),
                    value.getTransactionReference());
        }

        for (PurchaseReturnItem item : items) {
            PurchaseReturnStockRequest request = new PurchaseReturnStockRequest();
            request.setBusinessId(value.getBusinessId());
            request.setBranchId(value.getBranchId());
            request.setProductVariantId(item.getProductVariantId());
            request.setProductBatchId(item.getProductBatchId());
            request.setEnteredReturnQuantity(item.getEnteredReturnQuantity());
            request.setReturnUnitId(item.getReturnUnitId());
            request.setConversionFactorSnapshot(item.getConversionFactor());
            request.setBaseInventoryUnitIdSnapshot(item.getBaseInventoryUnitId());
            request.setBaseQuantity(item.getBaseQuantity());
            request.setPreservedAllocatedLandedUnitCost(item.getAllocatedLandedUnitCostSnapshot());
            request.setSourceReferenceId(String.valueOf(value.getId()));
            request.setSourceLineReference(String.valueOf(item.getId()));
            request.setPostingKey("PURCHASE-RETURN:" + value.getId() + ":ITEM:" + item.getId());
            request.setActorType(InventoryActorType.OWNER);
            request.setActorId(actorId);
            request.setReason("Purchase Return");
            request.setNotes(value.getNotes());

            InventoryPostingResponse posting = inventoryPostingService.returnPurchaseStock(request);
            item.attachStockMovement(posting.getMovement().getId());
            returnItemRepository.saveAndFlush(item);
        }

        Instant now = Instant.now(clock);
        BigDecimal dueReduction;
        try {
            dueReduction = purchase.applyReturn(value.getTotalReturnAmount(), now);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new PurchaseStateException(ex.getMessage());
        }
        purchaseRepository.saveAndFlush(purchase);

        BigDecimal settlementRemainder = value.getTotalReturnAmount().subtract(dueReduction);
        BigDecimal supplierCredit = BigDecimal.ZERO.setScale(PurchaseValidator.MONEY_SCALE);
        BigDecimal refund = BigDecimal.ZERO.setScale(PurchaseValidator.MONEY_SCALE);
        Long cashMovementId = null;

        if (settlementRemainder.signum() > 0) {
            if (value.getSettlementType() == PurchaseReturnSettlementType.SUPPLIER_CREDIT) {
                supplierCredit = settlementRemainder;
            } else if (value.getSettlementType() == PurchaseReturnSettlementType.REFUND) {
                refund = settlementRemainder;
                if (refundMethod != null && refundMethod.cash()) {
                    CashMovementRequest cash = new CashMovementRequest();
                    cash.setSourceModule(CashSourceModule.PURCHASE);
                    cash.setSourceTransactionId(String.valueOf(value.getId()));
                    cash.setSourceReference(value.getReferenceNumber());
                    cash.setMovementType(CashMovementType.SUPPLIER_REFUND_RECEIVED);
                    cash.setDirection(CashMovementDirection.INFLOW);
                    cash.setAmount(refund);
                    cash.setPostedByUserId(actorId);
                    cash.setPostingKey(limitedKey("PR:" + value.getIdempotencyKey()));
                    cash.setNote(value.getNotes());

                    CashMovementResponse movement = cashManagementPostingService.post(
                            value.getBusinessId(), value.getBranchId(), cash);
                    cashMovementId = movement.getId();
                }
            }
        }

        value.confirm(dueReduction, supplierCredit, refund, cashMovementId, now);
        value = returnRepository.saveAndFlush(value);

        auditRepository.save(PurchaseAuditEvent.record(
                value.getBusinessId(), value.getBranchId(), purchase.getId(),
                PurchaseAuditAction.PURCHASE_RETURN_CONFIRMED, "PURCHASE_RETURN", value.getId(),
                PurchaseActorType.OWNER, actorId,
                "Purchase Return stock, supplier due/credit/refund and applicable cash effect posted atomically", now));

        return response(value);
    }

    private PreparedReturn prepareReturn(Purchase purchase, List<PurchaseReturnItemCommand> commands) {
        List<PreparedReturnItem> prepared = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(PurchaseValidator.MONEY_SCALE);

        for (PurchaseReturnItemCommand command : commands) {
            PurchaseItem purchaseItem = purchaseItemRepository.findByIdAndPurchaseId(
                            command.purchaseItemId(), purchase.getId())
                    .orElseThrow(() -> new PurchaseValidationException(
                            "Purchase Return item does not belong to the selected Purchase"));

            BigDecimal returnedAlready = returnItemRepository.sumConfirmedReturnedBaseQuantity(purchaseItem.getId());
            if (returnedAlready == null) returnedAlready = BigDecimal.ZERO;
            BigDecimal eligible = purchaseItem.getBaseQuantity().subtract(returnedAlready);

            ResolvedReturnQuantity quantity = resolveReturnQuantity(purchaseItem, command);
            if (quantity.baseQuantity().compareTo(eligible) > 0) {
                throw new PurchaseValidationException(
                        "Purchase Return quantity exceeds the original eligible purchased quantity");
            }

            BigDecimal ratio = quantity.baseQuantity().divide(
                    purchaseItem.getBaseQuantity(), 12, RoundingMode.HALF_UP);
            BigDecimal returnAmount = purchaseItem.getLineAmount().multiply(ratio)
                    .setScale(PurchaseValidator.MONEY_SCALE, RoundingMode.HALF_UP);

            prepared.add(new PreparedReturnItem(
                    purchaseItem, command, quantity.conversionFactor(), quantity.baseQuantity(), returnAmount));
            total = total.add(returnAmount);
        }

        if (total.signum() <= 0) {
            throw new PurchaseValidationException("Purchase Return amount must be greater than zero");
        }
        return new PreparedReturn(total, prepared);
    }

    private ResolvedReturnQuantity resolveReturnQuantity(
            PurchaseItem purchaseItem, PurchaseReturnItemCommand command) {
        BigDecimal entered = validator.quantity(command.returnQuantity());

        if (command.returnUnitId().equals(purchaseItem.getBaseInventoryUnitId())) {
            return new ResolvedReturnQuantity(
                    BigDecimal.ONE.setScale(PurchaseValidator.QUANTITY_SCALE), entered);
        }

        if (!command.returnUnitId().equals(purchaseItem.getEnteredUnitId())) {
            throw new PurchaseValidationException(
                    "Purchase Return must use the original Purchase Unit or its Base Inventory Unit snapshot");
        }

        BigDecimal base = validator.quantity(
                entered.multiply(purchaseItem.getConversionFactor()));
        return new ResolvedReturnQuantity(purchaseItem.getConversionFactor(), base);
    }

    private List<PurchaseReturnItem> saveItems(
            Long returnId, List<PreparedReturnItem> prepared, Instant now) {
        List<PurchaseReturnItem> result = new ArrayList<>();
        for (PreparedReturnItem value : prepared) {
            PurchaseItem purchaseItem = value.purchaseItem();
            PurchaseReturnItem item = PurchaseReturnItem.create(
                    returnId, purchaseItem.getId(), purchaseItem.getProductVariantId(),
                    purchaseItem.getProductBatchId(), validator.quantity(value.command().returnQuantity()),
                    value.command().returnUnitId(), value.conversionFactor(),
                    purchaseItem.getBaseInventoryUnitId(), value.baseQuantity(),
                    purchaseItem.getUnitCost(), purchaseItem.getBaseUnitLandedCost(),
                    value.returnAmount(), now);
            result.add(returnItemRepository.save(item));
        }
        returnItemRepository.flush();
        return result;
    }

    private void revalidateEligibleQuantities(List<PurchaseReturnItem> items) {
        for (PurchaseReturnItem item : items) {
            PurchaseItem original = purchaseItemRepository.findById(item.getPurchaseItemId())
                    .orElseThrow(() -> new PurchaseValidationException("Original Purchase item was not found"));
            BigDecimal returnedAlready = returnItemRepository.sumConfirmedReturnedBaseQuantity(original.getId());
            if (returnedAlready == null) returnedAlready = BigDecimal.ZERO;
            BigDecimal eligible = original.getBaseQuantity().subtract(returnedAlready);
            if (item.getBaseQuantity().compareTo(eligible) > 0) {
                throw new PurchaseValidationException(
                        "Purchase Return quantity is no longer eligible because stock was already returned");
            }
        }
    }

    private void validateSettlementBeforePosting(
            Purchase purchase, BigDecimal totalReturnAmount, PurchaseReturnSettlementType settlementType) {
        BigDecimal dueReduction = totalReturnAmount.min(purchase.getDueAmount());
        BigDecimal remainder = totalReturnAmount.subtract(dueReduction);

        if (settlementType == PurchaseReturnSettlementType.REDUCE_SUPPLIER_DUE
                && remainder.signum() > 0) {
            throw new PurchaseValidationException(
                    "Reduce Supplier Due settlement cannot exceed the Purchase outstanding due");
        }
    }

    private void validateReturnablePurchase(Purchase purchase) {
        if (!purchase.isConfirmedOperational()) {
            throw new PurchaseStateException("Only a confirmed operational Purchase can be returned");
        }
    }

    private PaymentMethodResponse requirePaymentMethod(
            Long businessId, Long branchId, Long paymentMethodId, String transactionReference) {
        if (paymentMethodId == null) {
            throw new PurchaseValidationException("Payment Method is required for Supplier refund");
        }
        PaymentMethodResponse method = paymentMethodAccessService.findActiveForBranch(
                        businessId, branchId, paymentMethodId)
                .orElseThrow(() -> new PurchaseValidationException(
                        "Payment Method is inactive or unavailable for this Branch"));
        if (method.transactionReferenceRequired()
                && (transactionReference == null || transactionReference.isBlank())) {
            throw new PurchaseValidationException("Transaction reference is required for this Payment Method");
        }
        return method;
    }

    private PurchaseReturnResponse response(PurchaseReturn value) {
        return mapper.toResponse(
                value, returnItemRepository.findByPurchaseReturnIdOrderByIdAsc(value.getId()));
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

    private String limitedKey(String value) {
        if (value.length() <= 100) return value;
        return value.substring(0, 100);
    }

    private record ResolvedReturnQuantity(BigDecimal conversionFactor, BigDecimal baseQuantity) {
    }

    private record PreparedReturnItem(
            PurchaseItem purchaseItem,
            PurchaseReturnItemCommand command,
            BigDecimal conversionFactor,
            BigDecimal baseQuantity,
            BigDecimal returnAmount) {
    }

    private record PreparedReturn(
            BigDecimal totalReturnAmount,
            List<PreparedReturnItem> items) {
    }
}
