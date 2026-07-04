package com.example.openapp.jwk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwkKeyEncryptionServiceTest {

  @Test
  void encryptThenDecryptReturnsOriginal() {
    var service = new JwkKeyEncryptionService("my-secret-passphrase");
    String plaintext = "{\"kty\":\"RSA\",\"kid\":\"abc\",\"d\":\"private-key-data\"}";
    String encrypted = service.encrypt(plaintext);

    assertThat(encrypted).isNotEqualTo(plaintext);
    assertThat(service.decrypt(encrypted)).isEqualTo(plaintext);
  }

  @Test
  void ciphertextDiffersForSamePlaintextDueToRandomIv() {
    var service = new JwkKeyEncryptionService("passphrase");
    String plaintext = "sensitive-key-data";

    String first = service.encrypt(plaintext);
    String second = service.encrypt(plaintext);

    assertThat(first).isNotEqualTo(second);
    assertThat(service.decrypt(first)).isEqualTo(plaintext);
    assertThat(service.decrypt(second)).isEqualTo(plaintext);
  }

  @Test
  void decryptWithWrongKeyFails() {
    var encryptor = new JwkKeyEncryptionService("correct-key");
    var decryptor = new JwkKeyEncryptionService("different-key");
    String encrypted = encryptor.encrypt("data");

    assertThatThrownBy(() -> decryptor.decrypt(encrypted))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void blankPassphraseRejected() {
    assertThatThrownBy(() -> new JwkKeyEncryptionService(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new JwkKeyEncryptionService("   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void malformedStoredValueRejected() {
    var service = new JwkKeyEncryptionService("passphrase");
    assertThatThrownBy(() -> service.decrypt("not-a-valid-format"))
        .isInstanceOf(IllegalStateException.class);
  }
}
