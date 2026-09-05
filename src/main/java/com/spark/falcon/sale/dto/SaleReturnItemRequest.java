package com.spark.falcon.sale.dto;

import com.spark.falcon.sale.entity.SaleReturnItemCondition;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class SaleReturnItemRequest {
    @NotNull
    private Long saleItemId;

    @NotNull
    @DecimalMin(value = "0.00000001")
    private BigDecimal quantity;

    @NotNull
    private SaleReturnItemCondition condition;

    private String reason;

    public Long getSaleItemId() { return saleItemId; }
    public void setSaleItemId(Long saleItemId) { this.saleItemId = saleItemId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public SaleReturnItemCondition getCondition() { return condition; }
    public void setCondition(SaleReturnItemCondition condition) { this.condition = condition; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
