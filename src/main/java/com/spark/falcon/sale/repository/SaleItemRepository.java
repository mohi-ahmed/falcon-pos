package com.spark.falcon.sale.repository;

import com.spark.falcon.sale.entity.SaleItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    List<SaleItem> findBySaleIdOrderByIdAsc(Long saleId);

    Optional<SaleItem> findByIdAndSaleId(Long id, Long saleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item from SaleItem item
            where item.id = :itemId and item.saleId = :saleId
            """)
    Optional<SaleItem> findForUpdate(@Param("saleId") Long saleId, @Param("itemId") Long itemId);
}
