package com.spark.falcon.purchase.service;

import com.spark.falcon.purchase.dto.response.PurchasePaymentAllocationResult;
import com.spark.falcon.purchase.dto.response.PurchasePaymentInvoiceResponse;
import com.spark.falcon.purchase.entity.Purchase;
import com.spark.falcon.purchase.entity.PurchaseAuditEvent;
import com.spark.falcon.purchase.entity.enumtype.PurchaseActorType;
import com.spark.falcon.purchase.entity.enumtype.PurchaseAuditAction;
import com.spark.falcon.purchase.entity.enumtype.PurchasePaymentStatus;
import com.spark.falcon.purchase.exception.PurchaseStateException;
import com.spark.falcon.purchase.exception.PurchaseValidationException;
import com.spark.falcon.purchase.repository.PurchaseAuditEventRepository;
import com.spark.falcon.purchase.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PurchasePaymentAccessServiceImpl implements PurchasePaymentAccessService {

    private static final Set<PurchasePaymentStatus> DUE_STATUSES = Set.of(
            PurchasePaymentStatus.UNPAID,
            PurchasePaymentStatus.PARTIALLY_PAID
    );

    private final PurchaseRepository purchaseRepository;
    private final PurchaseAuditEventRepository auditRepository;
    private final Clock clock;

    @Override
    @Transactional
    public PurchasePaymentInvoiceResponse lockEligibleSupplierInvoice(
            Long businessId,
            Long branchId,
            Long supplierId,
            Long purchaseId) {
        Purchase purchase = lockEligiblePurchase(businessId, branchId, supplierId, purchaseId);
        return invoiceResponse(purchase);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchasePaymentInvoiceResponse> findEligibleSupplierInvoicesOldestFirst(
            Long businessId,
            Long branchId,
            Long supplierId) {
        return purchaseRepository
                .findByBusinessIdAndBranchIdAndSupplierIdAndPaymentStatusInOrderByPurchaseDateAscIdAsc(
                        businessId, branchId, supplierId, DUE_STATUSES)
                .stream()
                .filter(Purchase::isConfirmedOperational)
                .filter(purchase -> purchase.getDueAmount() != null && purchase.getDueAmount().signum() > 0)
                .map(this::invoiceResponse)
                .toList();
    }

    @Override
    @Transactional
    public PurchasePaymentAllocationResult applySupplierPayment(
            Long businessId,
            Long branchId,
            Long supplierId,
            Long purchaseId,
            BigDecimal amount,
            Long paymentId,
            Long actorId) {
        Purchase purchase = lockEligiblePurchase(businessId, branchId, supplierId, purchaseId);
        BigDecimal dueBefore = purchase.getDueAmount();
        try {
            purchase.applySupplierPayment(amount, now());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new PurchaseStateException(exception.getMessage());
        }
        purchaseRepository.saveAndFlush(purchase);

        Instant now = now();
        auditRepository.save(PurchaseAuditEvent.record(
                businessId,
                branchId,
                purchase.getId(),
                PurchaseAuditAction.SUPPLIER_PAYMENT_POSTED,
                "PAYMENT",
                paymentId,
                PurchaseActorType.OWNER,
                actorId,
                "Supplier Payment applied to Purchase. Due before: " + dueBefore
                        + ", due after: " + purchase.getDueAmount(),
                now
        ));

        return new PurchasePaymentAllocationResult(
                purchase.getId(), amount, dueBefore, purchase.getDueAmount());
    }

    @Override
    @Transactional
    public PurchasePaymentAllocationResult reverseSupplierPayment(
            Long businessId,
            Long branchId,
            Long supplierId,
            Long purchaseId,
            BigDecimal amount,
            Long reversalPaymentId,
            Long actorId) {
        Purchase purchase = lockPurchaseForSupplier(businessId, branchId, supplierId, purchaseId);
        BigDecimal dueBefore = purchase.getDueAmount();
        try {
            purchase.reverseSupplierPayment(amount, now());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new PurchaseStateException(exception.getMessage());
        }
        purchaseRepository.saveAndFlush(purchase);

        Instant now = now();
        auditRepository.save(PurchaseAuditEvent.record(
                businessId,
                branchId,
                purchase.getId(),
                PurchaseAuditAction.SUPPLIER_PAYMENT_REVERSED,
                "PAYMENT",
                reversalPaymentId,
                PurchaseActorType.OWNER,
                actorId,
                "Supplier Payment reversed for Purchase. Due before: " + dueBefore
                        + ", due after: " + purchase.getDueAmount(),
                now
        ));

        return new PurchasePaymentAllocationResult(
                purchase.getId(), amount, dueBefore, purchase.getDueAmount());
    }

    private Purchase lockEligiblePurchase(Long businessId,
                                          Long branchId,
                                          Long supplierId,
                                          Long purchaseId) {
        Purchase purchase = lockPurchaseForSupplier(businessId, branchId, supplierId, purchaseId);
        if (!purchase.isConfirmedOperational()) {
            throw new PurchaseValidationException("Purchase invoice is not eligible for supplier Payment");
        }
        if (purchase.getDueAmount() == null || purchase.getDueAmount().signum() <= 0) {
            throw new PurchaseValidationException("Purchase invoice has no outstanding supplier due");
        }
        return purchase;
    }

    private Purchase lockPurchaseForSupplier(Long businessId,
                                             Long branchId,
                                             Long supplierId,
                                             Long purchaseId) {
        Purchase purchase = purchaseRepository.findForUpdate(businessId, branchId, purchaseId)
                .orElseThrow(() -> new PurchaseValidationException("Purchase invoice was not found for this Branch"));
        if (!supplierId.equals(purchase.getSupplierId())) {
            throw new PurchaseValidationException("Purchase invoice does not belong to the selected Supplier");
        }
        return purchase;
    }

    private PurchasePaymentInvoiceResponse invoiceResponse(Purchase purchase) {
        return new PurchasePaymentInvoiceResponse(purchase.getId(), purchase.getDueAmount());
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
