package com.spark.falcon.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "branch_transfers", uniqueConstraints =
        @UniqueConstraint(name = "uk_transfer_business_key", columnNames = {"business_id", "idempotency_key"}))
@Getter
@NoArgsConstructor
public class BranchTransfer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "business_id", nullable = false) private Long businessId;
    @Column(name = "source_branch_id", nullable = false) private Long sourceBranchId;
    @Column(name = "destination_branch_id", nullable = false) private Long destinationBranchId;
    @Column(name = "request_date", nullable = false) private LocalDate requestDate;
    @Column(name = "expected_dispatch_date") private LocalDate expectedDispatchDate;
    @Column(length = 1000) private String notes;
    @Column(name = "attachment_reference", length = 500) private String attachmentReference;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private BranchTransferStatus status;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "approved_by") private Long approvedBy;
    @Column(name = "dispatched_by") private Long dispatchedBy;
    @Column(name = "received_by") private Long receivedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "dispatched_at") private Instant dispatchedAt;
    @Column(name = "last_received_at") private Instant lastReceivedAt;
    @Column(name = "idempotency_key", nullable = false, length = 100) private String idempotencyKey;
    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true) private List<BranchTransferItem> items = new ArrayList<>();

    public static BranchTransfer draft(Long businessId, Long source, Long destination, LocalDate request,
                                       LocalDate expected, String notes, String attachment, Long actor,
                                       String key, Instant now) {
        if (source.equals(destination)) throw new IllegalArgumentException("Source and Destination Branch must differ");
        BranchTransfer t = new BranchTransfer();
        t.businessId = businessId; t.sourceBranchId = source; t.destinationBranchId = destination;
        t.requestDate = request; t.expectedDispatchDate = expected; t.notes = notes; t.attachmentReference = attachment;
        t.createdBy = actor; t.idempotencyKey = key; t.createdAt = now; t.status = BranchTransferStatus.DRAFT;
        return t;
    }

    public void updateDraft(Long destination, LocalDate request, LocalDate expected, String notes, String attachment) {
        require(BranchTransferStatus.DRAFT);
        if (sourceBranchId.equals(destination)) throw new IllegalArgumentException("Source and Destination Branch must differ");
        destinationBranchId = destination; requestDate = request; expectedDispatchDate = expected;
        this.notes = notes; attachmentReference = attachment;
    }
    public void clearDraftItems() { require(BranchTransferStatus.DRAFT); items.clear(); }
    public void add(BranchTransferItem item) { require(BranchTransferStatus.DRAFT); item.attach(this); items.add(item); }
    public void submit() { require(BranchTransferStatus.DRAFT); if (items.isEmpty()) throw new IllegalStateException("Transfer requires at least one item"); status = BranchTransferStatus.SUBMITTED; }
    public void approved(Long actor, Instant now) { require(BranchTransferStatus.SUBMITTED); approvedBy = actor; approvedAt = now; status = BranchTransferStatus.APPROVED; }
    public void rejected() { require(BranchTransferStatus.SUBMITTED); status = BranchTransferStatus.REJECTED; }
    public void dispatched(Long actor, Instant now) { require(BranchTransferStatus.APPROVED); dispatchedBy = actor; dispatchedAt = now; status = BranchTransferStatus.DISPATCHED; }
    public void received(Long actor, Instant now) {
        if (status != BranchTransferStatus.DISPATCHED && status != BranchTransferStatus.PARTIALLY_RECEIVED) throw new IllegalStateException("Transfer must be Dispatched or Partially Received");
        receivedBy = actor; lastReceivedAt = now;
        status = items.stream().allMatch(BranchTransferItem::fullyResolved) ? BranchTransferStatus.RECEIVED : BranchTransferStatus.PARTIALLY_RECEIVED;
    }
    public boolean mayCancel() { return status == BranchTransferStatus.DRAFT || status == BranchTransferStatus.SUBMITTED || status == BranchTransferStatus.APPROVED; }
    public void cancelledAfterRelease() { if (!mayCancel()) throw new IllegalStateException("Transfer cannot be cancelled"); status = BranchTransferStatus.CANCELLED; }
    public BigDecimal totalBaseQuantity() { return items.stream().map(BranchTransferItem::getBaseQuantity).reduce(BigDecimal.ZERO, BigDecimal::add); }
    public BigDecimal totalValue() { return items.stream().map(i -> i.getBaseQuantity().multiply(i.getTransferUnitCostSnapshot())).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private void require(BranchTransferStatus expected) { if (status != expected) throw new IllegalStateException("Transfer must be " + expected); }
}
