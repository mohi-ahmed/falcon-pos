package com.spark.falcon.product.entity;

import com.spark.falcon.branch.entity.Branch;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "branch_products", uniqueConstraints = {
        @UniqueConstraint(name = "uk_branch_product", columnNames = {"product_id", "branch_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_branch_product_product"))
    @Getter(AccessLevel.NONE)
    private Product productReference;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_branch_product_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static BranchProduct assign(Long productId, Long branchId, Instant now) {
        BranchProduct value = new BranchProduct();
        value.productId = Objects.requireNonNull(productId, "productId is required");
        value.branchId = Objects.requireNonNull(branchId, "branchId is required");
        value.active = true;
        value.createdAt = Objects.requireNonNull(now, "now is required");
        value.updatedAt = now;
        return value;
    }

    public void activate(Instant now) {
        active = true;
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void deactivate(Instant now) {
        active = false;
        updatedAt = Objects.requireNonNull(now, "now is required");
    }
}
