package com.spark.falcon.purchase.dto.request;

import com.spark.falcon.purchase.entity.enumtype.PurchaseReturnSettlementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class PurchaseReturnRequest {
    @NotNull @Positive
    private Long branchId;

    @NotNull @Positive
    private Long purchaseId;

    @NotBlank @Size(max = 100)
    private String referenceNumber;

    @NotNull
    private LocalDate returnDate;

    @Size(max = 1000)
    private String notes;

    @NotNull
    private PurchaseReturnSettlementType settlementType;

    @Positive
    private Long paymentMethodId;

    @Size(max = 160)
    private String transactionReference;

    @Positive
    private Long cashLocationId;

    @Positive
    private Long registerId;

    @Positive
    private Long cashierShiftId;

    @NotEmpty @Valid
    private List<PurchaseReturnItemRequest> items = new ArrayList<>();

    @NotBlank @Size(max = 100)
    private String idempotencyKey;
}
