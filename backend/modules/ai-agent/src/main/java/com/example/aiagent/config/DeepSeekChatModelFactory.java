package com.example.aiagent.config;

import com.example.aiagent.config.AgentProperties.DeepSeek;
import com.example.sys.SysApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DeepSeek {@link OpenAiChatModel} 工厂：连接参数（api-key / base-url / model）全部从 sys_config
 * （界面配置）读取，懒构建并缓存模型实例。
 *
 * <p><b>替代原先由 Spring AI 自动装配、依赖服务器环境变量 {@code SPRING_AI_OPENAI_API_KEY} 的方案</b>： 管理员在『系统设置 &gt;
 * 配置』修改 {@code ai.deepseek.*} 后，下一次调用自动按新配置重建模型 —— 支持「界面改 Key 即生效」。
 *
 * <p>{@code OpenAiChatAutoConfiguration} 已在 {@code application.yml} 中排除，ChatModel 的构造由本类独占。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "deepseek")
public class DeepSeekChatModelFactory {

  /** sys_config 配置键。 */
  public static final String KEY_API_KEY = "ai.deepseek.api-key";

  public static final String KEY_BASE_URL = "ai.deepseek.base-url";
  public static final String KEY_MODEL = "ai.deepseek.model";

  private final SysApi sysApi;
  private final DeepSeek defaults;

  // 双重检查锁定的缓存字段
  private volatile OpenAiChatModel current;
  private volatile String cacheKey;

  public DeepSeekChatModelFactory(SysApi sysApi, AgentProperties properties) {
    this.sysApi = sysApi;
    this.defaults = properties.getDeepseek();
  }

  /**
   * 返回当前配置对应的 {@link OpenAiChatModel}；若 api-key/base-url/model 在配置中被修改，则下次调用自动重建。
   *
   * @throws IllegalStateException 未配置 api-key 时抛出友好提示
   */
  public OpenAiChatModel model() {
    String apiKey = resolve(KEY_API_KEY, defaults.getApiKey());
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "AI 助手未配置 DeepSeek API Key，请在『系统设置 > 配置』中设置 ai.deepseek.api-key");
    }
    String baseUrl = resolve(KEY_BASE_URL, defaults.getBaseUrl());
    String model = resolve(KEY_MODEL, defaults.getModel());
    String key = apiKey + "|" + baseUrl + "|" + model;
    OpenAiChatModel local = current;
    if (local != null && key.equals(cacheKey)) {
      return local;
    }
    synchronized (this) {
      if (key.equals(cacheKey) && current != null) {
        return current;
      }
      OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
      current =
          OpenAiChatModel.builder()
              .openAiApi(api)
              .defaultOptions(OpenAiChatOptions.builder().model(model).build())
              .build();
      cacheKey = key;
      return current;
    }
  }

  private String resolve(String configKey, String fallback) {
    String value = sysApi.getConfig(configKey, fallback);
    return (value == null || value.isBlank()) ? fallback : value;
  }
}
