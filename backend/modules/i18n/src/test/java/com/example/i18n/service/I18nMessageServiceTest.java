package com.example.i18n.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.BizException;
import com.example.common.exception.NotFoundException;
import com.example.common.i18n.Messages;
import com.example.common.result.PageResult;
import com.example.i18n.domain.I18nMessage;
import com.example.i18n.dto.I18nMessageImportDTO;
import com.example.i18n.dto.I18nMessageQueryDTO;
import com.example.i18n.dto.I18nMessageUpdateDTO;
import com.example.i18n.dto.I18nMessageVO;
import com.example.i18n.event.I18nMessageUpdatedEvent;
import com.example.i18n.repository.I18nMessageRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@DisplayName("I18nMessageService 单元测试")
class I18nMessageServiceTest {

  @Mock private I18nMessageRepository repository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @InjectMocks private I18nMessageService service;

  @BeforeAll
  static void initMessages() {
    ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
    ms.setBasename("classpath:i18n/messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setFallbackToSystemLocale(false);
    ms.setUseCodeAsDefaultMessage(true);
    new Messages(ms).init();
  }

  private I18nMessage message(Long id, String key, String locale, String module, String value) {
    I18nMessage m = new I18nMessage();
    m.setId(id);
    m.setMessageKey(key);
    m.setLocale(locale);
    m.setModule(module);
    m.setValue(value);
    return m;
  }

  @Test
  @DisplayName("update：存在则更新 value 并发布事件")
  void update_publishesEvent() {
    when(repository.findById(1L)).thenReturn(Optional.of(message(1L, "k", "zh-CN", "sys", "旧")));
    when(repository.save(any(I18nMessage.class)))
        .thenAnswer(inv -> inv.getArgument(0, I18nMessage.class));

    I18nMessageUpdateDTO dto = new I18nMessageUpdateDTO();
    dto.setValue("新");
    I18nMessageVO vo = service.update(1L, dto);

    assertThat(vo.getValue()).isEqualTo("新");
    ArgumentCaptor<I18nMessageUpdatedEvent> captor =
        ArgumentCaptor.forClass(I18nMessageUpdatedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().getLocale()).isEqualTo("zh-CN");
  }

  @Test
  @DisplayName("update：不存在抛 NotFoundException")
  void update_notFound() {
    when(repository.findById(99L)).thenReturn(Optional.empty());
    I18nMessageUpdateDTO dto = new I18nMessageUpdateDTO();
    dto.setValue("v");
    assertThatThrownBy(() -> service.update(99L, dto)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("import：拒绝未知 key")
  void import_rejectsUnknownKey() {
    when(repository.findByLocale("zh-CN"))
        .thenReturn(List.of(message(1L, "known.key", "zh-CN", "sys", "v")));
    I18nMessageImportDTO dto = new I18nMessageImportDTO();
    dto.setLocale("zh-CN");
    I18nMessageImportDTO.Item item = new I18nMessageImportDTO.Item();
    item.setMessageKey("unknown.key");
    item.setValue("x");
    dto.setItems(List.of(item));

    assertThatThrownBy(() -> service.importMessages(dto)).isInstanceOf(BizException.class);
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("import：已知 key 全部更新并返回条数")
  void import_updatesKnownKeys() {
    when(repository.findByLocale("zh-CN"))
        .thenReturn(
            List.of(
                message(1L, "a.key", "zh-CN", "sys", "old-a"),
                message(2L, "b.key", "zh-CN", "sys", "old-b")));
    when(repository.save(any(I18nMessage.class))).thenAnswer(inv -> inv.getArgument(0));

    I18nMessageImportDTO dto = new I18nMessageImportDTO();
    dto.setLocale("zh-CN");
    I18nMessageImportDTO.Item a = new I18nMessageImportDTO.Item();
    a.setMessageKey("a.key");
    a.setValue("new-a");
    I18nMessageImportDTO.Item b = new I18nMessageImportDTO.Item();
    b.setMessageKey("b.key");
    b.setValue("new-b");
    dto.setItems(List.of(a, b));

    int count = service.importMessages(dto);
    assertThat(count).isEqualTo(2);
    verify(repository, times(2)).save(any(I18nMessage.class));
    verify(eventPublisher).publishEvent(any(I18nMessageUpdatedEvent.class));
  }

  @Test
  @DisplayName("list：locale 过滤生效，返回 VO 分页")
  void list_filterByLocale() {
    when(repository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(message(1L, "k1", "zh-CN", "sys", "v1")), PageRequest.of(0, 20), 1));
    I18nMessageQueryDTO q = new I18nMessageQueryDTO();
    q.setLocale("zh-CN");
    PageResult<I18nMessageVO> result = service.list(q);
    assertThat(result.list()).hasSize(1);
    assertThat(result.list().get(0).getMessageKey()).isEqualTo("k1");
    assertThat(result.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("list：无过滤返回全量分页")
  void list_noFilter() {
    when(repository.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    PageResult<I18nMessageVO> result = service.list(new I18nMessageQueryDTO());
    assertThat(result.list()).isEmpty();
  }

  @Test
  @DisplayName("exportByLocale：返回 locale 下所有 VO")
  void export_returnsAll() {
    when(repository.findByLocale("en")).thenReturn(List.of(message(1L, "k", "en", "sys", "val")));
    List<I18nMessageVO> data = service.exportByLocale("en");
    assertThat(data).hasSize(1);
    assertThat(data.get(0).getValue()).isEqualTo("val");
  }

  @Test
  @DisplayName("getFlatMap：返回扁平 {key: value} 映射")
  void getFlatMap_returnsMap() {
    when(repository.findByLocale("zh-CN"))
        .thenReturn(
            List.of(message(1L, "a", "zh-CN", "sys", "1"), message(2L, "b", "zh-CN", "sys", "2")));
    Map<String, String> map = service.getFlatMap("zh-CN");
    assertThat(map).containsEntry("a", "1").containsEntry("b", "2").hasSize(2);
  }
}
