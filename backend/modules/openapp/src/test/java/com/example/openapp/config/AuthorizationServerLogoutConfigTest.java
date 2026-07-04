package com.example.openapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

class AuthorizationServerLogoutConfigTest {

  @Test
  void oidcLogoutEndpointIsConfiguredToOAuth2Logout() {
    OpenAppProperties properties = new OpenAppProperties();
    AuthorizationServerConfig config = new AuthorizationServerConfig();
    AuthorizationServerSettings settings = config.authorizationServerSettings(properties);

    assertThat(settings.getOidcLogoutEndpoint()).isEqualTo("/oauth2/logout");
  }

  @Test
  void issuerIsPreservedFromProperties() {
    OpenAppProperties properties = new OpenAppProperties();
    properties.setIssuer("https://as.example.com");
    AuthorizationServerConfig config = new AuthorizationServerConfig();
    AuthorizationServerSettings settings = config.authorizationServerSettings(properties);

    assertThat(settings.getIssuer()).isEqualTo("https://as.example.com");
  }
}
