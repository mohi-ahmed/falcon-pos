package com.spark.falcon.sale.dto;

import com.spark.falcon.sale.entity.SaleReturnSettlementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class SaleReturnRequest {
    @NotNull
    private Long branchId;

    @NotBlank
    @Size(max = 100)
    private String idempotencyKey;

    @NotNull
    private SaleReturnSettlementType settlementType;

    private Long paymentMethodId;
    private String transactionReference;
    private String accountReference;
    private Long cashLocationId;
    private Long registerId;
    private Long cashierShiftId;

    @Size(max = 1000)
    private String notes;

    @Valid
    @NotEmpty
    private List<SaleReturnItemRequest> items = new ArrayList<>();

    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public SaleReturnSettlementType getSettlementType() { return settlementType; }
    public void setSettlementType(SaleReturnSettlementType settlementType) { this.settlementType = settlementType; }
    public Long getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(Long paymentMethodId) { this.paymentMethodId = paymentMethodId; }
    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }
    public String getAccountReference() { return accountReference; }
    public void setAccountReference(String accountReference) { this.accountReference = accountReference; }
    public Long getCashLocationId() { return cashLocationId; }
    public void setCashLocationId(Long cashLocationId) { this.cashLocationId = cashLocationId; }
    public Long getRegisterId() { return registerId; }
    public void setRegisterId(Long registerId) { this.registerId = registerId; }
    public Long getCashierShiftId() { return cashierShiftId; }
    public void setCashierShiftId(Long cashierShiftId) { this.cashierShiftId = cashierShiftId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<SaleReturnItemRequest> getItems() { return items; }
    public void setItems(List<SaleReturnItemRequest> items) { this.items = items; }
}
