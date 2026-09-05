package com.spark.falcon.shared.export;

import java.io.IOException;
import java.io.OutputStream;

public interface ExportWriter {
    ExportFormat format();
    void write(ExportDocument document, OutputStream outputStream) throws IOException;
}
