package com.spark.falcon.expense.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="expense_recoverable_receipts", uniqueConstraints=@UniqueConstraint(
        name="uk_recoverable_receipt_business_key", columnNames={"business_id","idempotency_key"}))
@Getter @NoArgsConstructor
public class RecoverableReceipt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="business_id",nullable=false) private Long businessId;
    @Column(name="branch_id",nullable=false) private Long branchId;
    @Column(name="expense_id",nullable=false) private Long expenseId;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(name="payment_method_id",nullable=false) private Long paymentMethodId;
    @Column(name="payment_method_snapshot",nullable=false,length=120) private String paymentMethodSnapshot;
    @Column(name="receipt_reference",nullable=false,length=160) private String receiptReference;
    @Column(name="cash_location_id") private Long cashLocationId;
    @Column(name="register_id") private Long registerId;
    @Column(name="cashier_shift_id") private Long cashierShiftId;
    @Column(name="cash_movement_id",unique=true) private Long cashMovementId;
    @Column(name="received_by",nullable=false) private Long receivedBy;
    @Column(name="idempotency_key",nullable=false,length=100) private String idempotencyKey;
    @Column(name="received_at",nullable=false) private Instant receivedAt;
    public static RecoverableReceipt create(Long businessId,Long branchId,Long expenseId,BigDecimal amount,Long methodId,
            String method,String reference,Long location,Long register,Long shift,Long movement,Long actor,String key,Instant now){
        RecoverableReceipt r=new RecoverableReceipt();r.businessId=businessId;r.branchId=branchId;r.expenseId=expenseId;
        r.amount=amount;r.paymentMethodId=methodId;r.paymentMethodSnapshot=method;r.receiptReference=reference;
        r.cashLocationId=location;r.registerId=register;r.cashierShiftId=shift;r.cashMovementId=movement;
        r.receivedBy=actor;r.idempotencyKey=key;r.receivedAt=now;return r;}
}
