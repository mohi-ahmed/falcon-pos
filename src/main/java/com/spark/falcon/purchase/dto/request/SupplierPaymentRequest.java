package com.spark.falcon.purchase.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class SupplierPaymentRequest {
    @NotNull @Positive
    private Long branchId;

    @NotNull @Positive
    private Long supplierId;

    @NotNull @Positive
    private Long paymentMethodId;

    @NotNull @DecimalMin(value = "0.0001")
    private BigDecimal amount;

    @Size(max = 160)
    private String transactionReference;

    @Positive
    private Long cashLocationId;

    @Positive
    private Long registerId;

    @Positive
    private Long cashierShiftId;

    @Valid
    private List<SupplierPaymentAllocationRequest> allocations = new ArrayList<>();

    private boolean automaticOldestDueFirst;

    @NotBlank @Size(max = 100)
    private String idempotencyKey;

    @Size(max = 1000)
    private String notes;
}
