package com.spark.falcon.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class PosRequest {
    @NotNull @Positive
    private Long branchId;

    private Long customerId;

    @Valid
    private List<PosCartLineRequest> items = new ArrayList<>();

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal orderDiscount = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal shippingCharge = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal otherCharge = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal paidNow = BigDecimal.ZERO;

    private LocalDate dueDate;

    private Long paymentMethodId;
    private Long cashLocationId;
    private Long registerId;
    private Long cashierShiftId;

    @Size(max = 160)
    private String transactionReference;

    @Size(max = 160)
    private String accountReference;

    @NotBlank @Size(max = 100)
    private String idempotencyKey;

    @Size(max = 1000)
    private String notes;

    @Size(max = 160)
    private String barcode;
}
