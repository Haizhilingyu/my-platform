package com.example.i18n.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.result.PageResult;
import com.example.common.result.Result;
import com.example.i18n.dto.I18nMessageImportDTO;
import com.example.i18n.dto.I18nMessageQueryDTO;
import com.example.i18n.dto.I18nMessageUpdateDTO;
import com.example.i18n.dto.I18nMessageVO;
import com.example.i18n.service.I18nMessageService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("I18nMessageController 请求-响应链路")
class I18nMessageControllerTest {

  @Mock private I18nMessageService service;
  @InjectMocks private I18nMessageController controller;

  private I18nMessageVO vo(long id, String key) {
    I18nMessageVO v = new I18nMessageVO();
    v.setId(id);
    v.setMessageKey(key);
    v.setLocale("zh-CN");
    v.setModule("sys");
    v.setValue("值");
    return v;
  }

  @Test
  @DisplayName("list：返回分页 VO")
  void list_returnsPage() {
    when(service.list(any(I18nMessageQueryDTO.class)))
        .thenReturn(PageResult.of(List.of(vo(1L, "k")), 1, 1, 20));
    Result<PageResult<I18nMessageVO>> result = controller.list(new I18nMessageQueryDTO());
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data().list()).hasSize(1);
  }

  @Test
  @DisplayName("all：返回扁平 map")
  void all_returnsMap() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("k", "v");
    when(service.getFlatMap("zh-CN")).thenReturn(map);
    Result<Map<String, String>> result = controller.all("zh-CN");
    assertThat(result.data()).containsEntry("k", "v");
  }

  @Test
  @DisplayName("export：json 返回 Result 列表")
  void export_json() throws Exception {
    when(service.exportByLocale("zh-CN")).thenReturn(List.of(vo(1L, "k")));
    Object result = controller.export("zh-CN", "json");
    assertThat(result).isInstanceOf(Result.class);
    assertThat(((Result<?>) result).isSuccess()).isTrue();
  }

  @Test
  @DisplayName("export：xlsx 返回 ResponseEntity 带 Content-Disposition")
  void export_xlsx() throws Exception {
    when(service.exportByLocale("zh-CN")).thenReturn(List.of(vo(1L, "k")));
    ResponseEntity<ByteArrayResource> resp =
        (ResponseEntity<ByteArrayResource>) controller.export("zh-CN", "xlsx");
    assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(resp.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION))
        .anyMatch(h -> h.contains("attachment") && h.contains("i18n_zh-CN.xlsx"));
    assertThat(resp.getHeaders().getContentType())
        .isEqualTo(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    assertThat(resp.getBody().getByteArray().length).isGreaterThan(0);
  }

  @Test
  @DisplayName("update：透传 id 与 dto，返回 VO")
  void update_returnsVo() {
    when(service.update(eq(1L), any(I18nMessageUpdateDTO.class))).thenReturn(vo(1L, "k"));
    I18nMessageUpdateDTO dto = new I18nMessageUpdateDTO();
    dto.setValue("新值");
    Result<I18nMessageVO> result = controller.update(1L, dto);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data().getId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("import：JSON 路径透传 dto，返回更新条数")
  void import_json() throws Exception {
    I18nMessageImportDTO dto = new I18nMessageImportDTO();
    dto.setLocale("zh-CN");
    I18nMessageImportDTO.Item item = new I18nMessageImportDTO.Item();
    item.setMessageKey("k");
    item.setValue("v");
    dto.setItems(List.of(item));
    when(service.importMessages(dto)).thenReturn(1);
    Result<Integer> result = controller.importMessages(dto, null, null);
    assertThat(result.data()).isEqualTo(1);
    verify(service).importMessages(dto);
  }
}
