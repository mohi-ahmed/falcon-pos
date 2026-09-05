package com.spark.falcon.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class SupplierPaymentRequest {
    @NotNull
    @Positive
    private Long branchId;

    @NotNull
    @Positive
    private Long supplierId;

    @NotNull
    @Positive
    private Long paymentMethodId;

    @NotNull
    @DecimalMin(value = "0.0001")
    private BigDecimal amount;

    @Size(max = 160)
    private String transactionReference;

    @Size(max = 160)
    private String accountReference;

    @Positive
    private Long cashLocationId;

    @Positive
    private Long registerId;

    @Positive
    private Long cashierShiftId;

    @Valid
    private List<SupplierPaymentAllocationRequest> allocations = new ArrayList<>();

    private boolean automaticOldestDueFirst;

    @NotBlank
    @Size(max = 100)
    private String idempotencyKey;

    @Size(max = 1000)
    private String notes;

    @Size(max = 500)
    private String attachmentReference;
}
