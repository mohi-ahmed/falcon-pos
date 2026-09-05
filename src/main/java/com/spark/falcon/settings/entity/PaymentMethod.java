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
@Table(name = "payment_methods", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_method_business_code", columnNames = {"business_id", "code"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_payment_method_business"))
    @Getter(AccessLevel.NONE)
    private Business businessReference;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(length = 240)
    private String description;

    @Column(name = "is_cash", nullable = false)
    private boolean cash;

    @Column(name = "transaction_reference_required", nullable = false)
    private boolean transactionReferenceRequired;

    @Column(name = "reconciliation_channel_reference", length = 120)
    private String reconciliationChannelReference;

    @Column(name = "reconciliation_account_reference", length = 120)
    private String reconciliationAccountReference;

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

    public static PaymentMethod create(Long businessId, String name, String code, String description, boolean cash,
                                       boolean transactionReferenceRequired, String reconciliationChannelReference,
                                       String reconciliationAccountReference, int displayOrder, Instant now) {
        PaymentMethod method = new PaymentMethod();
        method.businessId = Objects.requireNonNull(businessId, "businessId is required");
        method.name = required(name, "name");
        method.code = normalizedCode(code);
        method.description = optional(description);
        method.cash = cash;
        method.transactionReferenceRequired = transactionReferenceRequired;
        method.reconciliationChannelReference = optional(reconciliationChannelReference);
        method.reconciliationAccountReference = optional(reconciliationAccountReference);
        method.displayOrder = nonNegative(displayOrder, "displayOrder");
        method.status = ConfigurationStatus.ACTIVE;
        method.createdAt = Objects.requireNonNull(now, "now is required");
        method.updatedAt = now;
        return method;
    }

    public void update(String name, String code, String description, boolean cash,
                       boolean transactionReferenceRequired, String reconciliationChannelReference,
                       String reconciliationAccountReference, int displayOrder, Instant now) {
        ensureNotArchived();
        this.name = required(name, "name");
        this.code = normalizedCode(code);
        this.description = optional(description);
        this.cash = cash;
        this.transactionReferenceRequired = transactionReferenceRequired;
        this.reconciliationChannelReference = optional(reconciliationChannelReference);
        this.reconciliationAccountReference = optional(reconciliationAccountReference);
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
        if (archivedAt != null) throw new IllegalStateException("Archived payment method cannot be changed");
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
