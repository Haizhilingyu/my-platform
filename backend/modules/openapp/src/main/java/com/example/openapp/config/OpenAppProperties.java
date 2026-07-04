package com.example.openapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openapp")
public class OpenAppProperties {

  private String issuer = "http://localhost:8090";

  private String jwkEncryptionKey = "my-platform-default-jwk-encryption-key-32b";

  private int jwkGraceDays = 30;

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public String getJwkEncryptionKey() {
    return jwkEncryptionKey;
  }

  public void setJwkEncryptionKey(String jwkEncryptionKey) {
    this.jwkEncryptionKey = jwkEncryptionKey;
  }

  public int getJwkGraceDays() {
    return jwkGraceDays;
  }

  public void setJwkGraceDays(int jwkGraceDays) {
    this.jwkGraceDays = jwkGraceDays;
  }
}
