package com.spark.falcon.shared.export;

public record ExportColumn(String heading, ValueType valueType) {
    public ExportColumn {
        if (heading == null || heading.isBlank()) throw new IllegalArgumentException("Column heading is required.");
        if (valueType == null) valueType = ValueType.TEXT;
    }

    public static ExportColumn text(String heading) { return new ExportColumn(heading, ValueType.TEXT); }
    public static ExportColumn number(String heading) { return new ExportColumn(heading, ValueType.NUMBER); }
    public static ExportColumn money(String heading) { return new ExportColumn(heading, ValueType.MONEY); }
    public static ExportColumn date(String heading) { return new ExportColumn(heading, ValueType.DATE); }
    public static ExportColumn dateTime(String heading) { return new ExportColumn(heading, ValueType.DATE_TIME); }

    public enum ValueType { TEXT, NUMBER, MONEY, DATE, DATE_TIME }
}
