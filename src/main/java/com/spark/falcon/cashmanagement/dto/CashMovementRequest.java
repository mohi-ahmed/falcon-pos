package com.spark.falcon.cashmanagement.dto;

import com.spark.falcon.cashmanagement.entity.CashMovementDirection;
import com.spark.falcon.cashmanagement.entity.CashMovementType;
import com.spark.falcon.cashmanagement.entity.CashSourceModule;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class CashMovementRequest {
    private Long cashLocationId;
    private Long destinationCashLocationId;
    private Long registerId;
    private Long destinationRegisterId;
    private Long cashierShiftId;
    private Long destinationCashierShiftId;

    @NotNull
    private CashSourceModule sourceModule;

    @Size(max = 120)
    private String sourceTransactionId;

    @Size(max = 160)
    private String sourceReference;

    @NotNull
    private CashMovementType movementType;

    @NotNull
    private CashMovementDirection direction;

    @NotNull
    @DecimalMin(value = "0.0001")
    private BigDecimal amount;

    @NotNull
    private Long postedByUserId;

    @NotBlank
    @Size(max = 100)
    private String postingKey;

    @Size(max = 160)
    private String externalAccountReference;

    @Size(max = 500)
    private String attachmentReference;

    private boolean approvalRequired;
    private Long approvedByOwnerId;

    @Size(max = 1000)
    private String note;
}
