package com.example.i18n.excel;

import com.example.i18n.dto.I18nMessageImportDTO.Item;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** 国际化消息 Excel 解析器。校验表头列名，逐行读取 key 与 value 列。 */
public final class I18nExcelParser {

  private I18nExcelParser() {}

  public static List<Item> parse(InputStream xlsx) throws IOException {
    try (Workbook workbook = new XSSFWorkbook(xlsx)) {
      Sheet sheet = workbook.getSheetAt(0);
      if (sheet == null) {
        throw new IllegalArgumentException("Excel 文件无工作表");
      }
      Row header = sheet.getRow(0);
      if (header == null) {
        throw new IllegalArgumentException("第 1 行缺少表头");
      }
      validateHeader(header);

      List<Item> items = new ArrayList<>();
      for (int r = 1; r <= sheet.getLastRowNum(); r++) {
        Row row = sheet.getRow(r);
        if (row == null) {
          continue;
        }
        String key = stringCell(row, I18nExcelExporter.COL_KEY, r);
        String value = stringCell(row, I18nExcelExporter.COL_VALUE, r);
        if (key == null || key.isBlank()) {
          continue;
        }
        Item item = new Item();
        item.setMessageKey(key);
        item.setValue(value != null ? value : "");
        items.add(item);
      }
      return items;
    }
  }

  private static void validateHeader(Row header) {
    String[] expected = {"Message Key", "Module", "Description", "Value"};
    for (int i = 0; i < expected.length; i++) {
      Cell cell = header.getCell(i);
      String val = cell != null ? cell.getStringCellValue() : null;
      if (!expected[i].equalsIgnoreCase(val)) {
        throw new IllegalArgumentException(
            "第 1 行第 " + (i + 1) + " 列表头应为 [" + expected[i] + "]，实际为 [" + val + "]");
      }
    }
  }

  private static String stringCell(Row row, int col, int rowIdx) {
    Cell cell = row.getCell(col);
    if (cell == null) {
      return null;
    }
    if (cell.getCellType() == CellType.STRING) {
      return cell.getStringCellValue();
    }
    throw new IllegalArgumentException("第 " + (rowIdx + 1) + " 行第 " + (col + 1) + " 列应为文本类型");
  }
}
