package com.spark.falcon.expense.dto;

import com.spark.falcon.expense.entity.ExpenseClassification;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data @NoArgsConstructor
public class ExpenseRequest {
    @NotNull @Positive private Long branchId;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate expenseDate;
    @NotNull private ExpenseClassification classification;
    private Long categoryId;
    @NotBlank @Size(max=180) private String title;
    @NotNull @DecimalMin("0.0001") private BigDecimal amount;
    @NotNull @Positive private Long paymentMethodId;
    @NotBlank @Size(max=160) private String paymentReference;
    @NotBlank @Size(max=160) private String paidFromReference;
    private Long cashLocationId;
    private Long registerId;
    private Long cashierShiftId;
    @Size(max=160) private String payee;
    @Size(max=500) private String attachmentReference;
    @Size(max=1000) private String notes;
    @NotBlank @Size(max=100) private String idempotencyKey;
}
