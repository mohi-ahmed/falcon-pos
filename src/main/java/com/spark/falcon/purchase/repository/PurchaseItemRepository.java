package com.spark.falcon.purchase.repository;

import com.spark.falcon.purchase.entity.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {
    List<PurchaseItem> findByPurchaseIdOrderByIdAsc(Long purchaseId);
    Optional<PurchaseItem> findByIdAndPurchaseId(Long id, Long purchaseId);
    void deleteByPurchaseId(Long purchaseId);
}
