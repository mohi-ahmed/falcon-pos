package com.spark.falcon.purchase.repository;

import com.spark.falcon.purchase.entity.SupplierPaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierPaymentAllocationRepository extends JpaRepository<SupplierPaymentAllocation, Long> {
    List<SupplierPaymentAllocation> findBySupplierPaymentIdOrderByIdAsc(Long supplierPaymentId);
    List<SupplierPaymentAllocation> findByPurchaseIdOrderByIdAsc(Long purchaseId);
}
