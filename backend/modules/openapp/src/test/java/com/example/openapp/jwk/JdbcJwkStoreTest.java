package com.example.openapp.jwk;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class JdbcJwkStoreTest {

  private JdbcTemplate jdbc;
  private JdbcJwkStore store;

  @BeforeEach
  void setUp() throws Exception {
    DataSource ds =
        new SingleConnectionDataSource(
            "jdbc:h2:mem:jwks;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE",
            "sa",
            "",
            true);
    jdbc = new JdbcTemplate(ds);
    // 保证每个测试方法拥有干净的数据库状态：H2 内存库在 DB_CLOSE_DELAY=-1 下会跨测试方法复用，
    // 而 V30 迁移脚本使用 CREATE TABLE IF NOT EXISTS，不会清理已有数据。
    jdbc.execute("DROP ALL OBJECTS");
    try (Connection con = ds.getConnection()) {
      ScriptUtils.executeSqlScript(
          con,
          new org.springframework.core.io.ClassPathResource("db/migration/V30__openapp_init.sql"));
    }
    store = new JdbcJwkStore(jdbc);
  }

  @Test
  void seedAndLoadActive() {
    assertThat(store.hasActive()).isFalse();
    store.seedFirstKey("kid-1", "RSA", "encrypted-1");

    assertThat(store.hasActive()).isTrue();
    assertThat(store.loadActive()).hasSize(1);
    assertThat(store.loadActive().get(0).kid()).isEqualTo("kid-1");
    assertThat(store.loadGrace()).isEmpty();
  }

  @Test
  void activateNewKeyDemotesPreviousToGrace() {
    store.seedFirstKey("kid-old", "RSA", "encrypted-old");
    store.activateNewKey("kid-new", "RSA", "encrypted-new");

    assertThat(store.loadActive()).hasSize(1);
    assertThat(store.loadActive().get(0).kid()).isEqualTo("kid-new");
    assertThat(store.loadGrace()).hasSize(1);
    assertThat(store.loadGrace().get(0).kid()).isEqualTo("kid-old");
    assertThat(store.loadActiveAndGrace()).hasSize(2);
  }

  @Test
  void expireGraceBeforeMarksOnlyOldGraceKeys() {
    store.seedFirstKey("kid-old", "RSA", "encrypted-old");
    store.activateNewKey("kid-new", "RSA", "encrypted-new");

    // kid-old is now grace with rotated_at = now(). Cutoff in the past -> nothing expired.
    int expired = store.expireGraceBefore(LocalDateTime.now().minusDays(1));
    assertThat(expired).isZero();

    // Cutoff in the future -> kid-old (grace, rotated_at=now) is before cutoff -> expired.
    expired = store.expireGraceBefore(LocalDateTime.now().plusDays(1));
    assertThat(expired).isEqualTo(1);
    assertThat(store.loadGrace()).isEmpty();
  }
}
