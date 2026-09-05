package com.spark.falcon.shared.export;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ExportResponse {
    private final ExportService exportService;
    private final ExportFileNameFactory fileNameFactory;

    public void write(String requestedFormat, ExportDocument document, HttpServletResponse response) throws IOException {
        ExportFormat format = ExportFormat.from(requestedFormat);
        String fileName = fileNameFactory.create(document.title(), format);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(format.contentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
        response.setHeader("X-Content-Type-Options", "nosniff");
        exportService.export(format, document, response.getOutputStream());
    }
}
