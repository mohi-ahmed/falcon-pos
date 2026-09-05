package com.spark.falcon.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "payment_audit_events", indexes = {
        @Index(name = "idx_payment_audit_payment_time", columnList = "payment_id,created_at"),
        @Index(name = "idx_payment_audit_branch_time", columnList = "business_id,branch_id,created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private PaymentAuditAction action;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    @Column(length = 1000, updatable = false)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PaymentAuditEvent record(Long businessId,
                                           Long branchId,
                                           Long paymentId,
                                           PaymentAuditAction action,
                                           Long actorId,
                                           String details,
                                           Instant now) {
        PaymentAuditEvent event = new PaymentAuditEvent();
        event.businessId = Objects.requireNonNull(businessId, "businessId is required");
        event.branchId = Objects.requireNonNull(branchId, "branchId is required");
        event.paymentId = Objects.requireNonNull(paymentId, "paymentId is required");
        event.action = Objects.requireNonNull(action, "action is required");
        event.actorId = Objects.requireNonNull(actorId, "actorId is required");
        event.details = details == null || details.isBlank() ? null : details.trim();
        event.createdAt = Objects.requireNonNull(now, "now is required");
        return event;
    }
}
