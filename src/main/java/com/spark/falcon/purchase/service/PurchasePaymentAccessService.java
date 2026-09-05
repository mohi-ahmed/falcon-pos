package com.spark.falcon.purchase.service;

import com.spark.falcon.purchase.dto.response.PurchasePaymentAllocationResult;
import com.spark.falcon.purchase.dto.response.PurchasePaymentInvoiceResponse;

import java.math.BigDecimal;
import java.util.List;

public interface PurchasePaymentAccessService {

    PurchasePaymentInvoiceResponse lockEligibleSupplierInvoice(
            Long businessId,
            Long branchId,
            Long supplierId,
            Long purchaseId
    );

    List<PurchasePaymentInvoiceResponse> findEligibleSupplierInvoicesOldestFirst(
            Long businessId,
            Long branchId,
            Long supplierId
    );

    PurchasePaymentAllocationResult applySupplierPayment(
            Long businessId,
            Long branchId,
            Long supplierId,
            Long purchaseId,
            BigDecimal amount,
            Long paymentId,
            Long actorId
    );

    PurchasePaymentAllocationResult reverseSupplierPayment(
            Long businessId,
            Long branchId,
            Long supplierId,
            Long purchaseId,
            BigDecimal amount,
            Long reversalPaymentId,
            Long actorId
    );
}
