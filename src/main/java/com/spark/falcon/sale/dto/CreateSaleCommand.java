package com.spark.falcon.sale.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateSaleCommand(
        Long businessId,
        Long branchId,
        Long customerId,
        Long actorId,
        String idempotencyKey,
        BigDecimal grossItemTotal,
        BigDecimal itemDiscountTotal,
        BigDecimal itemTaxTotal,
        BigDecimal itemPayableTotal,
        BigDecimal orderDiscount,
        BigDecimal shippingCharge,
        BigDecimal otherCharge,
        BigDecimal totalPayable,
        LocalDate dueDate,
        String notes
) {
}
