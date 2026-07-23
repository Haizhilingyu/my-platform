package com.example.openapp.config;

import com.example.openapp.client.JdbcRegisteredClientRepository;
import com.example.openapp.jwk.PersistentJwkSource;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * 授权服务器配置（Filter Chain Order=1）：OAuth2 端点 + OIDC + 持久化 JWK + JwtDecoder。
 *
 * <p>三条链共存：本链(Order=1, AS 端点) → 资源服务器(Order=2, /openapi/**) → platform-security 兜底链(/sys/** 内部
 * JWT)。JWK 必须来自 openapp_jwk 表（持久化）。
 */
@Configuration
public class AuthorizationServerConfig {

  @Bean
  @Order(1)
  public SecurityFilterChain authorizationServerFilterChain(HttpSecurity http) throws Exception {
    OAuth2AuthorizationServerConfigurer authorizationServer =
        new OAuth2AuthorizationServerConfigurer();

    http.securityMatcher(authorizationServer.getEndpointsMatcher())
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .with(authorizationServer, (server) -> server.oidc(Customizer.withDefaults()))
        .exceptionHandling(
            exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

    return http.build();
  }

  @Bean
  public JdbcRegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
    return new JdbcRegisteredClientRepository(jdbcTemplate);
  }

  @Bean
  public OAuth2AuthorizationService authorizationService() {
    return new InMemoryOAuth2AuthorizationService();
  }

  @Bean
  public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
  }

  @Bean
  public AuthorizationServerSettings authorizationServerSettings(OpenAppProperties properties) {
    return AuthorizationServerSettings.builder()
        .issuer(properties.getIssuer())
        .oidcLogoutEndpoint("/oauth2/logout")
        .build();
  }

  @Bean
  public PersistentJwkSource persistentJwkSource(
      JdbcTemplate jdbcTemplate,
      com.example.openapp.jwk.JwkKeyEncryptionService encryption,
      OpenAppProperties properties) {
    var store = new com.example.openapp.jwk.JdbcJwkStore(jdbcTemplate);
    var source = new PersistentJwkSource(store, encryption);
    return source;
  }

  @Bean
  public com.example.openapp.jwk.JwkRotationService jwkRotationService(
      PersistentJwkSource persistentJwkSource,
      JdbcTemplate jdbcTemplate,
      OpenAppProperties properties) {
    var store = new com.example.openapp.jwk.JdbcJwkStore(jdbcTemplate);
    return new com.example.openapp.jwk.JwkRotationService(
        persistentJwkSource, store, properties.getJwkGraceDays());
  }

  @Bean
  public com.example.openapp.jwk.JwkKeyEncryptionService jwkKeyEncryptionService(
      @Value("${JWK_ENCRYPTION_KEY:my-platform-default-jwk-encryption-key-32b}")
          String encryptionKey) {
    return new com.example.openapp.jwk.JwkKeyEncryptionService(encryptionKey);
  }
}
