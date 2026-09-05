package com.spark.falcon.settings.entity;

import com.spark.falcon.businesssetup.entity.Business;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "tax_rates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tax_rate_business_code", columnNames = {"business_id", "code"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_tax_rate_business"))
    @Getter(AccessLevel.NONE)
    private Business businessReference;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal rate;

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

    public static TaxRate create(Long businessId, String name, String code, BigDecimal rate,
                                 int displayOrder, Instant now) {
        TaxRate tax = new TaxRate();
        tax.businessId = Objects.requireNonNull(businessId, "businessId is required");
        tax.name = required(name, "name");
        tax.code = normalizedCode(code);
        tax.rate = validRate(rate);
        tax.displayOrder = nonNegative(displayOrder, "displayOrder");
        tax.status = ConfigurationStatus.ACTIVE;
        tax.createdAt = Objects.requireNonNull(now, "now is required");
        tax.updatedAt = now;
        return tax;
    }

    public void update(String name, String code, BigDecimal rate, int displayOrder, Instant now) {
        ensureNotArchived();
        this.name = required(name, "name");
        this.code = normalizedCode(code);
        this.rate = validRate(rate);
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
        if (archivedAt != null) throw new IllegalStateException("Archived tax rate cannot be changed");
    }

    private static BigDecimal validRate(BigDecimal value) {
        Objects.requireNonNull(value, "rate is required");
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("rate must be between 0 and 100");
        return value;
    }

    private static String normalizedCode(String value) {
        return required(value, "code").toUpperCase(Locale.ROOT);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static int nonNegative(int value, String field) {
        if (value < 0) throw new IllegalArgumentException(field + " must not be negative");
        return value;
    }
}
