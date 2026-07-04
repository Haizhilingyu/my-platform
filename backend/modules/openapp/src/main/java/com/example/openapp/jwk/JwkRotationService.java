package com.example.openapp.jwk;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JwkRotationService implements ApplicationRunner {

  private final PersistentJwkSource jwkSource;
  private final JdbcJwkStore store;
  private final int graceDays;

  public JwkRotationService(PersistentJwkSource jwkSource, JdbcJwkStore store, int graceDays) {
    this.jwkSource = jwkSource;
    this.store = store;
    this.graceDays = graceDays;
  }

  @Override
  public void run(ApplicationArguments args) {
    jwkSource.reload();
    if (!store.hasActive()) {
      jwkSource.seedIfEmpty(generateKey());
    }
  }

  /** 每周一 03:00 轮转：生成新 active，旧 active 降级为 grace。 */
  @Scheduled(cron = "0 0 3 ? * MON")
  public void scheduledRotate() {
    jwkSource.addAndActivate(generateKey());
  }

  /** 每日 03:30 清理：超过宽限期的 grace 密钥标记为 expired。 */
  @Scheduled(cron = "0 30 3 * * *")
  public void scheduledExpire() {
    store.expireGraceBefore(LocalDateTime.now().minusDays(graceDays));
    jwkSource.reload();
  }

  public void rotateNow() {
    jwkSource.addAndActivate(generateKey());
  }

  public int expireGraceNow() {
    int n = store.expireGraceBefore(LocalDateTime.now().minusDays(graceDays));
    jwkSource.reload();
    return n;
  }

  static RSAKey generateKey() {
    try {
      return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
    } catch (com.nimbusds.jose.JOSEException e) {
      throw new IllegalStateException("Failed to generate RSA key pair", e);
    }
  }
}
