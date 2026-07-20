package com.example.aiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI Copilot 配置。 */
@Data
@ConfigurationProperties(prefix = "app.ai")
public class AgentProperties {

  /** 是否启用 AI 助手。 */
  private boolean enabled = true;

  /** Agent brain 实现：mock（默认，关键词意图匹配）/ deepseek（真实 LLM）/ 后续 openai 等。 */
  private String provider = "mock";

  /** DeepSeek（真实 LLM）配置，provider=deepseek 时使用。key 通过环境变量注入，不进仓库。 */
  private Deepseek deepseek = new Deepseek();

  /** DeepSeek 子配置（OpenAI 兼容）。 */
  @Data
  public static class Deepseek {
    private String apiKey = "";
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-chat";
  }
}
