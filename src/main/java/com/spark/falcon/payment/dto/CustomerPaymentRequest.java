package com.spark.falcon.payment.dto;

import com.spark.falcon.payment.entity.CustomerPaymentSettlementType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerPaymentRequest {
    @NotNull private Long branchId;
    @NotNull private Long customerId;
    @NotNull private LocalDateTime paymentDateTime;
    @NotNull @DecimalMin(value = "0.0001") private BigDecimal amount;
    @NotNull private Long paymentMethodId;
    @Size(max = 160) private String transactionReference;
    @Size(max = 160) private String accountReference;
    private Long cashLocationId;
    private Long registerId;
    private Long cashierShiftId;
    @Valid private List<CustomerPaymentAllocationRequest> allocations = new ArrayList<>();
    private boolean automaticOldestDueFirst;
    private CustomerPaymentSettlementType excessSettlementType;
    @NotBlank @Size(max = 100) private String idempotencyKey;
    @Size(max = 1000) private String notes;
    @Size(max = 500) private String attachmentReference;
}
