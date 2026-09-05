package com.spark.falcon.cashmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashbookResponse {
    private Long id;
    private Long businessId;
    private Long branchId;
    private BigDecimal openingBalance;
    private BigDecimal currentBalance;
    private Instant createdAt;
    private Instant updatedAt;
}
