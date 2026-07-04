package com.example.openapp.jwk;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.text.ParseException;
import java.util.List;

/**
 * 持久化 JWK 源：密钥从 {@code openapp_jwk} 表加载（非内存生成）。
 *
 * <p>多副本从同一数据库加载相同密钥，避免 HA 下各副本内存密钥不一致导致 token 校验失败。 active 密钥排在前（用于签名），grace 密钥排在后（仅用于校验宽限期内签发的旧
 * token）。
 */
public class PersistentJwkSource implements JWKSource<SecurityContext> {

  private final JdbcJwkStore store;
  private final JwkKeyEncryptionService encryption;
  private volatile JWKSet cached = new JWKSet();

  public PersistentJwkSource(JdbcJwkStore store, JwkKeyEncryptionService encryption) {
    this.store = store;
    this.encryption = encryption;
  }

  /** 从数据库重新加载 active + grace 密钥到内存缓存。 */
  public synchronized void reload() {
    List<JWK> keys = store.loadActiveAndGrace().stream().<JWK>map(this::toRsaKey).toList();
    cached = new JWKSet(keys);
  }

  /** 生成并激活新密钥：当前 active 降级为 grace，新密钥成为 active，缓存刷新。 */
  public synchronized void addAndActivate(RSAKey newKey) {
    String encrypted = encryption.encrypt(newKey.toJSONString());
    store.activateNewKey(newKey.getKeyID(), "RSA", encrypted);
    reload();
  }

  /** 首次启动无密钥时插入种子 active 密钥。 */
  public synchronized void seedIfEmpty(RSAKey seedKey) {
    if (!store.hasActive()) {
      String encrypted = encryption.encrypt(seedKey.toJSONString());
      store.seedFirstKey(seedKey.getKeyID(), "RSA", encrypted);
      reload();
    }
  }

  public synchronized int cachedKeyCount() {
    return cached.getKeys().size();
  }

  @Override
  public List<JWK> get(JWKSelector jwkSelector, SecurityContext context) {
    return jwkSelector.select(cached);
  }

  private RSAKey toRsaKey(StoredJwk row) {
    try {
      String json = encryption.decrypt(row.encryptedData());
      return RSAKey.parse(json);
    } catch (ParseException e) {
      throw new IllegalStateException("Failed to parse stored JWK for kid=" + row.kid(), e);
    }
  }
}
