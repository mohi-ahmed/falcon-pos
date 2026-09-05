package com.spark.falcon.shared.export;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;

@Component
@RequiredArgsConstructor
public class ExcelExportWriter implements ExportWriter {
    private static final int ROW_WINDOW = 100;
    private final ExportValueFormatter formatter;

    @Override public ExportFormat format() { return ExportFormat.XLSX; }

    @Override
    public void write(ExportDocument document, OutputStream outputStream) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW);
        workbook.setCompressTempFiles(true);
        try {
            Sheet sheet = workbook.createSheet(safeSheetName(document.title()));
            CellStyle headingStyle = headingStyle(workbook);
            int[] rowNumber = {0};
            Row heading = sheet.createRow(rowNumber[0]++);
            for (int index = 0; index < document.columns().size(); index++) {
                Cell cell = heading.createCell(index, CellType.STRING);
                cell.setCellValue(document.columns().get(index).heading());
                cell.setCellStyle(headingStyle);
                sheet.setColumnWidth(index, Math.min(60, Math.max(12,
                        document.columns().get(index).heading().length() + 3)) * 256);
            }
            document.rows().forEach(values -> {
                CsvExportWriter.validateWidth(document, values);
                Row row = sheet.createRow(rowNumber[0]++);
                for (int index = 0; index < values.length; index++) {
                    String text = formatter.format(values[index], document.columns().get(index).valueType(), document.zoneId());
                    Cell cell = row.createCell(index, CellType.STRING);
                    cell.setCellValue(safeSpreadsheetText(text));
                }
            });
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, document.columns().size() - 1));
            workbook.write(outputStream);
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    private CellStyle headingStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private String safeSheetName(String value) {
        String safe = value.replaceAll("[\\\\/*?:\\[\\]]", " ").trim();
        return safe.substring(0, Math.min(31, safe.length()));
    }

    private String safeSpreadsheetText(String value) {
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) return "'" + value;
        return value;
    }
}
