# AI Agent 模块 (ai-agent)

## 功能概述
应用内、**绑定当前登录用户**的对话式 AI Copilot（非机器身份）。把「建/删用户、建/删角色」等管理操作封装为 LLM 工具，
由 Agent 在当前用户权限范围内调用，简化用户操作；操作结果可通过结构化事件驱动前端跳转/高亮展示。

## 核心约束（设计原则）
- **用户绑定**：聊天请求走内部 `/api` JWT 链，`CurrentUser` 天然就是当前用户，无需额外绑定。
- **权限挂钩（双层）**：
  1. 工具可见性——`ToolRegistry.toolsForCurrentUser()` 按用户权限筛选注册到 Agent 的工具集；
  2. 执行校验——每个工具执行器内部 `CurrentUser.hasPermission(...)` 兜底，缺权限抛 `ForbiddenException`。
  即 **AI 能力 ⊆ 当前用户权限**。
- **审计归属**：工具执行器挂 `@Auditable(action="AI_USER_*")`，actor=CurrentUser → 操作记到当前用户名下。
- **复用业务逻辑**：工具通过 `SysApi` 调用 sys 模块（建/删用户），不重写校验/加密/种子等逻辑，守住 Spring Modulith 边界。
- **LLM 可替换**：`AgentBrain` 接口隔离 LLM。当前 `MockAgentBrain`（关键词意图匹配，无需真实 provider）；
  `app.ai.provider=mock` 时生效（`matchIfMissing=true`）。后续接真实 provider（Spring AI）时新增实现 + `@ConditionalOnProperty`。

## 依赖
- platform-common（Result / CurrentUser / ForbiddenException / @Auditable / Messages）
- sys-module（`SysApi` 建删用户 + `UserCreateDTO`）
- spring-boot-starter-web（`@RestController` + `SseEmitter` 流式）

## Maven 依赖
```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>ai-agent-module</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## REST API
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/ai/chat | 对话，`text/event-stream`。请求体 `{message}`；事件类型：`token`/`tool`/`result`/`action`/`done`/`error`。任何已登录用户可用，工具按其权限受限。 |

## 配置
```yaml
app:
  ai:
    enabled: true
    provider: mock   # mock（默认）/ 后续 openai 等
```

## 工具清单（v1）
| 工具 | 所需权限 | 说明 |
|---|---|---|
| createUser | sys:user:add | 创建用户（username/password 必填，其余可选）|
| deleteUser | sys:user:delete | 删除用户（id）|
| navigateTo | （无） | 跳转前端页面 + 高亮资源，返回 `action` 事件 |

## 事件协议（SSE）
```
event: tool
data: {"name":"createUser","args":{"username":"bob"}}

event: result
data: "已创建用户 bob（id=42）"

event: action
data: {"path":"/sys/user","highlightId":42}

event: done
```

## 后续阶段（未实现）
- 接入真实 LLM（Spring AI，需升 Spring Boot 到最新稳定版 + UI 配置 provider/key）。
- 删除等高危操作二次确认、限流、软删策略。
- 前端聊天面板（Layout 抽屉）+ action 事件渲染跳转。
