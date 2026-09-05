package com.spark.falcon.inventory.dto;

import java.math.BigDecimal;

public record StockCountVarianceValueResponse(
        Long countId,
        BigDecimal totalAbsoluteVarianceValue
) {
}
