package com.spark.falcon.shared.export;

import lombok.RequiredArgsConstructor;
import org.openpdf.text.*;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class PdfExportWriter implements ExportWriter {
    private final ExportValueFormatter formatter;

    @Override public ExportFormat format() { return ExportFormat.PDF; }

    @Override
    public void write(ExportDocument export, OutputStream outputStream) throws IOException {
        Document pdf = new Document(PageSize.A4.rotate(), 24, 24, 28, 28);
        try {
            PdfWriter.getInstance(pdf, outputStream);
            pdf.open();
            pdf.add(new Paragraph(export.title(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
            String scope = String.join(" · ", export.businessName(), export.branchName()).replaceAll("^( · |· )|( · | ·)$", "");
            if (!scope.isBlank()) pdf.add(new Paragraph(scope, FontFactory.getFont(FontFactory.HELVETICA, 9)));
            pdf.add(new Paragraph("Generated: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .format(ZonedDateTime.now(export.zoneId())), FontFactory.getFont(FontFactory.HELVETICA, 8)));
            if (!export.filters().isEmpty()) {
                String filterText = export.filters().entrySet().stream()
                        .map(entry -> entry.getKey() + ": " + entry.getValue()).reduce((a, b) -> a + " · " + b).orElse("");
                pdf.add(new Paragraph(filterText, FontFactory.getFont(FontFactory.HELVETICA, 8)));
            }
            pdf.add(Chunk.NEWLINE);
            PdfPTable table = new PdfPTable(export.columns().size());
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (ExportColumn column : export.columns()) {
                PdfPCell cell = new PdfPCell(new Phrase(column.heading(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7)));
                cell.setBackgroundColor(new Color(226, 240, 234));
                cell.setPadding(4);
                table.addCell(cell);
            }
            export.rows().forEach(values -> {
                CsvExportWriter.validateWidth(export, values);
                for (int index = 0; index < values.length; index++) {
                    String text = formatter.format(values[index], export.columns().get(index).valueType(), export.zoneId());
                    PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA, 6.5f)));
                    cell.setPadding(3);
                    table.addCell(cell);
                }
            });
            pdf.add(table);
        } catch (DocumentException exception) {
            throw new IOException("Unable to generate PDF export.", exception);
        } finally {
            if (pdf.isOpen()) pdf.close();
        }
    }
}
