package com.spark.falcon.identity.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("An account already exists for this email.");
    }
}

