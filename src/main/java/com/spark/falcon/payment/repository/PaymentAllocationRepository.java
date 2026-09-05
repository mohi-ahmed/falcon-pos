package com.spark.falcon.payment.repository;

import com.spark.falcon.payment.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import com.spark.falcon.payment.entity.PaymentInvoiceType;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    interface InvoiceAmountProjection {
        Long getInvoiceId();
        java.math.BigDecimal getAmount();
    }

    @Query("""
            select allocation.invoiceId as invoiceId, sum(allocation.amount) as amount
            from PaymentAllocation allocation
            join Payment payment on payment.id = allocation.paymentId
            where payment.businessId = :businessId
              and (:branchId is null or payment.branchId = :branchId)
              and (:customerId is null or payment.customerId = :customerId)
              and allocation.invoiceType = com.spark.falcon.payment.entity.PaymentInvoiceType.SALE
              and allocation.effect = com.spark.falcon.payment.entity.PaymentAllocationEffect.REVERSE
            group by allocation.invoiceId
            """)
    List<InvoiceAmountProjection> sumCustomerReversalsByInvoice(@Param("businessId") Long businessId,
                                                                 @Param("branchId") Long branchId,
                                                                 @Param("customerId") Long customerId);

    @Query("""
            select coalesce(sum(case
                       when allocation.effect = com.spark.falcon.payment.entity.PaymentAllocationEffect.APPLY
                       then allocation.amount else -allocation.amount end), 0)
            from PaymentAllocation allocation
            join Payment payment on payment.id = allocation.paymentId
            where payment.businessId = :businessId
              and (:branchId is null or payment.branchId = :branchId)
              and payment.supplierId = :supplierId
              and allocation.invoiceType = com.spark.falcon.payment.entity.PaymentInvoiceType.PURCHASE
            """)
    java.math.BigDecimal sumNetSupplierAllocations(@Param("businessId") Long businessId,
                                                    @Param("branchId") Long branchId,
                                                    @Param("supplierId") Long supplierId);
    List<PaymentAllocation> findByPaymentIdOrderByIdAsc(Long paymentId);
    List<PaymentAllocation> findByInvoiceTypeAndInvoiceIdOrderByCreatedAtDesc(
            PaymentInvoiceType invoiceType, Long invoiceId);
}
