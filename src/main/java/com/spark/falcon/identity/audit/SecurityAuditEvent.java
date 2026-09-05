package com.spark.falcon.identity.audit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(
        name = "security_audit_events",
        indexes = {
                @Index(
                        name = "idx_security_audit_event_created_at",
                        columnList = "created_at"
                ),
                @Index(
                        name = "idx_security_audit_event_actor",
                        columnList = "actor_identifier"
                ),
                @Index(
                        name = "idx_security_audit_event_type",
                        columnList = "event_type"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecurityAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private SecurityAuditEventType eventType;

    @Column(name = "actor_identifier", length = 255)
    private String actorIdentifier;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "details", length = 500)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private SecurityAuditEvent(
            SecurityAuditEventType eventType,
            String actorIdentifier,
            String ipAddress,
            String userAgent,
            String details,
            Instant createdAt
    ) {
        this.eventType = eventType;
        this.actorIdentifier = actorIdentifier;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.details = details;
        this.createdAt = createdAt;
    }

    public static SecurityAuditEvent create(
            SecurityAuditEventType eventType,
            String actorIdentifier,
            String ipAddress,
            String userAgent,
            String details,
            Instant createdAt
    ) {
        return new SecurityAuditEvent(
                eventType,
                actorIdentifier,
                ipAddress,
                userAgent,
                details,
                createdAt
        );
    }
}