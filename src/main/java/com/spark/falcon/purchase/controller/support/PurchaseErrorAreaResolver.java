package com.spark.falcon.purchase.controller.support;

import com.spark.falcon.cashmanagement.exception.CashManagementAccessDeniedException;
import com.spark.falcon.cashmanagement.exception.CashManagementValidationException;
import com.spark.falcon.inventory.exception.InventoryAccessDeniedException;
import com.spark.falcon.inventory.exception.InventoryPostingException;
import org.springframework.stereotype.Component;

@Component
public class PurchaseErrorAreaResolver {

    public PurchaseErrorArea resolvePurchaseArea(RuntimeException exception) {
        if (isCashManagementException(exception)) {
            return PurchaseErrorArea.PAYMENT;
        }
        if (isInventoryException(exception)) {
            return PurchaseErrorArea.ITEMS;
        }
        return PurchaseErrorArea.FORM;
    }

    public PurchaseErrorArea resolveReturnArea(RuntimeException exception) {
        if (isCashManagementException(exception)) {
            return PurchaseErrorArea.SETTLEMENT;
        }
        if (isInventoryException(exception)) {
            return PurchaseErrorArea.ITEMS;
        }
        return PurchaseErrorArea.FORM;
    }

    private boolean isCashManagementException(RuntimeException exception) {
        return exception instanceof CashManagementValidationException
                || exception instanceof CashManagementAccessDeniedException;
    }

    private boolean isInventoryException(RuntimeException exception) {
        return exception instanceof InventoryPostingException
                || exception instanceof InventoryAccessDeniedException;
    }
}
