package com.example.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Wave 0 基线冒烟测试：以 test profile（H2 内存库 + PostgreSQL 兼容模式）启动完整应用上下文。
 *
 * <p>该测试通过上下文初始化隐式验证三件事：
 *
 * <ol>
 *   <li>Flyway 能在 H2 上成功执行 V1+V2 迁移（日志可见 "Successfully applied 2 migrations"）
 *   <li>Hibernate validate 校验实体与 Flyway 建出的表结构一致
 *   <li>不产生任何远程 PG（<NAS_IP>:5532）或 Redis 连接尝试
 * </ol>
 *
 * 上下文成功加载即代表基线通过。
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextLoadsTest {

  @Autowired ApplicationContext applicationContext;

  @Test
  void contextLoads() {
    assertThat(applicationContext).isNotNull();
  }
}
