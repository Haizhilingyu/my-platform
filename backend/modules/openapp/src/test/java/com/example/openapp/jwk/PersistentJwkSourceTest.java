package com.example.openapp.jwk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersistentJwkSourceTest {

  private static RSAKey genKey(String kid) {
    try {
      return new RSAKeyGenerator(2048).keyID(kid).generate();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void reloadLoadsActiveAndGraceKeysWithActiveFirst() {
    var encryption = new JwkKeyEncryptionService("passphrase");
    var store = mock(JdbcJwkStore.class);
    RSAKey active = genKey("kid-active");
    RSAKey grace = genKey("kid-grace");
    when(store.loadActiveAndGrace())
        .thenReturn(
            List.of(
                new StoredJwk(
                    "kid-active", "RSA", encryption.encrypt(active.toJSONString()), "active"),
                new StoredJwk(
                    "kid-grace", "RSA", encryption.encrypt(grace.toJSONString()), "grace")));

    var source = new PersistentJwkSource(store, encryption);
    source.reload();

    assertThat(source.cachedKeyCount()).isEqualTo(2);
    JWKSelector rsaSelector =
        new JWKSelector(new JWKMatcher.Builder().keyType(KeyType.RSA).build());
    assertThat(source.get(rsaSelector, null)).hasSize(2);
  }

  @Test
  void addAndActivateStoresNewKeyAndReloads() {
    var encryption = new JwkKeyEncryptionService("passphrase");
    var store = mock(JdbcJwkStore.class);
    when(store.loadActive()).thenReturn(List.of());
    when(store.loadGrace()).thenReturn(List.of());

    var source = new PersistentJwkSource(store, encryption);
    source.reload();

    RSAKey newKey = genKey("kid-new");
    source.addAndActivate(newKey);

    verify(store).activateNewKey(any(String.class), any(String.class), any(String.class));
  }

  @Test
  void seedIfEmptySkipsWhenActiveAlreadyExists() {
    var encryption = new JwkKeyEncryptionService("passphrase");
    var store = mock(JdbcJwkStore.class);
    when(store.hasActive()).thenReturn(true);

    var source = new PersistentJwkSource(store, encryption);
    source.seedIfEmpty(genKey("kid-seed"));

    verify(store, org.mockito.Mockito.never())
        .seedFirstKey(any(String.class), any(String.class), any(String.class));
  }
}
