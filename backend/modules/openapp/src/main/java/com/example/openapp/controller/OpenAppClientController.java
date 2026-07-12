package com.example.openapp.controller;

import com.example.common.exception.NotFoundException;
import com.example.common.i18n.Messages;
import com.example.common.result.PageResult;
import com.example.common.result.Result;
import com.example.common.security.RequiresPermission;
import com.example.openapp.client.JdbcRegisteredClientRepository;
import com.example.openapp.client.JdbcRegisteredClientRepository.OpenAppClientRow;
import com.example.openapp.client.dto.OpenAppClientCreateDTO;
import com.example.openapp.client.dto.OpenAppClientUpdateDTO;
import com.example.openapp.client.dto.OpenAppClientVO;
import com.example.openapp.client.dto.OpenAppSecretResult;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部应用客户端管理 CRUD API。供管理后台（T21 前端）调用，路径 {@code /api/sys/openapp/clients}。
 *
 * <p>权限：{@code sys:openapp:list/add/edit/delete}（V32 migration 已绑定 admin）。
 *
 * <p>密钥策略：BCrypt 哈希存库，明文仅在 create / reset-secret 响应中返回一次。
 */
@RestController
@RequestMapping("/api/sys/openapp/clients")
@RequiredArgsConstructor
public class OpenAppClientController {

  private static final int CLIENT_ID_LEN = 24;
  private static final int SECRET_LEN = 40;
  private static final String ID_PREFIX = "app-";
  private static final String ID_ALPHABET =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final String SECRET_ALPHABET =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~";

  private final JdbcRegisteredClientRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final SecureRandom random = new SecureRandom();

