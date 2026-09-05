package com.spark.falcon.settings.dto.command;

import java.math.BigDecimal;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;

public record CreateTaxRateCommand(
        Long ownerId, String name, String code, BigDecimal rate, int displayOrder, ConfigurationStatus status) {
}
