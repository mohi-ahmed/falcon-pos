package com.spark.falcon.branch.exception;

public class BranchCodeAlreadyUsedException extends RuntimeException {
    public BranchCodeAlreadyUsedException(String code) {
        super("Branch code '" + code + "' is already in use");
    }
}
