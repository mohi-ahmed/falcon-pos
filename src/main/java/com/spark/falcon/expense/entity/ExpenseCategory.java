package com.spark.falcon.expense.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "expense_categories", uniqueConstraints = @UniqueConstraint(
        name = "uk_expense_category_business_slug", columnNames = {"business_id", "slug"}))
@Getter
@NoArgsConstructor
public class ExpenseCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_id", nullable = false) private Long businessId;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 140) private String slug;
    @Column(name = "parent_category_id") private Long parentCategoryId;
    @Column(length = 1000) private String description;
    @Column(nullable = false) private boolean active;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(nullable = false) private boolean archived;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static ExpenseCategory create(Long businessId, String name, String slug, Long parentCategoryId,
                                         String description, boolean active, int displayOrder, Instant now) {
        ExpenseCategory value = new ExpenseCategory();
        value.businessId = businessId;
        value.change(name, slug, parentCategoryId, description, active, displayOrder, now);
        value.createdAt = now;
        return value;
    }

    public void change(String name, String slug, Long parentCategoryId, String description,
                       boolean active, int displayOrder, Instant now) {
        this.name = name; this.slug = slug; this.parentCategoryId = parentCategoryId;
        this.description = description; this.active = active; this.displayOrder = displayOrder; this.updatedAt = now;
    }

    public void activate(Instant now) {
        if (archived) throw new IllegalStateException("Archived Expense Category cannot be activated");
        active = true;
        updatedAt = now;
    }

    public void deactivate(Instant now) {
        active = false;
        updatedAt = now;
    }

    public void archive(Instant now) { active = false; archived = true; updatedAt = now; }
}
