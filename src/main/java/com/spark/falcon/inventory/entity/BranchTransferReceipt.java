package com.spark.falcon.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "branch_transfer_receipts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_transfer_receipt_idempotency", columnNames = {"business_id", "transfer_id", "idempotency_key"})
}, indexes = {
        @Index(name = "idx_transfer_receipt_transfer_time", columnList = "transfer_id,received_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchTransferReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "transfer_id", nullable = false, updatable = false)
    private Long transferId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_transfer_receipt_transfer"))
    @Getter(AccessLevel.NONE)
    private BranchTransfer transferReference;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "received_by", nullable = false, updatable = false)
    private Long receivedBy;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BranchTransferReceiptItem> items = new ArrayList<>();

    public static BranchTransferReceipt create(Long businessId, Long transferId, String idempotencyKey,
                                               Long receivedBy, Instant receivedAt) {
        BranchTransferReceipt receipt = new BranchTransferReceipt();
        receipt.businessId = Objects.requireNonNull(businessId, "businessId is required");
        receipt.transferId = Objects.requireNonNull(transferId, "transferId is required");
        receipt.idempotencyKey = required(idempotencyKey);
        receipt.receivedBy = Objects.requireNonNull(receivedBy, "receivedBy is required");
        receipt.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt is required");
        return receipt;
    }

    public void addItem(BranchTransferReceiptItem item) {
        Objects.requireNonNull(item, "receipt item is required").attach(this);
        items.add(item);
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Idempotency key is required");
        return value.trim();
    }
}
