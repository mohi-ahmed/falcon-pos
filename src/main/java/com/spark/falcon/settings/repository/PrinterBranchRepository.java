package com.spark.falcon.settings.repository;

import com.spark.falcon.settings.entity.PrinterBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PrinterBranchRepository extends JpaRepository<PrinterBranch, Long> {
    List<PrinterBranch> findByPrinterId(Long printerId);
    List<PrinterBranch> findByBranchIdAndActiveTrue(Long branchId);
    boolean existsByPrinterIdAndBranchIdAndActiveTrue(Long printerId, Long branchId);
}
