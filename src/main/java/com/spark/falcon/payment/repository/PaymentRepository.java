package com.spark.falcon.payment.repository;

import com.spark.falcon.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.spark.falcon.payment.entity.PaymentDirection;
import com.spark.falcon.payment.entity.PaymentFinancialPurpose;
import com.spark.falcon.payment.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("""
            select coalesce(sum(payment.customerCreditAmount), 0) from Payment payment
            where payment.businessId = :businessId
              and (:branchId is null or payment.branchId = :branchId)
              and payment.status = com.spark.falcon.payment.entity.PaymentStatus.CONFIRMED
              and payment.customerSettlementType = com.spark.falcon.payment.entity.CustomerPaymentSettlementType.CUSTOMER_CREDIT
            """)
    java.math.BigDecimal sumAvailableCustomerCredit(@Param("businessId") Long businessId,
                                                     @Param("branchId") Long branchId);

    @Query("""
            select coalesce(sum(payment.customerCreditAmount), 0) from Payment payment
            where payment.businessId = :businessId
              and (:branchId is null or payment.branchId = :branchId)
              and payment.customerId = :customerId
              and payment.status = com.spark.falcon.payment.entity.PaymentStatus.CONFIRMED
              and payment.customerSettlementType = com.spark.falcon.payment.entity.CustomerPaymentSettlementType.CUSTOMER_CREDIT
            """)
    java.math.BigDecimal sumAvailableCustomerCreditForCustomer(@Param("businessId") Long businessId,
                                                                @Param("branchId") Long branchId,
                                                                @Param("customerId") Long customerId);

    @Query("""
            select payment from Payment payment
            where payment.businessId = :businessId
              and (:branchId is null or payment.branchId = :branchId)
              and payment.customerId = :customerId
              and payment.confirmedAt >= :fromTime
              and payment.confirmedAt < :toTime
              and (payment.status = com.spark.falcon.payment.entity.PaymentStatus.CONFIRMED
                   or payment.status = com.spark.falcon.payment.entity.PaymentStatus.REVERSED
                   or payment.reversalOfPaymentId is not null)
            order by payment.confirmedAt asc, payment.id asc
            """)
    List<Payment> findCustomerStatementPayments(@Param("businessId") Long businessId,
                                                 @Param("branchId") Long branchId,
                                                 @Param("customerId") Long customerId,
                                                 @Param("fromTime") Instant fromTime,
                                                 @Param("toTime") Instant toTime);

    Optional<Payment> findByBusinessIdAndBranchIdAndIdempotencyKey(
            Long businessId, Long branchId, String idempotencyKey);

    Optional<Payment> findByIdAndBusinessIdAndBranchId(Long id, Long businessId, Long branchId);

    List<Payment> findByBusinessIdAndBranchIdAndSupplierIdOrderByCreatedAtDesc(
            Long businessId, Long branchId, Long supplierId);

    List<Payment> findByBusinessIdAndBranchIdAndCustomerIdOrderByCreatedAtDesc(
            Long businessId, Long branchId, Long customerId);

    List<Payment> findByBusinessIdAndBranchIdAndSourceTransactionIdAndStatusOrderByCreatedAtAsc(
            Long businessId, Long branchId, Long sourceTransactionId,
            PaymentStatus status);

    List<Payment> findByBusinessIdAndBranchIdAndSourceTransactionIdInAndStatusOrderBySourceTransactionIdAscCreatedAtAsc(
            Long businessId, Long branchId, Collection<Long> sourceTransactionIds, PaymentStatus status);

    @Query("""
            select distinct payment.sourceTransactionId from Payment payment
            where payment.businessId = :businessId
              and payment.branchId = :branchId
              and payment.status = com.spark.falcon.payment.entity.PaymentStatus.CONFIRMED
              and payment.financialPurpose = com.spark.falcon.payment.entity.PaymentFinancialPurpose.CUSTOMER_DUE_COLLECTION
              and lower(payment.paymentMethodNameSnapshot) like lower(concat('%', :keyword, '%'))
            """)
    List<Long> findSaleIdsByPaymentMethodKeyword(@Param("businessId") Long businessId,
                                                 @Param("branchId") Long branchId,
                                                 @Param("keyword") String keyword);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select payment from Payment payment
            where payment.id = :paymentId
              and payment.businessId = :businessId
              and payment.branchId = :branchId
            """)
    Optional<Payment> findForUpdate(@Param("businessId") Long businessId,
                                    @Param("branchId") Long branchId,
                                    @Param("paymentId") Long paymentId);

    @Query("""
            select distinct payment from Payment payment
            left join PaymentAllocation allocation on allocation.paymentId = payment.id
            where payment.businessId = :businessId
              and (:branchId is null or payment.branchId = :branchId)
              and (:paymentId is null or payment.id = :paymentId)
              and (:invoiceId is null or allocation.invoiceId = :invoiceId)
              and (:customerId is null or payment.customerId = :customerId)
              and (:supplierId is null or payment.supplierId = :supplierId)
              and (:paymentMethodId is null or payment.paymentMethodId = :paymentMethodId)
              and (:direction is null or payment.direction = :direction)
              and (:status is null or payment.status = :status)
              and (:actorId is null or payment.createdByActorId = :actorId or payment.confirmedByActorId = :actorId)
              and payment.createdAt >= :fromTime
              and payment.createdAt < :toTime
            """)
    Page<Payment> searchHistory(@Param("businessId") Long businessId,
                                @Param("branchId") Long branchId,
                                @Param("paymentId") Long paymentId,
                                @Param("invoiceId") Long invoiceId,
                                @Param("customerId") Long customerId,
                                @Param("supplierId") Long supplierId,
                                @Param("paymentMethodId") Long paymentMethodId,
                                @Param("direction") PaymentDirection direction,
                                @Param("status") PaymentStatus status,
                                @Param("actorId") Long actorId,
                                @Param("fromTime") Instant fromTime,
                                @Param("toTime") Instant toTime,
                                Pageable pageable);

    @Query("""
            select payment from Payment payment
            where payment.businessId = :businessId
              and (:branchId is null or payment.branchId = :branchId)
              and (:paymentMethodId is null or payment.paymentMethodId = :paymentMethodId)
              and (:direction is null or payment.direction = :direction)
              and (:status is null or payment.status = :status)
              and (:actorId is null or payment.createdByActorId = :actorId or payment.confirmedByActorId = :actorId)
              and payment.createdAt >= :fromTime and payment.createdAt < :toTime
            """)
    List<Payment> findOverviewRows(@Param("businessId") Long businessId,
                                   @Param("branchId") Long branchId,
                                   @Param("paymentMethodId") Long paymentMethodId,
                                   @Param("direction") PaymentDirection direction,
                                   @Param("status") PaymentStatus status,
                                   @Param("actorId") Long actorId,
                                   @Param("fromTime") Instant fromTime,
                                   @Param("toTime") Instant toTime);

    @Query("""
            select coalesce(sum(payment.amount), 0) from Payment payment
            where payment.businessId = :businessId
              and (:branchId is null or payment.branchId = :branchId)
              and payment.status = :status
              and payment.financialPurpose = :purpose
              and payment.createdAt >= :fromTime and payment.createdAt < :toTime
            """)
    java.math.BigDecimal sumByPurpose(@Param("businessId") Long businessId,
                                      @Param("branchId") Long branchId,
                                      @Param("status") PaymentStatus status,
                                      @Param("purpose") PaymentFinancialPurpose purpose,
                                      @Param("fromTime") Instant fromTime,
                                      @Param("toTime") Instant toTime);

    @Query("""
            select coalesce(sum(coalesce(payment.allocatedAmount, payment.amount)), 0) from Payment payment
            where payment.businessId = :businessId
              and (:branchId is null or payment.branchId = :branchId)
              and payment.status = com.spark.falcon.payment.entity.PaymentStatus.CONFIRMED
              and payment.financialPurpose = com.spark.falcon.payment.entity.PaymentFinancialPurpose.CUSTOMER_DUE_COLLECTION
              and payment.createdAt >= :fromTime and payment.createdAt < :toTime
            """)
    java.math.BigDecimal sumCustomerDueCollected(@Param("businessId") Long businessId,
                                                  @Param("branchId") Long branchId,
                                                  @Param("fromTime") Instant fromTime,
                                                  @Param("toTime") Instant toTime);

    @Query("""
            select coalesce(sum(payment.amount - coalesce(payment.changeAmount, 0)), 0) from Payment payment
            where payment.businessId = :businessId
              and (:branchId is null or payment.branchId = :branchId)
              and payment.status = :status
              and payment.cashPayment = :cashPayment
              and payment.direction = :direction
              and payment.createdAt >= :fromTime and payment.createdAt < :toTime
            """)
    java.math.BigDecimal sumByCashAndDirection(@Param("businessId") Long businessId,
                                               @Param("branchId") Long branchId,
                                               @Param("status") PaymentStatus status,
                                               @Param("cashPayment") boolean cashPayment,
                                               @Param("direction") PaymentDirection direction,
                                               @Param("fromTime") Instant fromTime,
                                               @Param("toTime") Instant toTime);

    @Query("""
            select count(payment) from Payment payment
            where payment.businessId = :businessId
              and (:branchId is null or payment.branchId = :branchId)
              and payment.status = :status
              and payment.createdAt >= :fromTime and payment.createdAt < :toTime
            """)
    long countStatus(@Param("businessId") Long businessId,
                     @Param("branchId") Long branchId,
                     @Param("status") PaymentStatus status,
                     @Param("fromTime") Instant fromTime,
                     @Param("toTime") Instant toTime);

    @Query("""
            select coalesce(sum(payment.amount - coalesce(payment.changeAmount, 0)), 0) from Payment payment
            where payment.businessId = :businessId
              and (:branchId is null or payment.branchId = :branchId)
              and payment.status = com.spark.falcon.payment.entity.PaymentStatus.CONFIRMED
              and payment.direction = com.spark.falcon.payment.entity.PaymentDirection.INFLOW
              and payment.cashPayment = false
              and (lower(payment.paymentMethodNameSnapshot) like :firstPattern
                   or lower(payment.paymentMethodCodeSnapshot) like :firstPattern
                   or (:secondPattern is not null and (lower(payment.paymentMethodNameSnapshot) like :secondPattern
                       or lower(payment.paymentMethodCodeSnapshot) like :secondPattern)))
              and payment.createdAt >= :fromTime and payment.createdAt < :toTime
            """)
    java.math.BigDecimal sumCollectionByMethodPatterns(@Param("businessId") Long businessId,
                                                       @Param("branchId") Long branchId,
                                                       @Param("firstPattern") String firstPattern,
                                                       @Param("secondPattern") String secondPattern,
                                                       @Param("fromTime") Instant fromTime,
                                                       @Param("toTime") Instant toTime);
}
