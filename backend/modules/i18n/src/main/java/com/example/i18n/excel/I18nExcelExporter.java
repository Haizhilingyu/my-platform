package com.example.i18n.excel;

import com.example.i18n.domain.I18nMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** 国际化消息 Excel 导出器。表头粗体灰底，key/module/description 列锁定，仅 value 列可编辑。 */
public final class I18nExcelExporter {

  public static final int COL_KEY = 0;
  public static final int COL_MODULE = 1;
  public static final int COL_DESCRIPTION = 2;
  public static final int COL_VALUE = 3;

  private I18nExcelExporter() {}

  public static byte[] export(List<I18nMessage> messages) throws IOException {
    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("i18n");

      CellStyle headerStyle = workbook.createCellStyle();
      Font headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerStyle.setFont(headerFont);
      headerStyle.setAlignment(HorizontalAlignment.CENTER);

      CellStyle lockedStyle = workbook.createCellStyle();
      lockedStyle.setLocked(true);

      Row header = sheet.createRow(0);
      String[] headers = {"Message Key", "Module", "Description", "Value"};
      for (int i = 0; i < headers.length; i++) {
        Cell cell = header.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      int rowIdx = 1;
      for (I18nMessage m : messages) {
        Row row = sheet.createRow(rowIdx++);
        Cell keyCell = row.createCell(COL_KEY);
        keyCell.setCellValue(safe(m.getMessageKey()));
        keyCell.setCellStyle(lockedStyle);

        Cell moduleCell = row.createCell(COL_MODULE);
        moduleCell.setCellValue(safe(m.getModule()));
        moduleCell.setCellStyle(lockedStyle);

        Cell descCell = row.createCell(COL_DESCRIPTION);
        descCell.setCellValue(safe(m.getDescription()));
        descCell.setCellStyle(lockedStyle);

        Cell valueCell = row.createCell(COL_VALUE);
        valueCell.setCellValue(safe(m.getValue()));
      }

      sheet.protectSheet("");
      sheet.setColumnWidth(COL_KEY, 12000);
      sheet.setColumnWidth(COL_VALUE, 16000);
      workbook.write(out);
      return out.toByteArray();
    }
  }

  private static String safe(String s) {
    return s != null ? s : "";
  }
}
