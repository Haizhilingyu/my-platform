package com.example.openapp.config;

import com.example.openapp.client.JdbcRegisteredClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.stereotype.Component;

/**
 * E2E 测试客户端（{@code test-client}）启动幂等播种。
 *
 * <p>oauth2-e2e.sh 依赖一个 client_id={@code test-client}、明文 secret={@code test-secret} 的客户端做 AS 端点可达性
 * + client_credentials 流程验证。客户端密钥必须用平台 {@link PasswordEncoder}（BCrypt）编码后入库—— SQL 脚本无法在迁移期访问该
 * bean，故改由本 {@link ApplicationRunner} 在上下文就绪后用真实编码器播种。
 *
 * <p>幂等：已存在则跳过（不覆盖管理员可能改过的配置）。
 */
@Component
@Order(30)
public class TestClientSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(TestClientSeeder.class);

  private final ObjectProvider<JdbcRegisteredClientRepository> repositoryProvider;
  private final ObjectProvider<PasswordEncoder> passwordEncoderProvider;

  public TestClientSeeder(
      ObjectProvider<JdbcRegisteredClientRepository> repositoryProvider,
      ObjectProvider<PasswordEncoder> passwordEncoderProvider) {
    this.repositoryProvider = repositoryProvider;
    this.passwordEncoderProvider = passwordEncoderProvider;
  }

  @Override
  public void run(ApplicationArguments args) {
    JdbcRegisteredClientRepository repository = repositoryProvider.getIfAvailable();
    PasswordEncoder passwordEncoder = passwordEncoderProvider.getIfAvailable();
    if (repository == null || passwordEncoder == null) {
      return;
    }
    if (repository.findByClientId("test-client") != null) {
      return;
    }
    RegisteredClient testClient =
        RegisteredClient.withId("test-client")
            .clientId("test-client")
            .clientSecret(passwordEncoder.encode("test-secret"))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .redirectUri("http://127.0.0.1:18080/callback")
            .postLogoutRedirectUri("http://127.0.0.1:18080/logout")
            .scope("openid")
            .scope("profile")
            .scope("openapi.read")
            .clientSettings(ClientSettings.builder().build())
            .build();
    repository.save(testClient);
    log.info("E2E 测试客户端 test-client 已播种（client_credentials / authorization_code）");
  }
}
