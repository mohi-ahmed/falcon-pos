package com.spark.falcon.inventory.controller.support;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Component
public class InventoryFilterParser {

    public Long parseLong(String value, String label, List<String> errors) {
        if (!hasText(value)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0) {
                errors.add(label + " must be greater than zero.");
                return null;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            errors.add(label + " must be a valid number.");
            return null;
        }
    }

    public Boolean parseBoolean(String value, String label, List<String> errors) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalized)) {
            return Boolean.FALSE;
        }
        errors.add(label + " must be true or false.");
        return null;
    }

    public <E extends Enum<E>> E parseEnum(String value, Class<E> enumType, String label, List<String> errors) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            errors.add(label + " is not valid.");
            return null;
        }
    }

    public DateRange parseDateRange(String fromValue, String toValue, List<String> errors) {
        Instant from = parseInstant(fromValue, "From date", errors);
        Instant to = parseInstant(toValue, "To date", errors);
        if (from != null && to != null && from.isAfter(to)) {
            errors.add("From date must not be after To date.");
            return new DateRange(null, null);
        }
        return new DateRange(from, to);
    }

    public String errorMessage(List<String> errors) {
        return String.join(" ", errors);
    }

    private Instant parseInstant(String value, String label, List<String> errors) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            errors.add(label + " must use ISO-8601 UTC format, for example 2026-08-30T00:00:00Z.");
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record DateRange(Instant from, Instant to) {
    }
}
