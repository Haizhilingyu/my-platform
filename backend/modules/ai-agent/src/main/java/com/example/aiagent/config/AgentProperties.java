package com.example.aiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI Copilot 配置。 */
@Data
@ConfigurationProperties(prefix = "app.ai")
public class AgentProperties {

  /** 是否启用 AI 助手。 */
  private boolean enabled = true;

  /** Agent brain 实现：mock（默认，关键词意图匹配）/ deepseek（真实 LLM，OpenAI 兼容）。 */
  private String provider = "mock";

  /**
   * DeepSeek/OpenAI 兼容连接兜底默认值。实际取值优先来自 sys_config（界面配置），见 {@link
   * DeepSeekChatModelFactory}。这里仅作「配置项缺失」时的回退。
   */
  private final DeepSeek deepseek = new DeepSeek();

  @Data
  public static class DeepSeek {
    /** API Key 兜底默认（空）。生产应通过『系统设置 &gt; 配置』中的 {@code ai.deepseek.api-key} 设置， 不再依赖服务器环境变量。 */
    private String apiKey = "";

    /** 兼容 OpenAI 的 base-url，默认指向 DeepSeek 官方。 */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型名，默认 deepseek-chat。 */
    private String model = "deepseek-chat";
  }

  /** 对话限流（防止 DeepSeek 调用被刷导致成本失控）。 */
  private final RateLimit rateLimit = new RateLimit();

  @Data
  public static class RateLimit {
    /** 每窗口内每用户最大请求数；{@code <=0} 表示关闭限流。 */
    private int maxRequests = 30;

    /** 时间窗口大小（秒）。 */
    private int windowSeconds = 60;
  }
}
