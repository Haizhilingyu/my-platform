package com.example.openapp.jwk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class JwkRotationServiceTest {

  @Test
  void runSeedsInitialKeyWhenNoneActive() {
    var source = mock(PersistentJwkSource.class);
    var store = mock(JdbcJwkStore.class);
    when(store.hasActive()).thenReturn(false);

    var service = new JwkRotationService(source, store, 30);
    service.run(mock(ApplicationArguments.class));

    verify(source).reload();
    verify(source).seedIfEmpty(any(RSAKey.class));
  }

  @Test
  void runSkipsSeedingWhenActiveAlreadyExists() {
    var source = mock(PersistentJwkSource.class);
    var store = mock(JdbcJwkStore.class);
    when(store.hasActive()).thenReturn(true);

    var service = new JwkRotationService(source, store, 30);
    service.run(mock(ApplicationArguments.class));

    verify(source, never()).seedIfEmpty(any(RSAKey.class));
  }

  @Test
  void rotateNowGeneratesAndActivatesNewKey() {
    var source = mock(PersistentJwkSource.class);
    var store = mock(JdbcJwkStore.class);

    var service = new JwkRotationService(source, store, 30);
    service.rotateNow();

    verify(source).addAndActivate(any(RSAKey.class));
  }

  @Test
  void expireGraceNowExpiresAndReloads() {
    var source = mock(PersistentJwkSource.class);
    var store = mock(JdbcJwkStore.class);
    when(store.expireGraceBefore(any())).thenReturn(2);

    var service = new JwkRotationService(source, store, 30);
    int expired = service.expireGraceNow();

    assertThat(expired).isEqualTo(2);
    verify(store).expireGraceBefore(any());
    verify(source).reload();
  }

  @Test
  void generateKeyProducesRsaKeyWithKid() {
    RSAKey key = JwkRotationService.generateKey();
    assertThat(key).isNotNull();
    assertThat(key.getKeyID()).isNotBlank();
    assertThat(key.getKeyType().getValue()).isEqualTo("RSA");
  }
}
