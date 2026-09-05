package com.spark.falcon.cashmanagement.exception;

public class CashManagementAccessDeniedException extends RuntimeException {
    public CashManagementAccessDeniedException() {
        super("Cash Management access denied");
    }
}
