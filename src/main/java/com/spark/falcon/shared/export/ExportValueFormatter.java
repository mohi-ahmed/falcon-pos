package com.spark.falcon.shared.export;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;

@Component
public class ExportValueFormatter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String format(Object value, ExportColumn.ValueType type, ZoneId zoneId) {
        if (value == null) return "";
        if (value instanceof Instant instant) return DATE_TIME.format(instant.atZone(zoneId));
        if (value instanceof OffsetDateTime dateTime) return DATE_TIME.format(dateTime.atZoneSameInstant(zoneId));
        if (value instanceof ZonedDateTime dateTime) return DATE_TIME.format(dateTime.withZoneSameInstant(zoneId));
        if (value instanceof LocalDateTime dateTime) return DATE_TIME.format(dateTime);
        if (value instanceof LocalDate date) return DATE.format(date);
        if (value instanceof BigDecimal decimal && type == ExportColumn.ValueType.MONEY) {
            return decimal.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        return String.valueOf(value);
    }
}
