package com.spark.falcon.purchase.entity;

import com.spark.falcon.purchase.entity.enumtype.PurchaseActorType;
import com.spark.falcon.purchase.entity.enumtype.PurchaseAuditAction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "purchase_audit_events", indexes = {
        @Index(name = "idx_purchase_audit_purchase_time", columnList = "purchase_id,created_at"),
        @Index(name = "idx_purchase_audit_branch_time", columnList = "business_id,branch_id,created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "purchase_id", updatable = false)
    private Long purchaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private PurchaseAuditAction action;

    @Column(name = "subject_type", nullable = false, updatable = false, length = 40)
    private String subjectType;

    @Column(name = "subject_id", updatable = false)
    private Long subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 20)
    private PurchaseActorType actorType;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    @Column(length = 1000, updatable = false)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PurchaseAuditEvent record(Long businessId, Long branchId, Long purchaseId,
                                            PurchaseAuditAction action, String subjectType, Long subjectId,
                                            PurchaseActorType actorType, Long actorId, String details,
                                            Instant now) {
        PurchaseAuditEvent event = new PurchaseAuditEvent();
        event.businessId = Objects.requireNonNull(businessId, "businessId is required");
        event.branchId = Objects.requireNonNull(branchId, "branchId is required");
        event.purchaseId = purchaseId;
        event.action = Objects.requireNonNull(action, "action is required");
        event.subjectType = required(subjectType, "subjectType");
        event.subjectId = subjectId;
        event.actorType = Objects.requireNonNull(actorType, "actorType is required");
        event.actorId = Objects.requireNonNull(actorId, "actorId is required");
        event.details = optional(details);
        event.createdAt = Objects.requireNonNull(now, "now is required");
        return event;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
