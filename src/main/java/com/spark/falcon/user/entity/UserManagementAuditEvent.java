package com.spark.falcon.user.entity;

import com.spark.falcon.user.entity.enumtype.ManagementActorType;
import com.spark.falcon.user.entity.enumtype.UserManagementAuditAction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "user_management_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserManagementAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 20)
    private ManagementActorType actorType;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 64)
    private UserManagementAuditAction action;

    @Column(name = "target_type", nullable = false, updatable = false, length = 40)
    private String targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public static UserManagementAuditEvent record(Long businessId, ManagementActorType actorType, Long actorId,
                                                  UserManagementAuditAction action, String targetType,
                                                  Long targetId, Instant occurredAt) {
        UserManagementAuditEvent event = new UserManagementAuditEvent();
        event.businessId = Objects.requireNonNull(businessId, "businessId is required");
        event.actorType = Objects.requireNonNull(actorType, "actorType is required");
        event.actorId = Objects.requireNonNull(actorId, "actorId is required");
        event.action = Objects.requireNonNull(action, "action is required");
        event.targetType = required(targetType, "targetType");
        event.targetId = Objects.requireNonNull(targetId, "targetId is required");
        event.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        return event;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
