# 消息中心模块 (notify)

## 功能概述
提供实时消息推送能力：WebSocket 长连接、三级紧急度（URGENT / IMPORTANT / NORMAL）、
多范围发布（USER / ROLE / UNIT）、断线重连按 seq 补播、24 小时 TTL 离线消息回放。

## 依赖
- platform-common（统一响应、JWT、CurrentUser）
- sys-module（用于 ROLE / UNIT 范围的接收人展开：SysUserRepository / SysUserRoleRepository / SysUnitRepository）
- PostgreSQL + Flyway（建表随模块自动执行）
- spring-boot-starter-websocket（WebSocket 长连接）

> 单向依赖：`notify → sys`。sys 模块不感知 notify，避免循环。

## Maven 依赖
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>notify-module</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 数据库表（Flyway）
模块自带迁移脚本（与 sys 模块的 V1/V2/V3 不冲突，notify 使用独立版本号）：

- `notify_message`       — 消息主表（标题/正文/级别/发送人/业务类型/过期时间）
- `notify_recipient`     — 接收范围（每条消息 N 行：USER/ROLE/UNIT + 接收方 ID）
- `notify_user_inbox`    — 用户收件箱（user_id + message_id + seq + 已投递/已读状态）

## WebSocket 协议
- 连接路径：`ws://host/ws/notify?token={JWT}`
- 握手认证：从 query string 解析 token，JwtUtil 校验，绑定 session → userId
- 客户端首帧：`{lastSeqReceived: N}` 触发服务端补播 seq > N 且 24h 内未投递的消息
- 服务端推送格式：
  ```json
  { "type": "message", "level": "URGENT", "messageId": 100, "title": "...", "content": "..." }
  ```

## 紧急度（level）
- `URGENT`    — 投递收件箱后立即 WebSocket 推送
- `IMPORTANT` — 仅写入收件箱，由前端拉取或后续轮询策略处理
- `NORMAL`    — 仅写入收件箱

## 接收范围（recipientType）
- `USER` — 直接写入指定 user_id 的收件箱
- `ROLE` — 查询 sys_user_role 取该 role_id 的所有 user_id，批量写入收件箱
- `UNIT` — 调用 SysUnitRepository.findDescendantUnitIds(unitId) 递归取单位及下级，
          再查 sys_user.unit_id IN (...)，批量写入收件箱

## REST API 列表

### 内部接口（管理端，需登录 + 权限）
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | /sys/notify/publish | sys:notify:publish | 管理端手动发布消息 |

### 对外接口（开放 API，供外部应用/SDK 调用）
| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | /openapi/notify/publish | TODO T16 @RequiresAppScope("notify:publish") | 外部应用发布消息 |

> 当前对外接口仅做基本入参校验，**OAuth2 / App Scope 校验由 T16 接入**。

## seq 分配策略
- 每个 user_id 维护独立的单调递增 seq
- 当前实现：`SELECT MAX(seq)+1 FROM notify_user_inbox WHERE user_id = ?`
- 在 `@Transactional` 内执行；多并发同用户场景下因唯一索引 (user_id, seq) 兜底，最坏情况仅多投一次（不影响补播正确性）
- 未来可替换为 `notify_user_seq` 计数器表或 Redis INCR 原子计数

## 重连补播
- 客户端断线重连后首帧发送 `{lastSeqReceived: N}`
- 服务端查询 `notify_user_inbox WHERE user_id=? AND seq > N AND created_at > now() - 24h`
- 逐条推送并标记 `delivered=true, delivered_at=now()`

## 通道扩展（SPI 占位）
当前仅支持 WebSocket。邮件 / 短信 / App Push 通道不在本模块范围（Metis 范围排除）。
预留 `MessageChannel` SPI 接口供后续扩展（当前空实现）。

## 可配置项
| 属性 | 默认值 | 说明 |
|---|---|---|
| app.notify.replay-ttl-hours | 24 | 重连补播 TTL（小时） |
| app.notify.websocket-path | /ws/notify | WebSocket 端点路径 |
