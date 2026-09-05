package com.spark.falcon.payment.repository;

import com.spark.falcon.payment.entity.PaymentAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAuditEventRepository extends JpaRepository<PaymentAuditEvent, Long> {
    List<PaymentAuditEvent> findByPaymentIdOrderByCreatedAtAscIdAsc(Long paymentId);
}
