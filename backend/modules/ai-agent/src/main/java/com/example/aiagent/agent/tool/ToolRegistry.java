package com.example.aiagent.agent.tool;

import com.example.common.ai.AgentTool;
import com.example.common.ai.AiToolProvider;
import com.example.common.security.CurrentUser;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 工具注册表。聚合所有 {@link AiToolProvider} bean 的工具，并按当前用户权限筛选可见工具—— <b>这是「AI 能力 ⊆
 * 当前用户权限」的第一层保障</b>：用户没有对应权限，工具不会注册给 Agent。
 *
 * <p>每个业务模块实现 {@link AiToolProvider} 返回自己的工具集，Spring 自动发现并注入此处。新增模块 = 新增一个 provider
 * bean，ToolRegistry 零改动。
 */
@Component
public class ToolRegistry {

  private final List<AgentTool> allTools;

  public ToolRegistry(List<AiToolProvider> providers) {
    this.allTools =
        providers.stream()
            .flatMap(p -> p.getTools().stream())
            .sorted(Comparator.comparing(AgentTool::name))
            .toList();
  }

  /** 按当前登录用户权限筛选可用工具（admin 通配 {@code *} 拥有全部）。 */
  public List<AgentTool> toolsForCurrentUser() {
    Set<String> perms = CurrentUser.getPermissions();
    boolean admin = perms.contains("*");
    return allTools.stream()
        .filter(
            t -> t.requiredPermission() == null || admin || perms.contains(t.requiredPermission()))
        .sorted(Comparator.comparing(AgentTool::name))
        .toList();
  }

  /** 按名查找（仅在用户可见集合内查找，见 {@link com.example.aiagent.agent.AgentService}）。 */
  public Optional<AgentTool> find(String name) {
    return allTools.stream().filter(t -> t.name().equals(name)).findFirst();
  }
}
