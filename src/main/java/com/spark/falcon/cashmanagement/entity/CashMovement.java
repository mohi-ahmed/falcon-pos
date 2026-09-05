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
@Table(name = "cash_movements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cash_movement_posting_key", columnNames = {"business_id", "branch_id", "posting_key"})
}, indexes = {
        @Index(name = "idx_cash_movement_cashbook_time", columnList = "cashbook_id,posted_at"),
        @Index(name = "idx_cash_movement_shift", columnList = "cashier_shift_id"),
        @Index(name = "idx_cash_movement_destination_shift", columnList = "destination_cashier_shift_id"),
        @Index(name = "idx_cash_movement_source", columnList = "source_module,source_transaction_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashMovement {

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
            foreignKey = @ForeignKey(name = "fk_cash_movement_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(name = "cashbook_id", nullable = false, updatable = false)
    private Long cashbookId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashbook_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cash_movement_cashbook"))
    @Getter(AccessLevel.NONE)
    private Cashbook cashbookReference;

    @Column(name = "cash_location_id")
    private Long cashLocationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_location_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cash_movement_location"))
    @Getter(AccessLevel.NONE)
    private CashLocation cashLocationReference;

    @Column(name = "destination_cash_location_id")
    private Long destinationCashLocationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_cash_location_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cash_movement_destination_location"))
    @Getter(AccessLevel.NONE)
    private CashLocation destinationCashLocationReference;

    @Column(name = "register_id")
    private Long registerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "register_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cash_movement_register"))
    @Getter(AccessLevel.NONE)
    private Register registerReference;

    @Column(name = "destination_register_id")
    private Long destinationRegisterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_register_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cash_movement_destination_register"))
    @Getter(AccessLevel.NONE)
    private Register destinationRegisterReference;

    @Column(name = "cashier_shift_id")
    private Long cashierShiftId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_shift_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cash_movement_shift"))
    @Getter(AccessLevel.NONE)
    private CashierShift cashierShiftReference;

    @Column(name = "destination_cashier_shift_id")
    private Long destinationCashierShiftId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_cashier_shift_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cash_movement_destination_shift"))
    @Getter(AccessLevel.NONE)
    private CashierShift destinationCashierShiftReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_module", nullable = false, length = 30, updatable = false)
    private CashSourceModule sourceModule;

    @Column(name = "source_transaction_id", length = 120, updatable = false)
    private String sourceTransactionId;

    @Column(name = "source_reference", length = 160, updatable = false)
    private String sourceReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 40, updatable = false)
    private CashMovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private CashMovementDirection direction;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "posted_by_user_id", nullable = false, updatable = false)
    private Long postedByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by_user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cash_movement_user"))
    @Getter(AccessLevel.NONE)
    private User postedByReference;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private Instant postedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashMovementStatus status;

    @Column(name = "reversal_reference_id")
    private Long reversalReferenceId;

    @Column(name = "posting_key", nullable = false, length = 100, updatable = false)
    private String postingKey;

    @Column(name = "external_account_reference", length = 160, updatable = false)
    private String externalAccountReference;

    @Column(name = "attachment_reference", length = 500, updatable = false)
    private String attachmentReference;

    @Column(name = "approval_required", nullable = false, updatable = false, columnDefinition = "boolean default false")
    private boolean approvalRequired;

    @Column(name = "approved_by_owner_id", updatable = false)
    private Long approvedByOwnerId;

    @Column(name = "approved_at", updatable = false)
    private Instant approvedAt;

    @Column(length = 1000, updatable = false)
    private String note;

    public static CashMovement posted(Long businessId, Long branchId, Long cashbookId,
                                      Long cashLocationId, Long destinationCashLocationId,
                                      Long registerId, Long destinationRegisterId,
                                      Long cashierShiftId, Long destinationCashierShiftId,
                                      CashSourceModule sourceModule, String sourceTransactionId,
                                      String sourceReference, CashMovementType movementType,
                                      CashMovementDirection direction, BigDecimal amount,
                                      BigDecimal balanceBefore, BigDecimal balanceAfter,
                                      Long postedByUserId, String postingKey,
                                      String externalAccountReference, String attachmentReference,
                                      boolean approvalRequired, Long approvedByOwnerId,
                                      String note, Instant now) {
        CashMovement movement = new CashMovement();
        movement.businessId = Objects.requireNonNull(businessId, "businessId is required");
        movement.branchId = Objects.requireNonNull(branchId, "branchId is required");
        movement.cashbookId = Objects.requireNonNull(cashbookId, "cashbookId is required");
        movement.cashLocationId = cashLocationId;
        movement.destinationCashLocationId = destinationCashLocationId;
        movement.registerId = registerId;
        movement.destinationRegisterId = destinationRegisterId;
        movement.cashierShiftId = cashierShiftId;
        movement.destinationCashierShiftId = destinationCashierShiftId;
        movement.sourceModule = Objects.requireNonNull(sourceModule, "sourceModule is required");
        movement.sourceTransactionId = optional(sourceTransactionId);
        movement.sourceReference = optional(sourceReference);
        movement.movementType = Objects.requireNonNull(movementType, "movementType is required");
        movement.direction = Objects.requireNonNull(direction, "direction is required");
        movement.amount = positive(amount);
        movement.balanceBefore = money(balanceBefore);
        movement.balanceAfter = money(balanceAfter);
        movement.postedByUserId = Objects.requireNonNull(postedByUserId, "postedByUserId is required");
        movement.postingKey = required(postingKey, "postingKey");
        movement.externalAccountReference = optional(externalAccountReference);
        movement.attachmentReference = optional(attachmentReference);
        movement.approvalRequired = approvalRequired;
        movement.approvedByOwnerId = approvalRequired
                ? Objects.requireNonNull(approvedByOwnerId, "approvedByOwnerId is required when approval is required")
                : null;
        movement.approvedAt = approvalRequired ? Objects.requireNonNull(now, "now is required") : null;
        movement.note = optional(note);
        movement.postedAt = Objects.requireNonNull(now, "now is required");
        movement.status = CashMovementStatus.POSTED;
        return movement;
    }

    public void linkReversal(Long reversalMovementId) {
        if (status != CashMovementStatus.POSTED || reversalReferenceId != null) {
            throw new IllegalStateException("Cash Movement has already been reversed");
        }
        status = CashMovementStatus.REVERSED;
        reversalReferenceId = Objects.requireNonNull(reversalMovementId, "reversalMovementId is required");
    }

    public void referenceOriginalMovement(Long originalMovementId) {
        if (movementType != CashMovementType.REVERSAL) {
            throw new IllegalStateException("Only a reversal Cash Movement may reference an original movement");
        }
        if (reversalReferenceId != null) {
            throw new IllegalStateException("Original Cash Movement reference is already assigned");
        }
        reversalReferenceId = Objects.requireNonNull(originalMovementId, "originalMovementId is required");
    }

    private static BigDecimal positive(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException("amount must be greater than zero");
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return Objects.requireNonNull(value, "balance is required").setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
