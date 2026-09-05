package com.spark.falcon.cashmanagement.entity;

import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "cashier_shifts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cashier_shift_id", columnNames = {"business_id", "shift_code"}),
        @UniqueConstraint(name = "uk_cashier_shift_open_key", columnNames = {"business_id", "branch_id", "open_key"}),
        @UniqueConstraint(name = "uk_cashier_shift_close_key", columnNames = {"business_id", "branch_id", "close_key"})
}, indexes = {
        @Index(name = "idx_cashier_shift_branch_status", columnList = "business_id,branch_id,status"),
        @Index(name = "idx_cashier_shift_register_status", columnList = "register_id,status"),
        @Index(name = "idx_cashier_shift_cashier_status", columnList = "cashier_user_id,status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashierShift {

    private static final int MONEY_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cashier_shift_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(name = "register_id", nullable = false, updatable = false)
    private Long registerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "register_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cashier_shift_register"))
    @Getter(AccessLevel.NONE)
    private Register registerReference;

    @Column(name = "cashier_user_id", nullable = false, updatable = false)
    private Long cashierUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cashier_shift_user"))
    @Getter(AccessLevel.NONE)
    private User cashierReference;

    @Column(name = "source_cash_location_id", nullable = false, updatable = false)
    private Long sourceCashLocationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_cash_location_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cashier_shift_source_location"))
    @Getter(AccessLevel.NONE)
    private CashLocation sourceCashLocationReference;

    @Column(name = "shift_code", nullable = false, length = 80, updatable = false)
    private String shiftCode;

    @Column(name = "open_key", nullable = false, length = 100, updatable = false)
    private String openKey;

    @Column(name = "close_key", length = 100)
    private String closeKey;

    @Column(name = "opening_time", nullable = false, updatable = false)
    private Instant openingTime;

    @Column(name = "opening_float", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal openingFloat;

    @Column(name = "closing_time")
    private Instant closingTime;

    @Column(name = "expected_cash", precision = 19, scale = 4)
    private BigDecimal expectedCash;

    @Column(name = "physical_counted_cash", precision = 19, scale = 4)
    private BigDecimal physicalCountedCash;

    @Column(name = "cash_variance", precision = 19, scale = 4)
    private BigDecimal cashVariance;

    @Enumerated(EnumType.STRING)
    @Column(name = "variance_result", length = 20)
    private CashVarianceResult varianceResult;

    @Column(name = "closing_cash_location_id")
    private Long closingCashLocationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closing_cash_location_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cashier_shift_closing_location"))
    @Getter(AccessLevel.NONE)
    private CashLocation closingCashLocationReference;

    @Column(name = "denomination_count", length = 2000)
    private String denominationCount;

    @Column(name = "closing_note", length = 1000)
    private String closingNote;

    @Column(name = "variance_approval_required", nullable = false, columnDefinition = "boolean default false")
    private boolean varianceApprovalRequired;

    @Column(name = "variance_reviewed_by_owner_id")
    private Long varianceReviewedByOwnerId;

    @Column(name = "variance_reviewed_at")
    private Instant varianceReviewedAt;

    @Column(name = "closed_by_owner_id")
    private Long closedByOwnerId;

    @Column(name = "closing_transfer_cash_movement_id")
    private Long closingTransferCashMovementId;

    @Column(name = "variance_adjustment_cash_movement_id")
    private Long varianceAdjustmentCashMovementId;

    @Column(name = "variance_resolved_at")
    private Instant varianceResolvedAt;

    @Column(length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashierShiftStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static CashierShift open(Long businessId, Long branchId, Long registerId, Long cashierUserId,
                                    Long sourceCashLocationId, String shiftCode, String openKey,
                                    BigDecimal openingFloat, String note, Instant now) {
        CashierShift shift = new CashierShift();
        shift.businessId = Objects.requireNonNull(businessId, "businessId is required");
        shift.branchId = Objects.requireNonNull(branchId, "branchId is required");
        shift.registerId = Objects.requireNonNull(registerId, "registerId is required");
        shift.cashierUserId = Objects.requireNonNull(cashierUserId, "cashierUserId is required");
        shift.sourceCashLocationId = Objects.requireNonNull(sourceCashLocationId, "sourceCashLocationId is required");
        shift.shiftCode = required(shiftCode, "shiftCode");
        shift.openKey = required(openKey, "openKey");
        shift.openingFloat = nonNegative(openingFloat);
        shift.note = optional(note);
        shift.openingTime = Objects.requireNonNull(now, "now is required");
        shift.createdAt = now;
        shift.status = CashierShiftStatus.OPEN;
        return shift;
    }

    public void close(BigDecimal expectedCash,
                      BigDecimal physicalCountedCash,
                      Long closingCashLocationId,
                      String denominationCount,
                      String closingNote,
                      boolean varianceApprovalRequired,
                      Long varianceReviewedByOwnerId,
                      Long closedByOwnerId,
                      Long closingTransferCashMovementId,
                      String closeKey,
                      Instant now) {
        if (!isOpen()) throw new IllegalStateException("Closed cashier shift cannot be closed again");
        this.expectedCash = nonNegative(expectedCash);
        this.physicalCountedCash = nonNegative(physicalCountedCash);
        this.cashVariance = money(this.physicalCountedCash.subtract(this.expectedCash));
        this.varianceResult = this.cashVariance.signum() == 0
                ? CashVarianceResult.BALANCED
                : (this.cashVariance.signum() < 0 ? CashVarianceResult.SHORTAGE : CashVarianceResult.EXCESS);
        this.closingCashLocationId = Objects.requireNonNull(closingCashLocationId, "closingCashLocationId is required");
        this.denominationCount = optional(denominationCount);
        this.closingNote = optional(closingNote);
        this.varianceApprovalRequired = varianceApprovalRequired;
        this.varianceReviewedByOwnerId = varianceApprovalRequired
                ? Objects.requireNonNull(varianceReviewedByOwnerId, "varianceReviewedByOwnerId is required")
                : null;
        this.varianceReviewedAt = varianceApprovalRequired ? Objects.requireNonNull(now, "now is required") : null;
        this.closedByOwnerId = Objects.requireNonNull(closedByOwnerId, "closedByOwnerId is required");
        this.closingTransferCashMovementId = closingTransferCashMovementId;
        this.closeKey = required(closeKey, "closeKey");
        this.closingTime = Objects.requireNonNull(now, "now is required");
        this.status = CashierShiftStatus.CLOSED;
    }

    public void attachVarianceAdjustment(Long cashMovementId, Instant now) {
        if (varianceResult == null || varianceResult == CashVarianceResult.BALANCED) {
            throw new IllegalStateException("Balanced shift has no unresolved cash variance");
        }
        if (varianceAdjustmentCashMovementId != null || varianceResolvedAt != null) {
            throw new IllegalStateException("Cash variance has already been resolved");
        }
        varianceAdjustmentCashMovementId = Objects.requireNonNull(cashMovementId, "cashMovementId is required");
        varianceResolvedAt = Objects.requireNonNull(now, "now is required");
    }

    public boolean isOpen() {
        return status == CashierShiftStatus.OPEN;
    }

    public boolean hasUnresolvedVariance() {
        return status == CashierShiftStatus.CLOSED
                && varianceResult != null
                && varianceResult != CashVarianceResult.BALANCED
                && varianceResolvedAt == null;
    }

    public boolean wasClosedWithKey(String value) {
        return closeKey != null && value != null && closeKey.equals(value.trim());
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("cash amount must not be negative");
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return Objects.requireNonNull(value, "cash value is required").setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
