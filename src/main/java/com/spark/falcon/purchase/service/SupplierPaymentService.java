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
import com.spark.falcon.purchase.dto.command.SupplierPaymentAllocationCommand;
import com.spark.falcon.purchase.dto.command.SupplierPaymentCommand;
import com.spark.falcon.purchase.dto.response.SupplierPaymentResponse;
import com.spark.falcon.purchase.entity.*;
import com.spark.falcon.purchase.entity.enumtype.PurchaseActorType;
import com.spark.falcon.purchase.entity.enumtype.PurchaseAuditAction;
import com.spark.falcon.purchase.entity.enumtype.PurchasePaymentStatus;
import com.spark.falcon.purchase.exception.PurchaseAccessDeniedException;
import com.spark.falcon.purchase.exception.PurchaseDuplicateRequestException;
import com.spark.falcon.purchase.exception.PurchaseStateException;
import com.spark.falcon.purchase.exception.PurchaseValidationException;
import com.spark.falcon.purchase.mapper.PurchaseMapper;
import com.spark.falcon.purchase.repository.*;
import com.spark.falcon.purchase.validation.PurchaseValidator;
import com.spark.falcon.settings.dto.response.PaymentMethodResponse;
import com.spark.falcon.settings.service.PaymentMethodAccessService;
import com.spark.falcon.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SupplierPaymentService {

    private final PurchaseRepository purchaseRepository;
    private final SupplierPaymentRepository paymentRepository;
    private final SupplierPaymentAllocationRepository allocationRepository;
    private final PurchaseAuditEventRepository auditRepository;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final SupplierService supplierService;
    private final PaymentMethodAccessService paymentMethodAccessService;
    private final CashManagementPostingService cashManagementPostingService;
    private final PurchaseMapper mapper;
    private final PurchaseValidator validator;
    private final Clock clock;

    @Transactional
    public SupplierPaymentResponse paySupplierDue(SupplierPaymentCommand command) {
        validator.validate(command);
        BusinessAccessResponse business = requireBusiness(command.ownerId());
        requireBranch(business.businessId(), command.branchId());
        requireSupplier(business.businessId(), command.supplierId());

        SupplierPayment repeated = paymentRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                business.businessId(), command.branchId(), command.idempotencyKey()).orElse(null);
        if (repeated != null) {
            return response(repeated);
        }

        PaymentMethodResponse method = requirePaymentMethod(
                business.businessId(), command.branchId(), command.paymentMethodId(),
                command.transactionReference());

        BigDecimal paymentAmount = validator.money(command.amount());
        List<LockedAllocation> locked = command.automaticOldestDueFirst()
                ? allocateOldestDueFirst(business.businessId(), command.branchId(), command.supplierId(), paymentAmount)
                : validateAndLockManualAllocations(
                        business.businessId(), command.branchId(), command.supplierId(),
                        command.allocations(), paymentAmount);

        return postPayment(
                business.businessId(), command.branchId(), command.supplierId(), command.ownerId(),
                method, paymentAmount, command.transactionReference(), command.cashLocationId(),
                command.registerId(), command.cashierShiftId(), command.idempotencyKey(), command.notes(),
                CashSourceModule.PAYMENT, locked);
    }

    SupplierPaymentResponse postInitialPurchasePayment(Long businessId, Long branchId, Long ownerId,
                                                       Purchase purchase, BigDecimal paymentAmount,
                                                       Long paymentMethodId, String transactionReference,
                                                       Long cashLocationId, Long registerId, Long cashierShiftId) {
        if (paymentAmount == null || paymentAmount.signum() <= 0) return null;

        PaymentMethodResponse method = requirePaymentMethod(
                businessId, branchId, paymentMethodId, transactionReference);

        BigDecimal amount = validator.money(paymentAmount);
        if (amount.compareTo(purchase.getDueAmount()) > 0) {
            throw new PurchaseValidationException("Initial supplier payment cannot exceed Purchase due");
        }

        String idempotencyKey = limitedKey(purchase.getIdempotencyKey() + ":INITIAL");
        SupplierPayment repeated = paymentRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(
                businessId, branchId, idempotencyKey).orElse(null);
        if (repeated != null) return response(repeated);

        LockedAllocation allocation = lockKnownPurchase(purchase, amount);
        return postPayment(
                businessId, branchId, purchase.getSupplierId(), ownerId, method, amount,
                transactionReference, cashLocationId, registerId, cashierShiftId,
                idempotencyKey, purchase.getNotes(), CashSourceModule.PURCHASE, List.of(allocation));
    }

    @Transactional(readOnly = true)
    public List<SupplierPaymentResponse> findSupplierPayments(
            Long ownerId, Long branchId, Long supplierId) {
        BusinessAccessResponse business = requireBusiness(ownerId);
        requireBranch(business.businessId(), branchId);
        requireSupplier(business.businessId(), supplierId);
        return paymentRepository.findByBusinessIdAndBranchIdAndSupplierIdOrderByCreatedAtDesc(
                        business.businessId(), branchId, supplierId)
                .stream().map(this::response).toList();
    }

    private SupplierPaymentResponse postPayment(Long businessId, Long branchId, Long supplierId, Long actorId,
                                                PaymentMethodResponse method, BigDecimal paymentAmount,
                                                String transactionReference, Long cashLocationId, Long registerId,
                                                Long cashierShiftId, String idempotencyKey, String notes,
                                                CashSourceModule cashSourceModule,
                                                List<LockedAllocation> allocations) {
        Instant now = Instant.now(clock);

        SupplierPayment payment = SupplierPayment.confirmed(
                businessId, branchId, supplierId, method.id(), method.name(), method.code(),
                method.cash(), paymentAmount, transactionReference, idempotencyKey,
                PurchaseActorType.OWNER, actorId, now);
        payment = paymentRepository.saveAndFlush(payment);

        List<SupplierPaymentAllocation> savedAllocations = new ArrayList<>();
        for (LockedAllocation locked : allocations) {
            Purchase purchase = locked.purchase();
            BigDecimal dueBefore = purchase.getDueAmount();
            try {
                purchase.applySupplierPayment(locked.amount(), now);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                throw new PurchaseStateException(ex.getMessage());
            }
            purchaseRepository.saveAndFlush(purchase);

            SupplierPaymentAllocation allocation = SupplierPaymentAllocation.create(
                    payment.getId(), purchase.getId(), locked.amount(), dueBefore, purchase.getDueAmount(), now);
            savedAllocations.add(allocationRepository.save(allocation));

            auditRepository.save(PurchaseAuditEvent.record(
                    businessId, branchId, purchase.getId(), PurchaseAuditAction.SUPPLIER_PAYMENT_POSTED,
                    "SUPPLIER_PAYMENT", payment.getId(), PurchaseActorType.OWNER, actorId,
                    "Allocated supplier payment " + locked.amount() + " to Purchase " + purchase.getId(), now));
        }

        if (method.cash()) {
            CashMovementRequest cashRequest = new CashMovementRequest();
            cashRequest.setCashLocationId(cashLocationId);
            cashRequest.setRegisterId(registerId);
            cashRequest.setCashierShiftId(cashierShiftId);
            cashRequest.setSourceModule(cashSourceModule);
            cashRequest.setSourceTransactionId(String.valueOf(payment.getId()));
            cashRequest.setSourceReference(transactionReference);
            cashRequest.setMovementType(CashMovementType.SUPPLIER_DUE_PAYMENT);
            cashRequest.setDirection(CashMovementDirection.OUTFLOW);
            cashRequest.setAmount(paymentAmount);
            cashRequest.setPostedByUserId(actorId);
            cashRequest.setPostingKey(cashPostingKey(idempotencyKey));
            cashRequest.setNote(notes);

            CashMovementResponse cashMovement = cashManagementPostingService.post(businessId, branchId, cashRequest);
            payment.attachCashMovement(cashMovement.getId());
            payment = paymentRepository.saveAndFlush(payment);
        }

        return mapper.toResponse(payment, savedAllocations);
    }

    private List<LockedAllocation> validateAndLockManualAllocations(
            Long businessId, Long branchId, Long supplierId,
            List<SupplierPaymentAllocationCommand> allocations, BigDecimal paymentAmount) {
        List<SupplierPaymentAllocationCommand> sorted = allocations.stream()
                .sorted(Comparator.comparing(SupplierPaymentAllocationCommand::purchaseId))
                .toList();

        BigDecimal total = BigDecimal.ZERO.setScale(PurchaseValidator.MONEY_SCALE);
        List<LockedAllocation> locked = new ArrayList<>();

        for (SupplierPaymentAllocationCommand allocation : sorted) {
            Purchase purchase = purchaseRepository.findForUpdate(
                            businessId, branchId, allocation.purchaseId())
                    .orElseThrow(() -> new PurchaseValidationException("Purchase invoice was not found"));

            validatePayablePurchase(purchase, supplierId);
            BigDecimal amount = validator.money(allocation.amount());
            if (amount.compareTo(purchase.getDueAmount()) > 0) {
                throw new PurchaseValidationException(
                        "Supplier payment allocation exceeds Purchase outstanding due");
            }
            total = total.add(amount);
            locked.add(new LockedAllocation(purchase, amount));
        }

        if (total.compareTo(paymentAmount) != 0) {
            throw new PurchaseValidationException(
                    "Supplier payment allocations must equal the Payment amount");
        }
        return locked;
    }

    private List<LockedAllocation> allocateOldestDueFirst(
            Long businessId, Long branchId, Long supplierId, BigDecimal paymentAmount) {
        List<Purchase> candidates = purchaseRepository
                .findByBusinessIdAndBranchIdAndSupplierIdAndPaymentStatusInOrderByPurchaseDateAscIdAsc(
                        businessId, branchId, supplierId,
                        Set.of(PurchasePaymentStatus.UNPAID, PurchasePaymentStatus.PARTIALLY_PAID));

        BigDecimal remaining = paymentAmount;
        List<LockedAllocation> result = new ArrayList<>();

        for (Purchase candidate : candidates) {
            if (remaining.signum() == 0) break;
            Purchase purchase = purchaseRepository.findForUpdate(
                            businessId, branchId, candidate.getId())
                    .orElseThrow(() -> new PurchaseValidationException("Purchase invoice was not found"));
            if (purchase.getDueAmount().signum() <= 0 || !purchase.isConfirmedOperational()) continue;

            BigDecimal amount = remaining.min(purchase.getDueAmount());
            result.add(new LockedAllocation(purchase, amount));
            remaining = remaining.subtract(amount);
        }

        if (remaining.signum() > 0) {
            throw new PurchaseValidationException(
                    "Supplier payment amount exceeds the supplier's eligible outstanding due");
        }
        return result;
    }

    private LockedAllocation lockKnownPurchase(Purchase purchase, BigDecimal amount) {
        validatePayablePurchase(purchase, purchase.getSupplierId());
        if (amount.compareTo(purchase.getDueAmount()) > 0) {
            throw new PurchaseValidationException("Supplier payment exceeds Purchase due");
        }
        return new LockedAllocation(purchase, amount);
    }

    private void validatePayablePurchase(Purchase purchase, Long supplierId) {
        if (!purchase.getSupplierId().equals(supplierId)) {
            throw new PurchaseValidationException("Purchase invoice does not belong to this Supplier");
        }
        if (!purchase.isConfirmedOperational() || purchase.getDueAmount().signum() <= 0) {
            throw new PurchaseStateException("Purchase invoice is not eligible for supplier due payment");
        }
    }

    private PaymentMethodResponse requirePaymentMethod(
            Long businessId, Long branchId, Long paymentMethodId, String transactionReference) {
        if (paymentMethodId == null) {
            throw new PurchaseValidationException("Payment method is required");
        }
        PaymentMethodResponse method = paymentMethodAccessService.findActiveForBranch(
                        businessId, branchId, paymentMethodId)
                .orElseThrow(() -> new PurchaseValidationException(
                        "Payment method is inactive or unavailable for this Branch"));

        if (method.transactionReferenceRequired()
                && (transactionReference == null || transactionReference.isBlank())) {
            throw new PurchaseValidationException("Transaction reference is required for this Payment Method");
        }
        return method;
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

    private void requireSupplier(Long businessId, Long supplierId) {
        if (supplierService.findActiveByBusinessIdAndId(businessId, supplierId).isEmpty()) {
            throw new PurchaseValidationException("Supplier is inactive or unavailable");
        }
    }

    private SupplierPaymentResponse response(SupplierPayment payment) {
        return mapper.toResponse(
                payment, allocationRepository.findBySupplierPaymentIdOrderByIdAsc(payment.getId()));
    }

    private String cashPostingKey(String idempotencyKey) {
        return limitedKey("SP:" + idempotencyKey);
    }

    private String limitedKey(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() <= 100) return trimmed;
        return trimmed.substring(0, 100);
    }

    private record LockedAllocation(Purchase purchase, BigDecimal amount) {
    }
}
