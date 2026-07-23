package com.example.common.ai;

import java.util.List;

/**
 * AI 工具提供者 SPI：每个业务模块实现此接口，返回该模块的全部工具定义。
 *
 * <p>实现类标注 {@code @Component} 后由 Spring 自动发现，ToolRegistry 聚合所有 provider 的工具。 新增模块只需新增一个
 * provider，ai-agent 零改动。
 */
public interface AiToolProvider {
  List<AgentTool> getTools();
}
