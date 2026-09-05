package com.spark.falcon.settings.entity;

import com.spark.falcon.settings.entity.enumtype.SettingsAuditAction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "settings_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettingsAuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", updatable = false)
    private Long branchId;

    @Column(name = "actor_owner_id", nullable = false, updatable = false)
    private Long actorOwnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 64)
    private SettingsAuditAction action;

    @Column(name = "target_type", nullable = false, updatable = false, length = 40)
    private String targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public static SettingsAuditEvent record(Long businessId, Long branchId, Long actorOwnerId,
                                            SettingsAuditAction action, String targetType, Long targetId,
                                            Instant occurredAt) {
        SettingsAuditEvent event = new SettingsAuditEvent();
        event.businessId = Objects.requireNonNull(businessId, "businessId is required");
        event.branchId = branchId;
        event.actorOwnerId = Objects.requireNonNull(actorOwnerId, "actorOwnerId is required");
        event.action = Objects.requireNonNull(action, "action is required");
        event.targetType = Objects.requireNonNull(targetType, "targetType is required");
        event.targetId = Objects.requireNonNull(targetId, "targetId is required");
        event.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        return event;
    }
}
