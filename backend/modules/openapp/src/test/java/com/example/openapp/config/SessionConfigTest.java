package com.example.openapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

class SessionConfigTest {

  @Test
  void sessionConfigIsAnnotatedWithEnableRedisHttpSession() {
    assertThat(SessionConfig.class.getAnnotation(EnableRedisHttpSession.class)).isNotNull();
  }

  @Test
  void configureRedisActionIsNoOp() {
    SessionConfig config = new SessionConfig();
    ConfigureRedisAction action = config.configureRedisAction();

    assertThat(action).isSameAs(ConfigureRedisAction.NO_OP);
  }
}
