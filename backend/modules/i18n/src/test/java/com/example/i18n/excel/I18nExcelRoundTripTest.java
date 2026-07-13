package com.example.i18n.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.i18n.domain.I18nMessage;
import com.example.i18n.dto.I18nMessageImportDTO.Item;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Excel 导出→解析 往返测试")
class I18nExcelRoundTripTest {

  private I18nMessage msg(String key, String module, String desc, String value) {
    I18nMessage m = new I18nMessage();
    m.setMessageKey(key);
    m.setModule(module);
    m.setDescription(desc);
    m.setValue(value);
    return m;
  }

  @Test
  @DisplayName("导出后再解析，key/value 完全一致")
  void roundTrip_preservesKeyAndValue() throws Exception {
    List<I18nMessage> messages =
        List.of(
            msg("sys.menu.1.name", "sys", "系统管理菜单", "系统管理"),
            msg("common.save", "common", null, "保存"),
            msg("validation.user.username.notBlank", "sys", "用户名校验", "用户名不能为空"));

    byte[] bytes = I18nExcelExporter.export(messages);
    List<Item> items = I18nExcelParser.parse(new ByteArrayInputStream(bytes));

    assertThat(items).hasSize(messages.size());
    for (int i = 0; i < messages.size(); i++) {
      assertThat(items.get(i).getMessageKey()).isEqualTo(messages.get(i).getMessageKey());
      assertThat(items.get(i).getValue()).isEqualTo(messages.get(i).getValue());
    }
  }

  @Test
  @DisplayName("导出文件表头与列锁定结构正确")
  void exportStructure() throws Exception {
    byte[] bytes = I18nExcelExporter.export(List.of(msg("k", "sys", "d", "v")));
    try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = wb.getSheetAt(0);
      Row header = sheet.getRow(0);
      assertThat(header.getCell(I18nExcelExporter.COL_KEY).getStringCellValue())
          .isEqualTo("Message Key");
      assertThat(header.getCell(I18nExcelExporter.COL_MODULE).getStringCellValue())
          .isEqualTo("Module");
      assertThat(header.getCell(I18nExcelExporter.COL_DESCRIPTION).getStringCellValue())
          .isEqualTo("Description");
      assertThat(header.getCell(I18nExcelExporter.COL_VALUE).getStringCellValue())
          .isEqualTo("Value");
    }
  }

  @Test
  @DisplayName("解析时表头错误抛 IllegalArgumentException")
  void parse_invalidHeader() throws Exception {
    try (Workbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("s");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("WRONG");
      header.createCell(1).setCellValue("Module");
      header.createCell(2).setCellValue("Description");
      header.createCell(3).setCellValue("Value");
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      wb.write(out);
      assertThatThrownBy(() -> I18nExcelParser.parse(new ByteArrayInputStream(out.toByteArray())))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("表头");
    }
  }
}
