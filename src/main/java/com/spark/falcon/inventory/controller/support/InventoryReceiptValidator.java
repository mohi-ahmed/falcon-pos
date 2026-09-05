package com.spark.falcon.inventory.controller.support;

import com.spark.falcon.inventory.dto.ReceiveBranchTransferCommand;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class InventoryReceiptValidator {

    public void validate(ReceiveBranchTransferCommand command) {
        List<ReceiveBranchTransferCommand.ItemReceipt> items = command.items();
        if (items == null || items.isEmpty()) {
            throw new InventoryReceiptValidationException("Enter at least one receipt quantity.");
        }

        Set<Long> itemIds = new HashSet<>();
        for (ReceiveBranchTransferCommand.ItemReceipt item : items) {
            if (item == null || item.transferItemId() == null) {
                throw new InventoryReceiptValidationException("A transfer item is missing from the receipt.");
            }
            if (!itemIds.add(item.transferItemId())) {
                throw new InventoryReceiptValidationException("The same transfer item cannot be entered twice in one receipt.");
            }

            BigDecimal accepted = nonNegative(item.acceptedQuantity());
            BigDecimal damaged = nonNegative(item.damagedQuantity());
            BigDecimal missing = nonNegative(item.missingQuantity());
            BigDecimal rejected = nonNegative(item.rejectedQuantity());
            BigDecimal total = accepted.add(damaged).add(missing).add(rejected);
            if (total.signum() <= 0) {
                throw new InventoryReceiptValidationException("Enter at least one receipt quantity for each displayed transfer item.");
            }
            if (damaged.add(missing).add(rejected).signum() > 0
                    && (item.discrepancyReason() == null || item.discrepancyReason().isBlank())) {
                throw new InventoryReceiptValidationException("Enter a discrepancy reason when damaged, missing, or rejected quantity is recorded.");
            }
        }
    }

    private BigDecimal nonNegative(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        if (normalized.signum() < 0) {
            throw new InventoryReceiptValidationException("Receipt quantities must not be negative.");
        }
        return normalized;
    }
}
