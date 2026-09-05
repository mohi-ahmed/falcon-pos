package com.spark.falcon.settings.dto.response;

import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;

import java.math.BigDecimal;

public record TaxRateResponse(
        Long id, Long businessId, String name, String code, BigDecimal rate, int displayOrder,
        ConfigurationStatus status, boolean archived) {
}
