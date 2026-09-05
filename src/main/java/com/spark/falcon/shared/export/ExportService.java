package com.spark.falcon.shared.export;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class ExportService {
    private final Map<ExportFormat, ExportWriter> writers = new EnumMap<>(ExportFormat.class);

    public ExportService(List<ExportWriter> writers) {
        writers.forEach(writer -> this.writers.put(writer.format(), writer));
        for (ExportFormat format : ExportFormat.values()) {
            if (!this.writers.containsKey(format)) throw new IllegalStateException("Missing export writer for " + format);
        }
    }

    public void export(ExportFormat format, ExportDocument document, OutputStream outputStream) throws IOException {
        writers.get(format).write(document, outputStream);
    }
}
