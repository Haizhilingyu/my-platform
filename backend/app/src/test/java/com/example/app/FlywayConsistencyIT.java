package com.example.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Flyway 迁移跨库一致性集成测试（IT，由 Failsafe 在 integration-test 阶段执行，不阻塞 mvn test）。
 *
 * <p>对同一个 V1+V2 迁移集，分别在真实 PostgreSQL 16（Testcontainers）与 H2（PostgreSQL 兼容模式） 上执行 Flyway
 * migrate，然后断言每张表的行数完全一致。这锁死了 V2 重写后的核心不变量：
 *
 * <pre>
 *   INSERT ... ON CONFLICT DO NOTHING  ≡  INSERT ... SELECT ... WHERE NOT EXISTS
 * </pre>
 *
 * 在两套库上的播种结果相同。若未来有人改写 V2 破坏该等价性，本测试会立刻失败。
 *
 * <p>无 Docker 环境下自动跳过（{@code disabledWithoutDocker = true}），不会让构建失败。
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayConsistencyIT {

  private static final String H2_URL =
      "jdbc:h2:mem:flywayit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
          + "DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=VALUE";

  private static final String[] TABLES = {
    "sys_unit", "sys_user", "sys_role", "sys_user_role", "sys_menu", "sys_role_menu", "sys_config"
  };

  /** 真值来源（PG）的预期播种行数；任一变化都说明种子数据被改动，需同步更新本断言。 */
  private static final Map<String, Long> EXPECTED = expectedCounts();

  @Container
  @SuppressWarnings("resource")
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("platform")
          .withUsername("test")
          .withPassword("test");

  @Test
  void flywayProducesIdenticalRowCountsOnH2AndPostgres() throws Exception {
    Map<String, Long> pgCounts =
        migrateAndCount(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    Map<String, Long> h2Counts = migrateAndCount(H2_URL, "sa", "");

    // 不变量一：H2 与 PG 行数逐表完全一致（V2 重写的跨库等价性）。
    assertThat(h2Counts).as("H2 与 PostgreSQL 播种行数必须一致").isEqualTo(pgCounts);

    // 不变量二：PG（真值）行数与预期种子一致，防止种子被静默改动。
    assertThat(pgCounts).as("PostgreSQL 播种行数与预期种子不符").isEqualTo(EXPECTED);
  }

  /** 对给定库执行 Flyway V1+V2 迁移，返回各表行数。 */
  private Map<String, Long> migrateAndCount(String url, String user, String password)
      throws Exception {
    Flyway.configure()
        .dataSource(url, user, password)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    Map<String, Long> counts = new LinkedHashMap<>();
    try (Connection conn = DriverManager.getConnection(url, user, password)) {
      for (String table : TABLES) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
            ResultSet rs = ps.executeQuery()) {
          rs.next();
          counts.put(table, rs.getLong(1));
        }
      }
    }
    return counts;
  }

  private static Map<String, Long> expectedCounts() {
    Map<String, Long> map = new LinkedHashMap<>();
    map.put("sys_unit", 1L);
    map.put("sys_user", 1L);
    map.put("sys_role", 1L);
    map.put("sys_user_role", 1L);
    map.put("sys_menu", 23L);
    map.put("sys_role_menu", 23L);
    map.put("sys_config", 7L);
    return map;
  }
}
