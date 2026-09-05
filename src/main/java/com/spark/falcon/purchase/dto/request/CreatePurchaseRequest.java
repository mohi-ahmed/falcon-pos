package com.spark.falcon.purchase.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class CreatePurchaseRequest {
    @NotNull @Positive
    private Long branchId;

    @NotNull @Positive
    private Long supplierId;

    @NotNull
    private LocalDate purchaseDate;

    @Size(max = 120)
    private String supplierInvoiceReference;

    @Size(max = 1000)
    private String notes;

    @Size(max = 500)
    private String attachmentReference;

    @NotEmpty @Valid
    private List<PurchaseItemRequest> items = new ArrayList<>();

    @NotNull @DecimalMin(value = "0.0000")
    private BigDecimal orderTax = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0.0000")
    private BigDecimal shippingCharges = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0.0000")
    private BigDecimal otherCharges = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0.0000")
    private BigDecimal discount = BigDecimal.ZERO;

    @NotNull @DecimalMin(value = "0.0000")
    private BigDecimal paidAmount = BigDecimal.ZERO;

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

    @NotBlank @Size(max = 100)
    private String idempotencyKey;
}
