package com.example.openapp.jwk;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM 对 JWK 私钥 JSON 做对称加解密。
 *
 * <p>密钥由 passphrase 经 SHA-256 派生（256-bit AES）。密文格式：base64(iv) + ":" + base64(ciphertext)。 持久化 JWK
 * 的私钥必须加密后入库，避免数据库泄露即泄露签名私钥。
 */
public class JwkKeyEncryptionService {

  private static final int GCM_TAG_BITS = 128;
  private static final int IV_BYTES = 12;

  private final SecretKey secretKey;
  private final SecureRandom random = new SecureRandom();

  public JwkKeyEncryptionService(String passphrase) {
    if (passphrase == null || passphrase.isBlank()) {
      throw new IllegalArgumentException("JWK encryption passphrase must not be blank");
    }
    try {
      byte[] keyBytes =
          MessageDigest.getInstance("SHA-256").digest(passphrase.getBytes(StandardCharsets.UTF_8));
      this.secretKey = new SecretKeySpec(keyBytes, "AES");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(iv)
          + ":"
          + Base64.getEncoder().encodeToString(cipherText);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("JWK encryption failed", e);
    }
  }

  public String decrypt(String stored) {
    try {
      int sep = stored.indexOf(':');
      if (sep < 0) {
        throw new IllegalStateException("Invalid encrypted JWK format: missing iv separator");
      }
      byte[] iv = Base64.getDecoder().decode(stored.substring(0, sep));
      byte[] cipherText = Base64.getDecoder().decode(stored.substring(sep + 1));
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
      return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("JWK decryption failed", e);
    }
  }
}
