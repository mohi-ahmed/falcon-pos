package com.spark.falcon.purchase.repository;

import com.spark.falcon.purchase.entity.PurchaseAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseAuditEventRepository extends JpaRepository<PurchaseAuditEvent, Long> {
    List<PurchaseAuditEvent> findByPurchaseIdOrderByCreatedAtAscIdAsc(Long purchaseId);
    Page<PurchaseAuditEvent> findByBusinessIdAndBranchIdOrderByCreatedAtDescIdDesc(
            Long businessId, Long branchId, Pageable pageable);

    @Query("""
            select event from PurchaseAuditEvent event
            left join Purchase purchase on purchase.id = event.purchaseId
            where event.businessId = :businessId and event.branchId = :branchId
              and (:supplierId is null or purchase.supplierId = :supplierId)
              and (:query = '' or lower(coalesce(event.details, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(event.subjectType, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(purchase.supplierInvoiceReference, '')) like lower(concat('%', :query, '%')))
            """)
    Page<PurchaseAuditEvent> search(@Param("businessId") Long businessId,
                                    @Param("branchId") Long branchId,
                                    @Param("supplierId") Long supplierId,
                                    @Param("query") String query,
                                    Pageable pageable);
}
