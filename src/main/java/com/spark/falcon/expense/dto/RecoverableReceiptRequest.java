package com.spark.falcon.expense.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
@Data @NoArgsConstructor
public class RecoverableReceiptRequest {
    @NotNull @DecimalMin("0.0001") private BigDecimal amount;
    @NotNull @Positive private Long paymentMethodId;
    @NotBlank @Size(max=160) private String receiptReference;
    private Long cashLocationId; private Long registerId; private Long cashierShiftId;
    @NotBlank @Size(max=100) private String idempotencyKey;
}
