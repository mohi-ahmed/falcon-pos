package com.spark.falcon.settings.dto.response;

import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;

import java.util.Set;

public record PaymentMethodResponse(
        Long id, Long businessId, String name, String code, String description, boolean cash,
        boolean transactionReferenceRequired, String reconciliationChannelReference,
        String reconciliationAccountReference, int displayOrder, ConfigurationStatus status,
        Set<Long> branchIds, boolean archived) {
}
