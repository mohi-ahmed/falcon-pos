package com.spark.falcon.purchase.service;

import com.spark.falcon.purchase.exception.StockImportValidationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
class StockImportCsvParser {

    List<ParsedCsvRow> parse(byte[] content) {
        if (content == null || content.length == 0) {
            throw new StockImportValidationException("Stock Import CSV file is empty");
        }

        String text = new String(content, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);

        List<List<String>> records = parseRecords(text);
        if (records.isEmpty()) throw new StockImportValidationException("Stock Import CSV file has no header row");

        List<String> header = records.getFirst().stream().map(this::normalizeHeader).toList();
        if (header.stream().allMatch(String::isBlank)) {
            throw new StockImportValidationException("Stock Import CSV header row is invalid");
        }

        List<ParsedCsvRow> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            if (record.stream().allMatch(value -> value == null || value.isBlank())) continue;

            Map<String, String> values = new LinkedHashMap<>();
            for (int column = 0; column < header.size(); column++) {
                String key = header.get(column);
                if (key.isBlank()) continue;
                String value = column < record.size() ? record.get(column).trim() : "";
                values.put(key, value);
            }
            rows.add(new ParsedCsvRow(i + 1, values));
        }
        return rows;
    }

    private List<List<String>> parseRecords(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }

            if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                records.add(row);
                row = new ArrayList<>();
            } else {
                field.append(ch);
            }
        }

        if (quoted) throw new StockImportValidationException("Stock Import CSV contains an unclosed quoted field");
        if (!row.isEmpty() || field.length() > 0) {
            row.add(field.toString());
            records.add(row);
        }
        return records;
    }

    private String normalizeHeader(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    record ParsedCsvRow(int rowNumber, Map<String, String> values) {
        String value(String... names) {
            for (String name : names) {
                String value = values.get(name);
                if (value != null && !value.isBlank()) return value.trim();
            }
            return null;
        }

        boolean has(String name) {
            String value = values.get(name);
            return value != null && !value.isBlank();
        }
    }
}