  @RequiresPermission("sys:openapp:list")
  @GetMapping
  public Result<PageResult<OpenAppClientVO>> list(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "10") int pageSize) {
    if (pageNum < 1) {
      pageNum = 1;
    }
    if (pageSize < 1 || pageSize > 200) {
      pageSize = 10;
    }
    int offset = (pageNum - 1) * pageSize;
    List<OpenAppClientRow> rows = repository.listRows(offset, pageSize, keyword, enabled);
    long total = repository.countRows(keyword, enabled);
    List<OpenAppClientVO> vos = rows.stream().map(OpenAppClientController::toVO).toList();
    return Result.ok(PageResult.of(vos, total, pageNum, pageSize));
  }

  @RequiresPermission("sys:openapp:list")
  @GetMapping("/{id}")
  public Result<OpenAppClientVO> get(@PathVariable Long id) {
    OpenAppClientRow row = repository.findRowById(id);
    if (row == null) {
      throw new NotFoundException(
          Messages.get("error.resource.not.found", Messages.get("resource.app"), id));
    }
    return Result.ok(toVO(row));
  }

  @RequiresPermission("sys:openapp:add")
  @PostMapping
  public Result<OpenAppSecretResult> create(@RequestBody @Valid OpenAppClientCreateDTO dto) {
    String clientId = ID_PREFIX + randomString(CLIENT_ID_LEN, ID_ALPHABET);
    String rawSecret = randomString(SECRET_LEN, SECRET_ALPHABET);
    String hash = passwordEncoder.encode(rawSecret);

    repository.insert(
        buildRegisteredClient(
            clientId,
            hash,
            dto.redirectUris(),
            dto.postLogoutRedirectUris(),
            dto.scopes(),
            dto.grantTypes()),
        dto.clientName());

    OpenAppClientRow saved = repository.findRowByClientId(clientId);
    return Result.ok(new OpenAppSecretResult(saved.id(), saved.clientId(), rawSecret));
  }

  @RequiresPermission("sys:openapp:edit")
  @PutMapping("/{id}")
  public Result<Void> update(
      @PathVariable Long id, @RequestBody @Valid OpenAppClientUpdateDTO dto) {
    OpenAppClientRow row = repository.findRowById(id);
    if (row == null) {
      throw new NotFoundException(
          Messages.get("error.resource.not.found", Messages.get("resource.app"), id));
    }

    Set<String> redirectUris =
        dto.redirectUris() == null ? Set.of() : new HashSet<>(dto.redirectUris());
    Set<String> postLogout =
        dto.postLogoutRedirectUris() == null
            ? Set.of()
            : new HashSet<>(dto.postLogoutRedirectUris());
    Set<String> scopes = dto.scopes() == null ? Set.of() : new HashSet<>(dto.scopes());
    Set<AuthorizationGrantType> grantTypes = parseGrantTypes(dto.grantTypes());
    Set<ClientAuthenticationMethod> authMethods = resolveAuthMethods(grantTypes);

    repository.updateManagementFields(
        id,
        dto.clientName(),
        redirectUris,
        postLogout,
        scopes,
        grantTypes,
        authMethods,
        Boolean.TRUE.equals(dto.enabled()));
    return Result.ok();
  }

  @RequiresPermission("sys:openapp:delete")
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    int affected = repository.deleteRowById(id);
    if (affected == 0) {
      throw new NotFoundException(
          Messages.get("error.resource.not.found", Messages.get("resource.app"), id));
    }
    return Result.ok();
  }

  @RequiresPermission("sys:openapp:edit")
  @PostMapping("/{id}/reset-secret")
  public Result<OpenAppSecretResult> resetSecret(@PathVariable Long id) {
    OpenAppClientRow row = repository.findRowById(id);
    if (row == null) {
      throw new NotFoundException(
          Messages.get("error.resource.not.found", Messages.get("resource.app"), id));
    }
    String rawSecret = randomString(SECRET_LEN, SECRET_ALPHABET);
    repository.updateSecret(row.clientId(), passwordEncoder.encode(rawSecret));
    return Result.ok(new OpenAppSecretResult(id, row.clientId(), rawSecret));
  }

  private org.springframework.security.oauth2.server.authorization.client.RegisteredClient
      buildRegisteredClient(
          String clientId,
          String secretHash,
          List<String> redirectUris,
          List<String> postLogoutRedirectUris,
          List<String> scopes,
          List<String> grantTypes) {
    var builder =
        org.springframework.security.oauth2.server.authorization.client.RegisteredClient.withId(
                clientId)
            .clientId(clientId)
            .clientSecret(secretHash);
    if (redirectUris != null) {
      redirectUris.forEach(builder::redirectUri);
    }
    if (postLogoutRedirectUris != null) {
      postLogoutRedirectUris.forEach(builder::postLogoutRedirectUri);
    }
    if (scopes != null) {
      scopes.forEach(builder::scope);
    }
    Set<AuthorizationGrantType> grants = parseGrantTypes(grantTypes);
    grants.forEach(builder::authorizationGrantType);
    resolveAuthMethods(grants).forEach(builder::clientAuthenticationMethod);
    return builder.build();
  }

  private static Set<AuthorizationGrantType> parseGrantTypes(List<String> grantTypes) {
    Set<AuthorizationGrantType> set = new HashSet<>();
    if (grantTypes == null) {
      return set;
    }
    for (String g : grantTypes) {
      if (g == null || g.isBlank()) {
        continue;
      }
      set.add(new AuthorizationGrantType(g.trim()));
    }
    return set;
  }

  /**
   * 推断认证方式：包含 client_credentials / authorization_code / password → client_secret_basic； 仅 implicit
   * → none。
   */
  private static Set<ClientAuthenticationMethod> resolveAuthMethods(
      Set<AuthorizationGrantType> grantTypes) {
    Set<ClientAuthenticationMethod> methods = new HashSet<>();
    boolean confidential =
        grantTypes.stream()
            .anyMatch(
                g ->
                    AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(g.getValue())
                        || AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(g.getValue())
                        || "password".equals(g.getValue())
                        || "urn:ietf:params:oauth:grant-type:device_code".equals(g.getValue()));
    if (confidential || grantTypes.isEmpty()) {
      methods.add(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
    }
    if (grantTypes.stream().anyMatch(g -> "implicit".equals(g.getValue()))) {
      methods.add(ClientAuthenticationMethod.NONE);
    }
    if (methods.isEmpty()) {
      methods.add(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
    }
    return methods;
  }

  private String randomString(int length, String alphabet) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
    }
    return sb.toString();
  }

  private static OpenAppClientVO toVO(OpenAppClientRow row) {
    return new OpenAppClientVO(
        row.id(),
        row.clientId(),
        row.clientName(),
        sortedList(row.redirectUris()),
        sortedList(row.postLogoutRedirectUris()),
        sortedList(row.scopes()),
        row.grantTypes().stream().sorted().toList(),
        row.enabled(),
        row.createdAt());
  }

  private static List<String> sortedList(java.util.Set<String> set) {
    return set == null ? List.of() : set.stream().sorted().toList();
  }
}
