package com.spark.falcon.purchase.service;

import com.spark.falcon.purchase.dto.response.SupplierReturnProjectionResponse;
import com.spark.falcon.purchase.repository.PurchaseReturnItemRepository;
import com.spark.falcon.purchase.repository.PurchaseReturnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PurchaseSupplierReadService {

    private final PurchaseReturnRepository purchaseReturnRepository;
    private final PurchaseReturnItemRepository purchaseReturnItemRepository;

    public SupplierReturnProjectionResponse projectSupplierReturns(
            Long businessId, Long branchId, Long supplierId, Set<Long> expiryControlledBatchIds) {
        long expiryBatchReturnCount = expiryControlledBatchIds == null || expiryControlledBatchIds.isEmpty()
                ? 0L
                : purchaseReturnItemRepository.countConfirmedSupplierReturnsForBatches(
                        businessId, branchId, supplierId, expiryControlledBatchIds);

        BigDecimal refundAmount = purchaseReturnRepository
                .sumConfirmedRefundAmountForSupplier(businessId, branchId, supplierId);
        BigDecimal creditAmount = purchaseReturnRepository
                .sumConfirmedCreditAmountForSupplier(businessId, branchId, supplierId);

        return new SupplierReturnProjectionResponse(
                expiryBatchReturnCount,
                refundAmount == null ? BigDecimal.ZERO : refundAmount,
                creditAmount == null ? BigDecimal.ZERO : creditAmount
        );
    }
}
