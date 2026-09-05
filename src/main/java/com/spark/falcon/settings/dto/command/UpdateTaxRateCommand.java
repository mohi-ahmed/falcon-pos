package com.spark.falcon.settings.dto.command;

import java.math.BigDecimal;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;

public record UpdateTaxRateCommand(
        Long ownerId, Long taxRateId, String name, String code, BigDecimal rate, int displayOrder, ConfigurationStatus status) {
}
