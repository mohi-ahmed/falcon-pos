package com.spark.falcon.cashmanagement.dto;

import com.spark.falcon.cashmanagement.entity.CashVarianceResult;
import com.spark.falcon.cashmanagement.entity.CashierShiftStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashierShiftResponse {
    private Long id;
    private Long businessId;
    private Long branchId;
    private Long registerId;
    private Long cashierUserId;
    private Long sourceCashLocationId;
    private String shiftCode;
    private Instant openingTime;
    private BigDecimal openingFloat;
    private String note;
    private CashierShiftStatus status;
    private Instant closingTime;
    private BigDecimal expectedCash;
    private BigDecimal physicalCountedCash;
    private BigDecimal cashVariance;
    private CashVarianceResult varianceResult;
    private Long closingCashLocationId;
    private String denominationCount;
    private String closingNote;
    private boolean varianceApprovalRequired;
    private Long varianceReviewedByOwnerId;
    private Instant varianceReviewedAt;
    private Long closedByOwnerId;
    private Long closingTransferCashMovementId;
    private Long varianceAdjustmentCashMovementId;
    private Instant varianceResolvedAt;
    private boolean unresolvedVariance;
}
