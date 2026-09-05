package com.spark.falcon.shared.export;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class CsvExportWriter implements ExportWriter {
    private final ExportValueFormatter formatter;

    @Override public ExportFormat format() { return ExportFormat.CSV; }

    @Override
    public void write(ExportDocument document, OutputStream outputStream) throws IOException {
        outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writeRow(writer, document.columns().stream().map(ExportColumn::heading).toArray());
        document.rows().forEach(values -> {
            validateWidth(document, values);
            Object[] formatted = new Object[values.length];
            for (int index = 0; index < values.length; index++) {
                formatted[index] = safeSpreadsheetText(formatter.format(values[index],
                        document.columns().get(index).valueType(), document.zoneId()));
            }
            writeRow(writer, formatted);
        });
        writer.flush();
    }

    private void writeRow(Writer writer, Object[] values) throws IOException {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) writer.write(',');
            String value = values[index] == null ? "" : String.valueOf(values[index]);
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }

    private String safeSpreadsheetText(String value) {
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) return "'" + value;
        return value;
    }

    static void validateWidth(ExportDocument document, Object[] values) throws IOException {
        if (values.length != document.columns().size()) {
            throw new IOException("Export row has " + values.length + " values for "
                    + document.columns().size() + " columns.");
        }
    }
}
