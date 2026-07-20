package com.example.aiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI Copilot 配置。 */
@Data
@ConfigurationProperties(prefix = "app.ai")
public class AgentProperties {

  /** 是否启用 AI 助手。 */
  private boolean enabled = true;

  /** Agent brain 实现：mock（默认，关键词意图匹配）/ 后续 openai 等。 */
  private String provider = "mock";
}
