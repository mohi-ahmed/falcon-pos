package com.spark.falcon.settings.exception;

public class SettingsNotFoundException extends RuntimeException {
    public SettingsNotFoundException(String type) {
        super(type + " was not found");
    }
}
