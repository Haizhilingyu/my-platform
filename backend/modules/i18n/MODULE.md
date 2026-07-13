# 国际化管理模块 (i18n)

## 功能概述
提供 DB 驱动的国际化消息管理：基于 `HybridMessageSource`（DB 主 + properties 兜底）实现运行时翻译解析，
配套翻译 CRUD、Excel/JSON 导入导出能力，替换原有基于独立翻译表的菜单本地化方案。

## 核心机制
- **HybridMessageSource**：`@Primary` 的 `MessageSource`，解析时先查 `i18n_message` 表，未命中回退到
  `classpath:i18n/messages*.properties`。`Messages` 工具类与 Bean Validation `{key}` 插值均自动走此 bean。
- **缓存失效**：更新翻译时发布 `I18nMessageUpdatedEvent`，`HybridMessageSource` 监听并清除对应 locale 缓存。
- **DBMessageProvider SPI**：`JpaDBMessageProvider` 实现，按 locale 加载扁平 `{key: value}` 映射。

## 依赖
- platform-common（Result / BaseEntity / 异常 / 安全注解 / Messages）
- PostgreSQL + Flyway（建表和种子数据随模块自动执行）
- Apache POI（Excel 导入导出）

## Maven 依赖
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>i18n-module</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## REST API 列表

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | /api/i18n/messages | sys:i18n:list | 分页查询翻译（locale/module/keyLike 过滤） |
| GET | /api/i18n/messages/all | sys:i18n:list | 获取某 locale 的扁平 `{key: value}` 映射（前端 overlay） |
| GET | /api/i18n/messages/export | sys:i18n:export | 导出 JSON 或 Excel（format=json\|xlsx） |
| PUT | /api/i18n/messages/{id} | sys:i18n:edit | 更新翻译值，触发缓存失效 |
| POST | /api/i18n/messages/import | sys:i18n:import | 导入 JSON 或 Excel（仅更新已知 key，未知 key 报错） |

## 校验规则
- `I18nMessageUpdateDTO`: value `@NotBlank+@Size(max=5000)`
- `I18nMessageImportDTO`: locale `@NotBlank+@Pattern(^(zh-CN|en)$)`，items `@Valid+@NotNull`
- 边界测试：`src/test/java/.../dto/` 下 2 个 `*BoundaryTest.java`

## 权限标识
- `sys:i18n:list` / `sys:i18n:edit` / `sys:i18n:import` / `sys:i18n:export`

## 领域事件
- `I18nMessageUpdatedEvent(locale)` — 翻译更新时发布，`HybridMessageSource` 监听失效缓存

## 数据库表
`i18n_message`（Flyway V104 自动建表 + 播种种子：properties + 前端 JSON + 菜单翻译 ~1000+ 行）

## Excel 格式
表头：`Message Key | Module | Description | Value`。key/module/description 列锁定，仅 value 列可编辑。
