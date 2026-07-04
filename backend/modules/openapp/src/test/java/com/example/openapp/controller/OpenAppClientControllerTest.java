package com.example.openapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.NotFoundException;
import com.example.common.result.PageResult;
import com.example.common.result.Result;
import com.example.openapp.client.JdbcRegisteredClientRepository;
import com.example.openapp.client.JdbcRegisteredClientRepository.OpenAppClientRow;
import com.example.openapp.client.dto.OpenAppClientCreateDTO;
import com.example.openapp.client.dto.OpenAppClientUpdateDTO;
import com.example.openapp.client.dto.OpenAppClientVO;
import com.example.openapp.client.dto.OpenAppSecretResult;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("外部应用客户端管理 Controller")
class OpenAppClientControllerTest {

  @Mock private JdbcRegisteredClientRepository repository;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private OpenAppClientController controller;

  private static OpenAppClientRow row(Long id, String clientId, String name, boolean enabled) {
    return new OpenAppClientRow(
        id,
        clientId,
        name,
        Set.of("http://cb"),
        Set.of("http://logout"),
        Set.of("openid", "notify:publish"),
        Set.of("authorization_code", "refresh_token"),
        enabled,
        Instant.parse("2025-01-01T00:00:00Z"));
  }

  @Test
  @DisplayName("list：分页 + 关键字，转换为 VO")
  void list_returnsPageOfVos() {
    when(repository.listRows(0, 10, "foo", null))
        .thenReturn(List.of(row(1L, "app-1", "Foo", true)));
    when(repository.countRows("foo", null)).thenReturn(1L);

    Result<PageResult<OpenAppClientVO>> result = controller.list("foo", null, 1, 10);

    assertThat(result.isSuccess()).isTrue();
    PageResult<OpenAppClientVO> page = result.data();
    assertThat(page.total()).isEqualTo(1L);
    assertThat(page.list()).hasSize(1);
    OpenAppClientVO vo = page.list().get(0);
    assertThat(vo.id()).isEqualTo(1L);
    assertThat(vo.clientId()).isEqualTo("app-1");
    assertThat(vo.clientName()).isEqualTo("Foo");
    assertThat(vo.scopes()).containsExactly("notify:publish", "openid");
    assertThat(vo.enabled()).isTrue();
  }

  @Test
  @DisplayName("list：分页参数越界 → 回退到默认值")
  void list_clampsInvalidPaging() {
    when(repository.listRows(0, 10, null, null)).thenReturn(List.of());
    when(repository.countRows(null, null)).thenReturn(0L);

    controller.list(null, null, -1, 9999);

    verify(repository).listRows(0, 10, null, null);
  }

