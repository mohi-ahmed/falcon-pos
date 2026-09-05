package com.spark.falcon.cashmanagement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class CashTransferRequest {

    @NotBlank
    @Size(max = 80)
    private String transferId;

    @NotNull
    private CashTransferType transferType;

    private Long sourceCashLocationId;
    private Long destinationCashLocationId;
    private Long sourceRegisterId;
    private Long destinationRegisterId;
    private Long sourceCashierShiftId;
    private Long destinationCashierShiftId;

    @Size(max = 160)
    private String externalAccountReference;

    @NotNull
    @DecimalMin(value = "0.0001")
    private BigDecimal amount;

    @NotBlank
    @Size(max = 500)
    private String reason;

    @Size(max = 500)
    private String attachmentReference;

    @NotNull
    private Long responsibleUserId;

    private boolean approvalConfirmed;

    @NotBlank
    @Size(max = 100)
    private String transferKey;
}
