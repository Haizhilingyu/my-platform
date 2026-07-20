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
  public AgentTool listUsersTool(UserToolExecutor exec) {
    return new AgentTool(
        "listUsers",
        "查询系统用户列表（只读）。参数：keyword(可选,按用户名/姓名模糊匹配)、limit(可选,返回条数,默认20,最大50)。",
        "sys:user:list",
        exec::listUsers);
  }

  @Bean
  public AgentTool assignRolesTool(UserToolExecutor exec) {
    return new AgentTool(
        "assignRoles",
        "给指定用户分配角色（覆盖其原有角色）。参数：userId(必填,用户ID)、roleIds(必填,角色ID数组)。",
        "sys:user:role",
        exec::assignRoles);
  }

  @Bean
  public AgentTool listRolesTool(RoleToolExecutor exec) {
    return new AgentTool(
        "listRoles", "查询系统角色列表（只读），返回角色ID/编码/名称。无参数。", "sys:role:list", exec::listRoles);
  }

  @Bean
  public AgentTool createRoleTool(RoleToolExecutor exec) {
    return new AgentTool(
        "createRole",
        "创建系统角色。参数：roleCode(必填,字母数字下划线3-50)、roleName(必填,角色名)；"
            + "可选 dataScope(数据范围,默认SELF)、remark。",
        "sys:role:add",
        exec::createRole);
  }

  @Bean
  public AgentTool assignRoleMenusTool(RoleToolExecutor exec) {
    return new AgentTool(
        "assignRoleMenus",
        "给指定角色分配菜单权限（覆盖其原有菜单）。参数：roleId(必填,角色ID)、menuIds(必填,菜单ID数组)。",
        "sys:role:perm",
        exec::assignRoleMenus);
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
