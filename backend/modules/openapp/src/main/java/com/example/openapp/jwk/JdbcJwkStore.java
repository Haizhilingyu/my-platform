package com.example.openapp.jwk;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class JdbcJwkStore {

  private static final RowMapper<StoredJwk> MAPPER = new StoredJwkRowMapper();

  private final JdbcTemplate jdbc;

  public JdbcJwkStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<StoredJwk> loadActive() {
    return jdbc.query(
        "SELECT kid, key_type, key_data, status FROM openapp_jwk "
            + "WHERE status = 'active' ORDER BY created_at DESC",
        MAPPER);
  }

  public List<StoredJwk> loadGrace() {
    return jdbc.query(
        "SELECT kid, key_type, key_data, status FROM openapp_jwk "
            + "WHERE status = 'grace' ORDER BY created_at DESC",
        MAPPER);
  }

  public List<StoredJwk> loadActiveAndGrace() {
    List<StoredJwk> all = new ArrayList<>(loadActive());
    all.addAll(loadGrace());
    return all;
  }

  public boolean hasActive() {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM openapp_jwk WHERE status = 'active'", Integer.class);
    return count != null && count > 0;
  }

  /** 将当前 active 密钥置为 grace（rotated_at 记录退役时间），并写入新的 active 密钥。 */
  public void activateNewKey(String kid, String keyType, String encryptedData) {
    jdbc.update(
        "UPDATE openapp_jwk SET status = 'grace', rotated_at = NOW() " + "WHERE status = 'active'");
    jdbc.update(
        "INSERT INTO openapp_jwk (kid, key_type, key_data, status) VALUES (?, ?, ?, 'active')",
        kid,
        keyType,
        encryptedData);
  }

  /** 首次启动时无 active 密钥的种子插入。 */
  public void seedFirstKey(String kid, String keyType, String encryptedData) {
    jdbc.update(
        "INSERT INTO openapp_jwk (kid, key_type, key_data, status) VALUES (?, ?, ?, 'active')",
        kid,
        keyType,
        encryptedData);
  }

  public int expireGraceBefore(LocalDateTime cutoff) {
    return jdbc.update(
        "UPDATE openapp_jwk SET status = 'expired' " + "WHERE status = 'grace' AND rotated_at < ?",
        cutoff);
  }

  private static final class StoredJwkRowMapper implements RowMapper<StoredJwk> {
    @Override
    public StoredJwk mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new StoredJwk(
          rs.getString("kid"),
          rs.getString("key_type"),
          rs.getString("key_data"),
          rs.getString("status"));
    }
  }
}
