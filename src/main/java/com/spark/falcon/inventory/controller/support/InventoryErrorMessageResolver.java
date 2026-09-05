package com.spark.falcon.inventory.controller.support;

import com.spark.falcon.inventory.exception.InventoryAccessDeniedException;
import com.spark.falcon.inventory.exception.InventoryOperationNotFoundException;
import com.spark.falcon.inventory.exception.InventoryOperationStateException;
import com.spark.falcon.inventory.exception.InventoryPostingException;
import com.spark.falcon.inventory.exception.InventoryStockNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class InventoryErrorMessageResolver {

    public String resolve(RuntimeException exception) {
        if (exception instanceof InventoryAccessDeniedException) {
            return "This inventory action is not available for the current branch or product assignment.";
        }
        if (exception instanceof InventoryStockNotFoundException) {
            return "Stock is not available for the selected product in this branch.";
        }
        if (exception instanceof InventoryOperationNotFoundException) {
            return "This inventory record is no longer available.";
        }
        if (exception instanceof InventoryPostingException
                || exception instanceof InventoryOperationStateException
                || exception instanceof InventoryReceiptValidationException) {
            return hasText(exception.getMessage())
                    ? exception.getMessage()
                    : "The inventory action could not be completed with the current values.";
        }
        return "The inventory action could not be completed.";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
