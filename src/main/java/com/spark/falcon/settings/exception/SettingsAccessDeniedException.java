package com.spark.falcon.settings.exception;

public class SettingsAccessDeniedException extends RuntimeException {
    public SettingsAccessDeniedException() {
        super("The requested settings record is not available for this business or branch");
    }
}
