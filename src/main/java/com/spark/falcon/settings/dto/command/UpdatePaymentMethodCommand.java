package com.spark.falcon.settings.dto.command;

import java.util.Set;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;

public record UpdatePaymentMethodCommand(
        Long ownerId, Long paymentMethodId, String name, String code, String description, boolean cash,
        boolean transactionReferenceRequired, String reconciliationChannelReference,
        String reconciliationAccountReference, int displayOrder, Set<Long> branchIds, ConfigurationStatus status) {
}
