package com.spark.falcon.inventory.service;

import com.spark.falcon.inventory.repository.ProductBatchRepository;
import com.spark.falcon.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class InventoryProductHistoryAccessService {
    private final ProductBatchRepository batchRepository;
    private final StockMovementRepository movementRepository;

    @Transactional(readOnly = true)
    public boolean hasBatchOrMovementHistory(Long businessId, Collection<Long> productVariantIds) {
        if (productVariantIds == null || productVariantIds.isEmpty()) return false;
        return batchRepository.existsByBusinessIdAndProductVariantIdIn(businessId, productVariantIds)
                || movementRepository.existsByBusinessIdAndProductVariantIdIn(businessId, productVariantIds);
    }
}
