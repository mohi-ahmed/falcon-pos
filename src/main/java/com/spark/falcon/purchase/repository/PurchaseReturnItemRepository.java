package com.spark.falcon.purchase.repository;

import com.spark.falcon.purchase.entity.PurchaseReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public interface PurchaseReturnItemRepository extends JpaRepository<PurchaseReturnItem, Long> {
    List<PurchaseReturnItem> findByPurchaseReturnIdOrderByIdAsc(Long purchaseReturnId);
    void deleteByPurchaseReturnId(Long purchaseReturnId);

    @Query("""
            select coalesce(sum(item.baseQuantity), 0)
            from PurchaseReturnItem item
            join PurchaseReturn value on value.id = item.purchaseReturnId
            where item.purchaseItemId = :purchaseItemId
              and value.status = com.spark.falcon.purchase.entity.enumtype.PurchaseReturnStatus.CONFIRMED
            """)
    BigDecimal sumConfirmedReturnedBaseQuantity(@Param("purchaseItemId") Long purchaseItemId);
    @Query("""
            select count(distinct item.purchaseReturnId)
            from PurchaseReturnItem item
            where item.productBatchId in :batchIds
              and exists (
                    select value.id from PurchaseReturn value
                    where value.id = item.purchaseReturnId
                      and value.businessId = :businessId
                      and value.branchId = :branchId
                      and value.supplierId = :supplierId
                      and value.status = com.spark.falcon.purchase.entity.enumtype.PurchaseReturnStatus.CONFIRMED
                  )
            """)
    long countConfirmedSupplierReturnsForBatches(@Param("businessId") Long businessId,
                                                 @Param("branchId") Long branchId,
                                                 @Param("supplierId") Long supplierId,
                                                 @Param("batchIds") Set<Long> batchIds);

}
