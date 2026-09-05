package com.spark.falcon.purchase.repository;

import com.spark.falcon.purchase.entity.StockImportRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockImportRowRepository extends JpaRepository<StockImportRow, Long> {
    List<StockImportRow> findByStockImportBatchIdOrderByRowNumberAsc(Long batchId);
    void deleteByStockImportBatchId(Long batchId);
}
