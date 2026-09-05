package com.spark.falcon.purchase.entity;

import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.purchase.entity.enumtype.PurchaseActorType;
import com.spark.falcon.purchase.entity.enumtype.StockImportPurpose;
import com.spark.falcon.purchase.entity.enumtype.StockImportStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "stock_import_batches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_import_idempotency", columnNames = {"business_id", "branch_id", "idempotency_key"})
}, indexes = {
        @Index(name = "idx_stock_import_branch_time", columnList = "business_id,branch_id,uploaded_at"),
        @Index(name = "idx_stock_import_fingerprint", columnList = "business_id,branch_id,import_purpose,file_fingerprint")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_stock_import_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_purpose", nullable = false, updatable = false, length = 50)
    private StockImportPurpose importPurpose;

    @Column(name = "file_name", nullable = false, updatable = false, length = 255)
    private String fileName;

    @Column(name = "file_fingerprint", nullable = false, updatable = false, length = 64)
    private String fileFingerprint;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Column(name = "posted_movement_count", nullable = false)
    private int postedMovementCount;

    @Column(name = "error_summary", length = 2000)
    private String errorSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StockImportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "uploaded_by_actor_type", nullable = false, updatable = false, length = 20)
    private PurchaseActorType uploadedByActorType;

    @Column(name = "uploaded_by_actor_id", nullable = false, updatable = false)
    private Long uploadedByActorId;

    @Column(name = "confirmed_by_actor_id")
    private Long confirmedByActorId;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static StockImportBatch uploaded(Long businessId, Long branchId, StockImportPurpose purpose,
                                            String fileName, String fingerprint, String idempotencyKey,
                                            PurchaseActorType actorType, Long actorId, Instant now) {
        StockImportBatch batch = new StockImportBatch();
        batch.businessId = Objects.requireNonNull(businessId, "businessId is required");
        batch.branchId = Objects.requireNonNull(branchId, "branchId is required");
        batch.importPurpose = Objects.requireNonNull(purpose, "purpose is required");
        batch.fileName = required(fileName, "fileName");
        batch.fileFingerprint = required(fingerprint, "fingerprint");
        batch.idempotencyKey = required(idempotencyKey, "idempotencyKey");
        batch.status = StockImportStatus.UPLOADED;
        batch.uploadedByActorType = Objects.requireNonNull(actorType, "actorType is required");
        batch.uploadedByActorId = Objects.requireNonNull(actorId, "actorId is required");
        batch.uploadedAt = Objects.requireNonNull(now, "now is required");
        return batch;
    }

    public void startValidation() {
        requireEditable();
        status = StockImportStatus.VALIDATING;
        errorSummary = null;
    }

    public void completeValidation(int totalRows, int validRows, int failedRows, String errorSummary) {
        requireEditableOrValidating();
        this.totalRows = totalRows;
        this.validRows = validRows;
        this.failedRows = failedRows;
        this.errorSummary = optional(errorSummary);
        this.status = failedRows > 0 || totalRows == 0
                ? StockImportStatus.VALIDATION_FAILED
                : StockImportStatus.READY_FOR_CONFIRMATION;
    }

    public void markPosted(int movementCount, Long confirmedByActorId, Instant now) {
        if (status != StockImportStatus.READY_FOR_CONFIRMATION) {
            throw new IllegalStateException("Only Ready for Confirmation Stock Import can be posted");
        }
        this.postedMovementCount = movementCount;
        this.confirmedByActorId = Objects.requireNonNull(confirmedByActorId, "confirmedByActorId is required");
        this.confirmedAt = Objects.requireNonNull(now, "now is required");
        this.status = StockImportStatus.POSTED;
    }

    public void markReversed(Instant now) {
        if (status != StockImportStatus.POSTED) {
            throw new IllegalStateException("Only Posted Stock Import can be reversed");
        }
        this.status = StockImportStatus.REVERSED;
        this.reversedAt = Objects.requireNonNull(now, "now is required");
    }

    public boolean canReplaceFile() {
        return status == StockImportStatus.UPLOADED
                || status == StockImportStatus.VALIDATION_FAILED
                || status == StockImportStatus.READY_FOR_CONFIRMATION
                || status == StockImportStatus.FAILED;
    }

    private void requireEditable() {
        if (!canReplaceFile()) throw new IllegalStateException("Posted or Reversed Stock Import is read-only");
    }

    private void requireEditableOrValidating() {
        if (status != StockImportStatus.VALIDATING && !canReplaceFile()) {
            throw new IllegalStateException("Posted or Reversed Stock Import is read-only");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
