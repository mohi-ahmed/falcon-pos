package com.spark.falcon.expense.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity @Table(name = "expense_audit_events") @Getter @NoArgsConstructor
public class ExpenseAuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="business_id", nullable=false) private Long businessId;
    @Column(name="branch_id", nullable=false) private Long branchId;
    @Column(name="expense_id", nullable=false) private Long expenseId;
    @Column(nullable=false, length=40) private String action;
    @Column(name="actor_id", nullable=false) private Long actorId;
    @Column(length=1000) private String details;
    @Column(name="occurred_at", nullable=false) private Instant occurredAt;
    public static ExpenseAuditEvent record(Expense e, String action, Long actorId, String details, Instant now) {
        ExpenseAuditEvent a = new ExpenseAuditEvent(); a.businessId=e.getBusinessId(); a.branchId=e.getBranchId();
        a.expenseId=e.getId(); a.action=action; a.actorId=actorId; a.details=details; a.occurredAt=now; return a;
    }
}
