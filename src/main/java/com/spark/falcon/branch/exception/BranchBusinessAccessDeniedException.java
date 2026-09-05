package com.spark.falcon.branch.exception;

public class BranchBusinessAccessDeniedException extends RuntimeException {
    public BranchBusinessAccessDeniedException() {
        super("You do not have access to this business");
    }
}
