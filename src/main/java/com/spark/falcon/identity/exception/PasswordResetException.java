package com.spark.falcon.identity.exception;

import lombok.Getter;

@Getter
public class PasswordResetException extends RuntimeException {

    private final Reason reason;

    public PasswordResetException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public enum Reason {
        INVALID_CODE,
        EXPIRED_CODE,
        ATTEMPT_LIMIT_REACHED,
        INVALID_SESSION
    }
}