package com.spark.falcon.sale.repository;

import com.spark.falcon.sale.entity.SaleReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaleReturnItemRepository extends JpaRepository<SaleReturnItem, Long> {
    List<SaleReturnItem> findBySaleReturnIdOrderByIdAsc(Long saleReturnId);
}
