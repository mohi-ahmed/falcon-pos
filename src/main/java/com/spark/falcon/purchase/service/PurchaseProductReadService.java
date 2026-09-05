package com.spark.falcon.purchase.service;

import com.spark.falcon.purchase.dto.ProductPurchaseTraceResponse;
import com.spark.falcon.purchase.entity.Purchase;
import com.spark.falcon.purchase.entity.PurchaseItem;
import com.spark.falcon.purchase.repository.PurchaseItemRepository;
import com.spark.falcon.purchase.repository.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseProductReadService {
    private final PurchaseItemRepository itemRepository;
    private final PurchaseRepository purchaseRepository;

    @Transactional(readOnly = true)
    public Map<Long, ProductPurchaseTraceResponse> findTraces(Long businessId, Collection<Long> purchaseItemIds) {
        if (purchaseItemIds == null || purchaseItemIds.isEmpty()) return Map.of();
        var items = itemRepository.findAllById(purchaseItemIds);
        Map<Long, Purchase> purchases = purchaseRepository.findAllById(items.stream().map(PurchaseItem::getPurchaseId).toList())
                .stream().filter(value -> value.getBusinessId().equals(businessId))
                .collect(Collectors.toMap(Purchase::getId, Function.identity()));
        Map<Long, ProductPurchaseTraceResponse> result = new LinkedHashMap<>();
        for (PurchaseItem item : items) {
            Purchase purchase = purchases.get(item.getPurchaseId());
            if (purchase == null) continue;
            result.put(item.getId(), new ProductPurchaseTraceResponse(item.getId(), purchase.getId(),
                    purchase.getSupplierId(), purchase.getPurchaseDate(), purchase.getSupplierInvoiceReference(),
                    item.getUnitCost(), item.getBaseUnitLandedCost()));
        }
        return Map.copyOf(result);
    }
}
