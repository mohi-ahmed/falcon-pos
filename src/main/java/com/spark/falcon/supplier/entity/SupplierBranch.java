package com.spark.falcon.supplier.entity;

import com.spark.falcon.branch.entity.Branch;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "supplier_branches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_supplier_branch", columnNames = {"supplier_id", "branch_id"})
}, indexes = {
        @Index(name = "idx_supplier_branch_branch", columnList = "branch_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SupplierBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_supplier_branch_supplier"))
    private Supplier supplierReference;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_supplier_branch_branch"))
    private Branch branchReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
