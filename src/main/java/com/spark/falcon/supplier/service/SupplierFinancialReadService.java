package com.spark.falcon.supplier.service;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.purchase.dto.response.SupplierFinancialSummaryResponse;
import com.spark.falcon.purchase.service.PurchaseSupplierFinancialReadService;
import com.spark.falcon.payment.service.PaymentSupplierFinancialReadService;
import com.spark.falcon.supplier.exception.SupplierNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupplierFinancialReadService {
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final SupplierService supplierService;
    private final PurchaseSupplierFinancialReadService purchaseReadService;
    private final PaymentSupplierFinancialReadService paymentReadService;

    public SupplierFinancialSummaryResponse summarize(Long ownerId, Long branchId, Long supplierId) {
        var business = businessAccessService.findByOwnerId(ownerId).orElseThrow(SupplierNotFoundException::new);
        branchAccessService.findActiveByBusinessIdAndBranchId(business.businessId(), branchId)
                .orElseThrow(SupplierNotFoundException::new);
        supplierService.findByOwnerAndId(ownerId, supplierId)
                .orElseThrow(SupplierNotFoundException::new);
        var purchases = purchaseReadService.summarize(business.businessId(), branchId, supplierId);
        return new SupplierFinancialSummaryResponse(
                supplierId, purchases.totalConfirmedPurchases(),
                paymentReadService.confirmedSupplierAllocations(business.businessId(), branchId, supplierId),
                purchases.outstandingDue(), purchases.purchaseReturnsOrCredits(), purchases.lastPurchaseDate());
    }
}
