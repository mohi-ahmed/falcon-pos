package com.spark.falcon.payment.dto;

import java.math.BigDecimal;

public record CustomerPaymentAllocationCommand(Long saleId, BigDecimal amount) {
}
