package com.spark.falcon.businesssetup.exception;
public class BusinessAlreadyConfiguredException extends RuntimeException {
    public BusinessAlreadyConfiguredException() { super("This owner already has a configured workplace"); }
}
