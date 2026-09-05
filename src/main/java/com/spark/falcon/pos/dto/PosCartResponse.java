package com.spark.falcon.pos.dto;

import java.math.BigDecimal;
import java.util.List;

public record PosCartResponse(
        Long businessId,
        Long branchId,
        List<PosPreparedLine> items,
        int totalItemLines,
        BigDecimal totalEnteredQuantity,
        BigDecimal grossItemTotal,
        BigDecimal itemDiscountTotal,
        BigDecimal itemTaxTotal,
        BigDecimal itemPayableTotal,
        BigDecimal orderDiscount,
        BigDecimal shippingCharge,
        BigDecimal otherCharge,
        BigDecimal totalPayable
) {
}
