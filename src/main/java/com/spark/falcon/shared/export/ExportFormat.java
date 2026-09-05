package com.spark.falcon.shared.export;

import org.springframework.http.MediaType;

import java.util.Locale;

public enum ExportFormat {
    PDF("pdf", MediaType.APPLICATION_PDF_VALUE),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("csv", "text/csv;charset=UTF-8");

    private final String extension;
    private final String contentType;

    ExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() { return extension; }
    public String contentType() { return contentType; }

    public static ExportFormat from(String value) {
        if (value == null) throw new InvalidExportFormatException("Export format is required.");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidExportFormatException("Unsupported export format: " + value);
        }
    }
}
