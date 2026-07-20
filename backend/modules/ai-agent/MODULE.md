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
- **审计归属**：工具执行器挂 `@Auditable(action="AI_USER_*/AI_ROLE_*")`，actor=CurrentUser → 操作记到当前用户名下。
- **复用业务逻辑**：工具通过 `SysApi` 调用 sys 模块（用户/角色增删改查、分配关系），不重写校验/加密/种子等逻辑，守住 Spring Modulith 边界。
- **LLM 可替换**：`AgentBrain` 接口隔离 LLM，按 `app.ai.provider` 二选一（`@ConditionalOnProperty`）：
  - `mock`（默认，`matchIfMissing=true`）：`MockAgentBrain` 关键词意图匹配，无需真实 provider；
  - `deepseek`：`DeepSeekAgentBrain` 真实 LLM（Spring AI 1.0.8，OpenAiChatModel 指向 DeepSeek），工具 schema 经 `FunctionToolCallback` 暴露、`internalToolExecutionEnabled=false` 仅「建议」工具调用，执行仍由 `AgentService` 完成（保留权限校验/审计/事件流）。
- **破坏性操作二次确认**：`deleteUser`/`assignRoles`/`assignRoleMenus` 等覆盖/删除类工具，先发 `confirm` 事件请用户确认；客户端点「执行」回传 `confirm`（工具名+参数），服务端直接执行该调用（**不再过大脑**，避免重复 LLM 调用与决策漂移）。
- **限流**：`ChatRateLimiter` 每用户固定时间窗口计数（内存实现），超出即在请求线程返回 `error` 事件，避免 DeepSeek 调用被刷导致成本失控。

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
| POST | /api/ai/chat | 对话，`text/event-stream`。请求体 `{message, confirm?}`；`message` 发起对话时必填，`confirm` 为破坏性工具二次确认回执（携带 `{tool, args}`）。事件类型：`token`/`tool`/`result`/`action`/`confirm`/`done`/`error`。任何已登录用户可用，工具按其权限受限，超出限流直接返回 `error`。 |

## 配置
```yaml
app:
  ai:
    enabled: true
    provider: mock          # mock（默认）/ deepseek（真实 LLM）
    deepseek:               # 仅 deepseek 模式生效；连接参数优先取 sys_config，此处为「配置缺失」兜底
      api-key: ""
      base-url: https://api.deepseek.com
      model: deepseek-chat
    rate-limit:
      max-requests: 30      # 每用户每窗口最大对话次数；<=0 关闭限流
      window-seconds: 60
```

DeepSeek 连接参数（api-key/base-url/model）通过 `DeepSeekChatModelFactory` **从 sys_config 读取并懒构建缓存**——
管理员在『系统设置 > 配置』改 `ai.deepseek.*` 后下次调用自动按新配置重建模型（支持「界面改 Key 即生效」）。
`ai.deepseek.api-key` 为 `SECRET` 类型，配置列表中以圆点脱敏。Spring AI 的 `OpenAiChatAutoConfiguration` 已在
`application.yml` 排除，`ChatModel` 构造由 `DeepSeekChatModelFactory` 独占（避免空 api-key 报错与重复 bean）。

## 工具清单
| 工具 | 所需权限 | 说明 | 二次确认 |
|---|---|---|:---:|
| createUser | sys:user:add | 创建用户（username/password 必填，其余可选） | |
| deleteUser | sys:user:delete | 删除用户（id） | ✓ |
| listUsers | sys:user:list | 查询用户列表（只读，keyword/limit 可选） | |
| assignRoles | sys:user:role | 给用户分配角色（覆盖其原有角色） | ✓ |
| listRoles | sys:role:list | 查询角色列表（只读） | |
| createRole | sys:role:add | 创建角色（roleCode/roleName 必填） | |
| assignRoleMenus | sys:role:perm | 给角色分配菜单权限（覆盖其原有菜单） | ✓ |
| navigateTo | （无） | 跳转前端页面 + 高亮资源，返回 `action` 事件 | |

## 事件协议（SSE）
```
event: tool
data: {"name":"createUser","args":{"username":"bob"}}

event: result
data: "已创建用户 bob（id=42）"

event: action
data: {"path":"/sys/user","highlightId":42}

event: confirm                    # 破坏性工具：先请用户确认
data: {"tool":"deleteUser","args":{"id":42},"message":"确认删除用户 #42？此操作不可撤销。"}

event: done
```
客户端对 `confirm` 点「执行」后，回发 `{message, confirm:{tool,args}}`，服务端直接执行该工具并产出
`tool`→`result`→（`action`）→`done`。

## 后续阶段（未实现）
- 软删策略（当前 deleteUser 为硬删除）。
- 限流改为 Redis 实现，支持多实例部署。
- 前端聊天面板（Layout 抽屉）+ action 事件渲染跳转。
