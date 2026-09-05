package com.spark.falcon.purchase.service;

import com.spark.falcon.purchase.dto.response.SupplierFinancialSummaryResponse;
import com.spark.falcon.purchase.entity.Purchase;
import com.spark.falcon.purchase.entity.enumtype.PurchaseStatus;
import com.spark.falcon.purchase.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseSupplierFinancialReadService {
    private static final int MONEY_SCALE = 4;
    private final PurchaseRepository purchaseRepository;

    public SupplierFinancialSummaryResponse summarize(Long businessId, Long branchId, Long supplierId) {
        List<Purchase> purchases = purchaseRepository.findSupplierFinancialPurchases(
                businessId, branchId, supplierId,
                List.of(PurchaseStatus.CONFIRMED, PurchaseStatus.PARTIALLY_RETURNED, PurchaseStatus.RETURNED));
        return new SupplierFinancialSummaryResponse(
                supplierId, sum(purchases, Purchase::getTotalPayable), sum(purchases, Purchase::getPaidAmount),
                sum(purchases, Purchase::getDueAmount), sum(purchases, Purchase::getReturnedAmount),
                purchases.stream().map(Purchase::getPurchaseDate).max(Comparator.naturalOrder()).orElse(null));
    }

    private BigDecimal sum(List<Purchase> purchases, java.util.function.Function<Purchase, BigDecimal> getter) {
        return purchases.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
