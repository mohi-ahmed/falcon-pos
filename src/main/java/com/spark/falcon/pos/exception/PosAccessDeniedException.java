package com.spark.falcon.pos.exception;

public class PosAccessDeniedException extends RuntimeException {
    public PosAccessDeniedException() {
        super("You do not have access to this POS branch");
    }
}
