package com.spark.falcon.supplier.repository;

import com.spark.falcon.supplier.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByIdAndBusinessId(Long id, Long businessId);

    @Query("""
            select s from Supplier s
            where s.businessId = :businessId
              and s.archivedAt is null
              and (
                    :keyword = ''
                    or lower(s.name) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(s.codeName, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(s.mobileNumber, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(s.email, '')) like lower(concat('%', :keyword, '%'))
                  )
            """)
    Page<Supplier> searchActive(@Param("businessId") Long businessId,
                                @Param("keyword") String keyword,
                                Pageable pageable);
    @Query("""
            select s from Supplier s
            where s.businessId = :businessId
              and s.archivedAt is null
              and exists (
                    select sb.id from SupplierBranch sb
                    where sb.supplierId = s.id
                      and sb.branchId = :branchId
                  )
              and (
                    :keyword = ''
                    or lower(s.name) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(s.codeName, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(s.mobileNumber, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(s.email, '')) like lower(concat('%', :keyword, '%'))
                  )
            """)
    Page<Supplier> searchActiveForBranch(@Param("businessId") Long businessId,
                                         @Param("branchId") Long branchId,
                                         @Param("keyword") String keyword,
                                         Pageable pageable);

}