  @Test
  @DisplayName("get：找到 → 返回 VO")
  void get_returnsVo() {
    when(repository.findRowById(7L)).thenReturn(row(7L, "app-7", "Seven", true));

    Result<OpenAppClientVO> result = controller.get(7L);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data().clientId()).isEqualTo("app-7");
  }

  @Test
  @DisplayName("get：未找到 → 404 NotFoundException")
  void get_notFound() {
    when(repository.findRowById(99L)).thenReturn(null);

    assertThatThrownBy(() -> controller.get(99L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("99");
  }

  @Test
  @DisplayName("create：生成 clientId+secret，BCrypt 哈希存库，明文 secret 仅返回一次")
  void create_generatesIdAndSecret_returnsPlaintextOnce() {
    OpenAppClientCreateDTO dto =
        new OpenAppClientCreateDTO(
            "My App",
            List.of("http://cb"),
            List.of("http://logout"),
            List.of("openid", "notify:publish"),
            List.of("authorization_code", "refresh_token"));
    when(passwordEncoder.encode(anyString())).thenReturn("BCRYPT-HASH");
    when(repository.findRowByClientId(anyString())).thenReturn(row(42L, "app-XYZ", "My App", true));

    Result<OpenAppSecretResult> result = controller.create(dto);

    assertThat(result.isSuccess()).isTrue();
    OpenAppSecretResult secret = result.data();
    assertThat(secret.id()).isEqualTo(42L);
    assertThat(secret.clientId()).startsWith("app-");
    assertThat(secret.clientSecret()).hasSize(40);

    ArgumentCaptor<RegisteredClient> captor = ArgumentCaptor.forClass(RegisteredClient.class);
    verify(repository).insert(captor.capture(), eq("My App"));
    RegisteredClient saved = captor.getValue();
    assertThat(saved.getClientSecret()).isEqualTo("BCRYPT-HASH");
    assertThat(saved.getClientId()).startsWith("app-");
    assertThat(saved.getScopes()).containsExactlyInAnyOrder("openid", "notify:publish");
    assertThat(saved.getAuthorizationGrantTypes())
        .contains(AuthorizationGrantType.AUTHORIZATION_CODE);
    assertThat(saved.getClientAuthenticationMethods())
        .contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
  }

  @Test
  @DisplayName("update：找到 → 全字段更新（不触及 secret/client_id）")
  void update_updatesAllFields() {
    when(repository.findRowById(5L)).thenReturn(row(5L, "app-5", "Old", true));

    OpenAppClientUpdateDTO dto =
        new OpenAppClientUpdateDTO(
            "New Name",
            List.of("http://new"),
            List.of(),
            List.of("openid"),
            List.of("client_credentials"),
            false);

    Result<Void> result = controller.update(5L, dto);

    assertThat(result.isSuccess()).isTrue();
    verify(repository)
        .updateManagementFields(
            eq(5L), eq("New Name"), any(), any(), any(), any(), any(), eq(false));
  }

  @Test
  @DisplayName("update：未找到 → 404")
  void update_notFound() {
    when(repository.findRowById(1L)).thenReturn(null);
    assertThatThrownBy(
            () ->
                controller.update(
                    1L, new OpenAppClientUpdateDTO("X", null, null, null, null, true)))
        .isInstanceOf(NotFoundException.class);
    verify(repository, never())
        .updateManagementFields(
            anyLong(), anyString(), any(), any(), any(), any(), any(), anyBoolean());
  }

  @Test
  @DisplayName("delete：找到 → 调用 deleteRowById")
  void delete_removesRow() {
    when(repository.deleteRowById(3L)).thenReturn(1);

    Result<Void> result = controller.delete(3L);

    assertThat(result.isSuccess()).isTrue();
    verify(repository).deleteRowById(3L);
  }

  @Test
  @DisplayName("delete：未找到 → 404（不调 delete）")
  void delete_notFound() {
    when(repository.deleteRowById(3L)).thenReturn(0);

    assertThatThrownBy(() -> controller.delete(3L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("reset-secret：生成新 secret，BCrypt 哈希写入")
  void resetSecret_generatesNewSecret() {
    when(repository.findRowById(8L)).thenReturn(row(8L, "app-8", "Eight", true));
    when(passwordEncoder.encode(anyString())).thenReturn("NEW-HASH");

    Result<OpenAppSecretResult> result = controller.resetSecret(8L);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.data().clientSecret()).hasSize(40);
    verify(repository).updateSecret(eq("app-8"), eq("NEW-HASH"));
  }

  @Test
  @DisplayName("reset-secret：未找到 → 404")
  void resetSecret_notFound() {
    when(repository.findRowById(99L)).thenReturn(null);
    assertThatThrownBy(() -> controller.resetSecret(99L)).isInstanceOf(NotFoundException.class);
    verify(repository, never()).updateSecret(anyString(), anyString());
  }

  @Test
  @DisplayName("create：clientId 必须以 'app-' 前缀 + 24 个随机字符")
  void create_clientIdHasPrefixAndLength() {
    OpenAppClientCreateDTO dto =
        new OpenAppClientCreateDTO(
            "X", List.of("http://cb"), null, List.of("openid"), List.of("client_credentials"));
    when(passwordEncoder.encode(anyString())).thenReturn("H");
    when(repository.findRowByClientId(anyString()))
        .thenAnswer(
            inv -> {
              String cid = inv.getArgument(0);
              return row(1L, cid, "X", true);
            });

    OpenAppSecretResult result = controller.create(dto).data();

    assertThat(result.clientId()).startsWith("app-");
    assertThat(result.clientId().length()).isEqualTo("app-".length() + 24);
  }
}
