
## T24 — 用户管理增强（锁定状态 + 解锁 + 数据权限配置）

### 完成内容
- `frontend/src/modules/sys/api/types.ts`: UserVO 增加 `locked?: boolean` 可选字段。
- `frontend/src/modules/sys/api/user.ts`: 新增 `unlock(id)` → `POST /sys/user/{id}/unlock`（后端已存在，权限 `sys:user:unlock`）。
- `frontend/src/modules/sys/api/role.ts`: 新增 `saveCustomUnits(id, unitIds)` / `getCustomUnits(id)` → `PUT|GET /sys/role/{id}/data-scope`。
- `frontend/src/modules/sys/views/user/index.vue`:
  - 状态列三态：`locked` → 红「锁定」；`status===1` → 绿「正常」；否则 → 黄「禁用」。
  - 操作列新增「解锁」按钮：仅当 `row.locked===true` 且 `hasPermission('sys:user:unlock')` 时渲染（h() 条件渲染，T6 模式）。
  - 解锁成功后刷新表格。scroll-x 由 910 → 950 适配更宽操作列。
- `frontend/src/modules/sys/views/role/index.vue`:
  - dataScope 选项新增「自定义」(CUSTOM)。
  - 表格「数据范围」列改为 NTag 显示中文 label（之前是裸字符串）。
  - 编辑表单：dataScope=CUSTOM 时显示 NTree（checkable/cascade，多选单位，复用 unitApi.tree）。
  - 响应式：引入 `useBreakpoint`，mobile 下 `label-placement` 切到 `top`，字段堆叠。
  - 保存：先走现有 `roleApi.update`（含 dataScope），CUSTOM 时再 best-effort 调 `saveCustomUnits`。

### 设计系统一致性
- 全程使用 Tailwind 工具类 + Naive UI 语义 props（type=success/error/warning/info，size=small/tiny），无硬编码 hex / 无 inline style。
- h() 条件渲染权限按钮的写法与 T7 unit/index.vue 的 renderLabel 完全一致。

### 已知 limitation（后端缺口，未改后端 per MUST NOT）
1. **UserVO.locked 未填充**：后端 `UserController.list` → `UserService.search` → `UserVO.of` 链路未调用 `LoginSecurityService.isLocked(username)`。需后端在 UserVO 增加 `locked` 字段并在 list 映射时填充，前端解锁按钮/锁定标签才会生效。`LoginSecurityService.isLocked(username)` 已存在（读 Redis key `user:lock:{username}`）。
2. **自定义数据范围端点缺失**：后端 `RoleController` 未暴露 `PUT|GET /sys/role/{id}/data-scope` 端点。`SysRoleDataScope` 实体 / `SysRoleDataScopeRepository` / `sys_role_data_scope` 表（V3）均已就绪，只差 Controller + Service 方法（`RoleService.assignCustomUnits(roleId, unitIds)` / `getCustomUnitIds(roleId)`）。前端已 best-effort 处理：保存失败给 warning toast 但不阻断 dataScope 保存；编辑时加载失败静默忽略。

### 验证
- `npm run lint:check`: 0 errors, 68 warnings（全部为既有全仓模式：no-explicit-any / attributes-order / html-indent，非 T24 引入的错误）。
- `npm run test:run`: 4 files, 28 tests passed（未触及既有测试）。
- `npm run build`: vue-tsc --noEmit + vite build 通过（38s）。

### Git 操作注意
- 本分支有大量并发任务的 WIP（router/index.ts、Layout.vue、session/、app/ 等），曾用 `git stash` 隔离时与并发修改的 router/index.ts 冲突。恢复时用 `git checkout stash@{0} -- <具体文件>` 精确还原 T24 的 5 个文件，未触碰并发任务的 router 改动。T24 提交应只包含这 5 个文件：api/{types,user,role}.ts + views/{user,role}/index.vue。
