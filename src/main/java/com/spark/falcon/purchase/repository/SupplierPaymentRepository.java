package com.spark.falcon.purchase.repository;

import com.spark.falcon.purchase.entity.SupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {
    Optional<SupplierPayment> findByBusinessIdAndBranchIdAndIdempotencyKey(
            Long businessId, Long branchId, String idempotencyKey);
    List<SupplierPayment> findByBusinessIdAndBranchIdAndSupplierIdOrderByCreatedAtDesc(
            Long businessId, Long branchId, Long supplierId);
}
