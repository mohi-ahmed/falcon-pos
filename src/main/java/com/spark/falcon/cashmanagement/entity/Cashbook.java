package com.spark.falcon.cashmanagement.entity;

import com.spark.falcon.branch.entity.Branch;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "cashbooks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cashbook_branch", columnNames = {"branch_id"})
}, indexes = {
        @Index(name = "idx_cashbook_business", columnList = "business_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cashbook {

    private static final int MONEY_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cashbook_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingBalance;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static Cashbook create(Long businessId, Long branchId, BigDecimal openingBalance, Instant now) {
        Cashbook cashbook = new Cashbook();
        cashbook.businessId = Objects.requireNonNull(businessId, "businessId is required");
        cashbook.branchId = Objects.requireNonNull(branchId, "branchId is required");
        cashbook.openingBalance = normalize(openingBalance);
        cashbook.currentBalance = cashbook.openingBalance;
        cashbook.createdAt = Objects.requireNonNull(now, "now is required");
        cashbook.updatedAt = now;
        return cashbook;
    }

    public BigDecimal post(CashMovementDirection direction, BigDecimal amount, Instant now) {
        BigDecimal normalizedAmount = normalizePositive(amount);
        BigDecimal before = currentBalance;
        currentBalance = switch (Objects.requireNonNull(direction, "direction is required")) {
            case INFLOW -> currentBalance.add(normalizedAmount);
            case OUTFLOW -> currentBalance.subtract(normalizedAmount);
            case TRANSFER -> currentBalance;
        };
        currentBalance = currentBalance.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        updatedAt = Objects.requireNonNull(now, "now is required");
        return before;
    }

    private static BigDecimal normalize(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("openingBalance must not be negative");
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException("amount must be greater than zero");
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
