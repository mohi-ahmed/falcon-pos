package com.spark.falcon.cashmanagement.dto;

import com.spark.falcon.cashmanagement.entity.CashMovementDirection;
import com.spark.falcon.cashmanagement.entity.CashMovementStatus;
import com.spark.falcon.cashmanagement.entity.CashMovementType;
import com.spark.falcon.cashmanagement.entity.CashSourceModule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashMovementResponse {
    private Long id;
    private Long businessId;
    private Long branchId;
    private Long cashbookId;
    private Long cashLocationId;
    private Long destinationCashLocationId;
    private Long registerId;
    private Long destinationRegisterId;
    private Long cashierShiftId;
    private Long destinationCashierShiftId;
    private CashSourceModule sourceModule;
    private String sourceTransactionId;
    private String sourceReference;
    private CashMovementType movementType;
    private CashMovementDirection direction;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private Long postedByUserId;
    private Instant postedAt;
    private CashMovementStatus status;
    private Long reversalReferenceId;
    private String externalAccountReference;
    private String attachmentReference;
    private boolean approvalRequired;
    private Long approvedByOwnerId;
    private Instant approvedAt;
    private String note;
}
