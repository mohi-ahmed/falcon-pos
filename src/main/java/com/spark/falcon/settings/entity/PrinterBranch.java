package com.spark.falcon.settings.entity;

import com.spark.falcon.branch.entity.Branch;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "printer_branches", uniqueConstraints = @UniqueConstraint(
        name = "uk_printer_branch", columnNames = {"printer_id", "branch_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrinterBranch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "printer_id", nullable = false, updatable = false) private Long printerId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "printer_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_printer_branch_printer")) @Getter(AccessLevel.NONE) private Printer printerReference;
    @Column(name = "branch_id", nullable = false, updatable = false) private Long branchId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_printer_branch_branch")) @Getter(AccessLevel.NONE) private Branch branchReference;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static PrinterBranch assign(Long printerId, Long branchId, Instant now) {
        PrinterBranch value = new PrinterBranch();
        value.printerId = Objects.requireNonNull(printerId);
        value.branchId = Objects.requireNonNull(branchId);
        value.active = true;
        value.createdAt = Objects.requireNonNull(now);
        value.updatedAt = now;
        return value;
    }
    public void activate(Instant now) { active = true; updatedAt = Objects.requireNonNull(now); }
    public void deactivate(Instant now) { active = false; updatedAt = Objects.requireNonNull(now); }
}
