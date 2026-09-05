package com.spark.falcon.sale.repository;

import com.spark.falcon.sale.entity.SaleAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SaleAuditEventRepository extends JpaRepository<SaleAuditEvent, Long> {
    List<SaleAuditEvent> findBySaleIdOrderByCreatedAtAscIdAsc(Long saleId);

    Page<SaleAuditEvent> findByBusinessIdAndBranchId(Long businessId, Long branchId, Pageable pageable);

    @Query("""
            select event from SaleAuditEvent event
            where event.businessId = :businessId
              and event.branchId = :branchId
              and (
                    lower(event.details) like lower(concat('%', :keyword, '%'))
                    or lower(cast(event.saleId as string)) like lower(concat('%', :keyword, '%'))
                    or lower(cast(event.actorId as string)) like lower(concat('%', :keyword, '%'))
                    or event.saleId in (
                        select sale.id from Sale sale
                        where sale.businessId = :businessId
                          and sale.branchId = :branchId
                          and lower(sale.customerReference.name) like lower(concat('%', :keyword, '%'))
                    )
                    or event.saleId in :paymentSaleIds
                  )
            """)
    Page<SaleAuditEvent> search(@Param("businessId") Long businessId,
                                @Param("branchId") Long branchId,
                                @Param("keyword") String keyword,
                                @Param("paymentSaleIds") Collection<Long> paymentSaleIds,
                                Pageable pageable);
}
