package com.suresell.orders.shared.export;
import com.suresell.orders.application.dto.ClosureResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
@Service
public class DailyClosureExcelExporter {
    public ByteArrayInputStream export(List<ClosureResponse> closures) throws IOException {
        String[] columns = { "ID", "Cajero", "Hora de Apertura", "Hora de Cierre", "Efectivo Esperado", "Tarjeta Esperada", "Nequi Esperado", "QR Esperado", "Total Esperado", "Efectivo Contado", "Tarjeta Contada", "Nequi Contado", "QR Contado", "Total Contado", "Diferencia", "Estado", "Notas" };
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream();) {
            Sheet sheet = workbook.createSheet("Historial de Cierres");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.BLUE_GREY.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("$#,##0.00"));
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            CellStyle statusBalancedStyle = workbook.createCellStyle();
            statusBalancedStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            statusBalancedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle statusPositiveDiffStyle = workbook.createCellStyle();
            statusPositiveDiffStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            statusPositiveDiffStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle statusNegativeDiffStyle = workbook.createCellStyle();
            statusNegativeDiffStyle.setFillForegroundColor(IndexedColors.CORAL.getIndex());
            statusNegativeDiffStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < columns.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(columns[col]);
                cell.setCellStyle(headerStyle);
            }
            int rowIdx = 1;
            for (ClosureResponse closure : closures) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(closure.id().toString());
                row.createCell(1).setCellValue(closure.userName());
                row.createCell(2).setCellValue(closure.openingTime().format(dateFormatter));
                row.createCell(3).setCellValue(closure.closingTime().format(dateFormatter));
                Cell expectedCashCell = row.createCell(4);
                expectedCashCell.setCellValue(closure.totalExpectedCash().doubleValue());
                expectedCashCell.setCellStyle(currencyStyle);
                Cell expectedCardCell = row.createCell(5);
                expectedCardCell.setCellValue(closure.totalExpectedCard().doubleValue());
                expectedCardCell.setCellStyle(currencyStyle);
                Cell expectedNequiCell = row.createCell(6);
                expectedNequiCell.setCellValue(closure.totalExpectedNequi().doubleValue());
                expectedNequiCell.setCellStyle(currencyStyle);
                Cell expectedQrCell = row.createCell(7);
                expectedQrCell.setCellValue(closure.totalExpectedQr().doubleValue());
                expectedQrCell.setCellStyle(currencyStyle);
                Cell totalExpectedCell = row.createCell(8);
                totalExpectedCell.setCellValue(closure.totalExpected().doubleValue());
                totalExpectedCell.setCellStyle(currencyStyle);
                Cell countedCashCell = row.createCell(9);
                countedCashCell.setCellValue(closure.totalCountedCash().doubleValue());
                countedCashCell.setCellStyle(currencyStyle);
                Cell countedCardCell = row.createCell(10);
                countedCardCell.setCellValue(closure.totalCountedCard().doubleValue());
                countedCardCell.setCellStyle(currencyStyle);
                Cell countedNequiCell = row.createCell(11);
                countedNequiCell.setCellValue(closure.totalCountedNequi().doubleValue());
                countedNequiCell.setCellStyle(currencyStyle);
                Cell countedQrCell = row.createCell(12);
                countedQrCell.setCellValue(closure.totalCountedQr().doubleValue());
                countedQrCell.setCellStyle(currencyStyle);
                Cell totalCountedCell = row.createCell(13);
                totalCountedCell.setCellValue(closure.totalCounted().doubleValue());
                totalCountedCell.setCellStyle(currencyStyle);
                Cell differenceCell = row.createCell(14);
                differenceCell.setCellValue(closure.differenceAmount().doubleValue());
                differenceCell.setCellStyle(currencyStyle);
                Cell statusCell = row.createCell(15);
                statusCell.setCellValue(closure.status());
                if (closure.differenceAmount().compareTo(BigDecimal.ZERO) == 0) {
                    statusCell.setCellStyle(statusBalancedStyle);
                } else if (closure.differenceAmount().compareTo(BigDecimal.ZERO) > 0) {
                    statusCell.setCellStyle(statusPositiveDiffStyle);
                } else {
                    statusCell.setCellStyle(statusNegativeDiffStyle);
                }
                row.createCell(16).setCellValue(closure.notes());
            }
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }
}
