package com.spark.falcon.branch.entity;

import com.spark.falcon.branch.entity.enumtype.BranchAuditAction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "branch_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BranchAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "actor_owner_id", nullable = false, updatable = false)
    private Long actorOwnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private BranchAuditAction action;

    @Lob
    @Column(name = "before_snapshot", updatable = false)
    private String beforeSnapshot;

    @Lob
    @Column(name = "after_snapshot", updatable = false)
    private String afterSnapshot;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public static BranchAuditEvent record(Long businessId, Long branchId, Long actorOwnerId,
                                          BranchAuditAction action, String beforeSnapshot,
                                          String afterSnapshot, Instant occurredAt) {
        return new BranchAuditEvent(null, Objects.requireNonNull(businessId), Objects.requireNonNull(branchId),
                Objects.requireNonNull(actorOwnerId), Objects.requireNonNull(action), beforeSnapshot, afterSnapshot,
                Objects.requireNonNull(occurredAt));
    }
}
