package com.spark.falcon.product.entity;

import com.spark.falcon.branch.entity.Branch;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "branch_product_categories", uniqueConstraints = {
        @UniqueConstraint(name = "uk_branch_product_category", columnNames = {"category_id", "branch_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "category_id", nullable = false, updatable = false) private Long categoryId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_branch_product_category_category"))
    @Getter(AccessLevel.NONE) private Category categoryReference;
    @Column(name = "branch_id", nullable = false, updatable = false) private Long branchId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_branch_product_category_branch"))
    @Getter(AccessLevel.NONE) private Branch branchReference;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static BranchCategory assign(Long categoryId, Long branchId, Instant now) {
        BranchCategory value = new BranchCategory();
        value.categoryId = Objects.requireNonNull(categoryId);
        value.branchId = Objects.requireNonNull(branchId);
        value.active = true;
        value.createdAt = Objects.requireNonNull(now);
        value.updatedAt = now;
        return value;
    }
    public void activate(Instant now) { active = true; updatedAt = Objects.requireNonNull(now); }
    public void deactivate(Instant now) { active = false; updatedAt = Objects.requireNonNull(now); }
}
