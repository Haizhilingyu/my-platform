package com.example.aiagent.agent.tool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 装配 Agent 工具 Bean（供 {@link ToolRegistry} 收集）。 */
@Configuration
public class ToolConfiguration {

  @Bean
  public AgentTool createUserTool(UserToolExecutor exec) {
    return new AgentTool(
        "createUser",
        "创建系统用户。参数：username(必填,字母数字下划线3-32)、password(必填,6-32)；" + "可选 realName/email/phone/unitId。",
        "sys:user:add",
        exec::createUser);
  }

  @Bean
  public AgentTool deleteUserTool(UserToolExecutor exec) {
    return new AgentTool(
        "deleteUser", "删除系统用户。参数：id(必填,用户ID)。", "sys:user:delete", exec::deleteUser);
  }

  @Bean
  public AgentTool navigateToTool() {
    return new AgentTool(
        "navigateTo",
        "跳转到指定页面，向用户展示操作结果。参数：path(前端路由,如 /sys/user)、highlightId(可选,高亮资源ID)。",
        null,
        args -> {
          Object pathVal = args.get("path");
          String path = pathVal == null ? null : pathVal.toString();
          Long highlightId = null;
          Object hl = args.get("highlightId");
          if (hl instanceof Number n) {
            highlightId = n.longValue();
          } else if (hl != null) {
            highlightId = Long.valueOf(hl.toString());
          }
          return new ToolResult(true, "已跳转 " + path, highlightId, path);
        });
  }
}
