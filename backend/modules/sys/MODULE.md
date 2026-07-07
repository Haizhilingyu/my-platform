# 系统设置模块 (sys)

## 功能概述
提供完整的后台管理基础能力：用户管理、角色管理、菜单管理、单位管理、系统配置、权限控制。
每个业务系统都需要的通用模块，可直接复用。

## 依赖
- platform-common
- PostgreSQL + Flyway（建表和初始数据随模块自动执行）
- Redis（权限缓存，可选）

## Maven 依赖
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>sys-module</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 对外 API 门面
- `SysApi.getUserPermissions(userId)` — 获取用户权限标识集合
- `SysApi.getUserRoles(userId)` — 获取用户角色编码集合
- `SysApi.hasPermission(userId, permission)` — 校验单个权限
- `SysApi.getConfig(key, defaultValue)` — 获取系统配置值

## REST API 列表

### 认证
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | /sys/auth/login | - | 登录，返回 JWT |
| GET  | /sys/auth/me | - | 获取当前用户信息 |
| GET  | /sys/auth/permissions | - | 获取当前用户权限列表 |
| GET  | /sys/auth/menus | - | 获取当前用户菜单树 |

### 用户管理
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | /sys/user | sys:user:list | 分页列表 |
| GET | /sys/user/{id} | sys:user:list | 详情 |
| POST | /sys/user | sys:user:add | 新增 |
| PUT | /sys/user/{id} | sys:user:edit | 修改 |
| DELETE | /sys/user/{id} | sys:user:delete | 删除 |
| POST | /sys/user/{id}/roles | sys:user:role | 分配角色 |
| GET | /sys/user/{id}/roles | sys:user:role | 查询已分配角色 |
| POST | /sys/user/{id}/reset-password | sys:user:reset | 重置密码 |

### 角色管理
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | /sys/role | sys:role:list | 列表 |
| GET | /sys/role/{id} | sys:role:list | 详情 |
| POST | /sys/role | sys:role:add | 新增 |
| PUT | /sys/role/{id} | sys:role:edit | 修改 |
| DELETE | /sys/role/{id} | sys:role:delete | 删除 |
| POST | /sys/role/{id}/menus | sys:role:perm | 分配菜单权限 |
| GET | /sys/role/{id}/menus | sys:role:perm | 查询已分配菜单 |

### 菜单管理
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | /sys/menu/tree | sys:menu:list | 菜单树 |
| GET | /sys/menu/{id} | sys:menu:list | 详情 |
| POST | /sys/menu | sys:menu:add | 新增 |
| PUT | /sys/menu/{id} | sys:menu:edit | 修改 |
| DELETE | /sys/menu/{id} | sys:menu:delete | 删除 |

### 单位管理
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | /sys/unit/tree | sys:unit:list | 单位树 |
| GET | /sys/unit/{id} | sys:unit:list | 详情 |
| POST | /sys/unit | sys:unit:add | 新增 |
| PUT | /sys/unit/{id} | sys:unit:edit | 修改 |
| DELETE | /sys/unit/{id} | sys:unit:delete | 删除 |

### 系统配置
| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | /sys/config | sys:config:list | 列表/按分类查询 |
| GET | /sys/config/{key} | sys:config:list | 按 key 查询 |
| POST | /sys/config | sys:config:add | 新增 |
| PUT | /sys/config/{id} | sys:config:edit | 修改 |
| PUT | /sys/config/batch | sys:config:edit | 批量更新 |

### 校验规则

所有 DTO 已添加 Jakarta Bean Validation 注解：
- `UserCreateDTO`: username `@NotBlank+@Size(3-32)+@Pattern`，password `@NotBlank+@Size(6-32)`，email `@Email`，phone `@Pattern`
- `UserUpdateDTO`: 全字段可选，email/phone 同上，status `@Min(0)@Max(1)`
- `RoleDTO`: roleCode `@NotBlank+@Size(3-50)+@Pattern`，roleName `@NotBlank+@Size(100)`，dataScope `@NotBlank`
- `MenuDTO`: menuName `@NotBlank+@Size(50)`，menuType `@NotBlank+@Size(20)`，path/component `@Size(200)`
- `UnitDTO`: unitCode `@NotBlank+@Size(3-50)+@Pattern`，unitName `@NotBlank+@Size(100)`
- `ConfigDTO`: configKey `@NotBlank+@Size(100)+@Pattern(^[a-zA-Z0-9._-]+$)`

所有 PUT 端点已添加 `@Valid`；`RoleController` 和 `ConfigController` 添加了类级 `@Validated`。
边界测试：`src/test/java/.../dto/` 下有 6 个 `*BoundaryTest.java` 文件。

## 权限标识规范
格式：`模块:资源:操作`

预置权限：
- `sys:user:list/add/edit/delete/reset/role`
- `sys:role:list/add/edit/delete/perm`
- `sys:menu:list/add/edit/delete`
- `sys:unit:list/add/edit/delete`
- `sys:config:list/add/edit`

## 领域事件
- `UserCreated(userId, username)` — 用户创建时发布
- `RolePermissionChanged(roleId, roleCode)` — 角色权限变更时发布

## 数据库表
sys_user, sys_role, sys_menu, sys_unit, sys_config,
sys_user_role, sys_role_menu

（Flyway 自动建表，自动插入初始数据：超级管理员 admin/admin123 + 完整菜单）

## 可配置项
| 属性 | 默认值 | 说明 |
|---|---|---|
| app.security.jwt.secret | my-platform-secret-key-... | JWT 签名密钥 |
| app.security.jwt.expiration | 86400000 | JWT 过期时间（毫秒），默认 24h |

## 集成步骤
1. 引入 `sys-module` Maven 依赖
2. 配置数据源（PostgreSQL）
3. 配置 Redis（可选，用于权限缓存）
4. 启动应用，Flyway 自动建表 + 初始化数据
5. 默认管理员：admin / admin123
6. 前端调用 /sys/auth/login 获取 token，后续请求带 Authorization: Bearer {token}
