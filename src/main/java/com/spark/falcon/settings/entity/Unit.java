package com.spark.falcon.settings.entity;

import com.spark.falcon.businesssetup.entity.Business;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "units", uniqueConstraints = {
        @UniqueConstraint(name = "uk_unit_business_code", columnNames = {"business_id", "code"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Unit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_unit_business"))
    @Getter(AccessLevel.NONE)
    private Business businessReference;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(length = 240)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConfigurationStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static Unit create(Long businessId, String name, String code, String description,
                              int displayOrder, Instant now) {
        Unit unit = new Unit();
        unit.businessId = Objects.requireNonNull(businessId, "businessId is required");
        unit.name = required(name, "name");
        unit.code = normalizedCode(code);
        unit.description = optional(description);
        unit.displayOrder = nonNegative(displayOrder, "displayOrder");
        unit.status = ConfigurationStatus.ACTIVE;
        unit.createdAt = Objects.requireNonNull(now, "now is required");
        unit.updatedAt = now;
        return unit;
    }

    public void update(String name, String code, String description, int displayOrder, Instant now) {
        ensureNotArchived();
        this.name = required(name, "name");
        this.code = normalizedCode(code);
        this.description = optional(description);
        this.displayOrder = nonNegative(displayOrder, "displayOrder");
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void changeStatus(ConfigurationStatus status, Instant now) {
        ensureNotArchived();
        this.status = Objects.requireNonNull(status, "status is required");
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void archive(Instant now) {
        if (archivedAt != null) return;
        this.status = ConfigurationStatus.INACTIVE;
        this.archivedAt = Objects.requireNonNull(now, "now is required");
        this.updatedAt = now;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void ensureNotArchived() {
        if (archivedAt != null) throw new IllegalStateException("Archived unit cannot be changed");
    }

    private static String normalizedCode(String value) {
        return required(value, "code").toUpperCase(Locale.ROOT);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int nonNegative(int value, String field) {
        if (value < 0) throw new IllegalArgumentException(field + " must not be negative");
        return value;
    }
}
