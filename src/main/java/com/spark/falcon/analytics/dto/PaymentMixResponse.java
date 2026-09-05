package com.spark.falcon.analytics.dto;

import java.math.BigDecimal;

public record PaymentMixResponse(String label, BigDecimal amount, boolean cash) { }
