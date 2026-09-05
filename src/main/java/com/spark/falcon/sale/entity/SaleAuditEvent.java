package com.spark.falcon.sale.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "sale_audit_events", indexes = {
        @Index(name = "idx_sale_audit_sale_time", columnList = "sale_id,created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "sale_id", nullable = false, updatable = false)
    private Long saleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private SaleAuditAction action;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    @Column(nullable = false, updatable = false, length = 1000)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SaleAuditEvent record(Long businessId, Long branchId, Long saleId,
                                        SaleAuditAction action, Long actorId, String details, Instant now) {
        SaleAuditEvent value = new SaleAuditEvent();
        value.businessId = Objects.requireNonNull(businessId, "businessId is required");
        value.branchId = Objects.requireNonNull(branchId, "branchId is required");
        value.saleId = Objects.requireNonNull(saleId, "saleId is required");
        value.action = Objects.requireNonNull(action, "action is required");
        value.actorId = Objects.requireNonNull(actorId, "actorId is required");
        value.details = required(details);
        value.createdAt = Objects.requireNonNull(now, "now is required");
        return value;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("details must not be blank");
        return value.trim();
    }
}
