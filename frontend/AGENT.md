# AGENT.md — 前端代码规范

> 本文档为 AI Agent 提供前端项目结构、编码规范和开发约定。遵循本规范可确保代码一致性。

## 项目基本信息

- **语言**: TypeScript + Vue 3 (Composition API + `<script setup>`)
- **UI 框架**: Naive UI（主要）+ Tailwind CSS（布局/自定义样式）
- **构建工具**: Vite 6
- **包管理器**: npm
- **代码规范**: ESLint + Prettier + vue-tsc + commitlint

## 项目结构

```
frontend/
├── index.html                     # 入口 HTML
├── package.json                   # 依赖与脚本
├── tsconfig.json                  # TypeScript 配置
├── tailwind.config.ts             # Tailwind + 设计 token
├── vite.config.ts                 # Vite 配置（路径别名、代理）
├── .eslintrc.cjs                  # ESLint 规则
├── .prettierrc                    # Prettier 格式化
├── commitlint.config.cjs          # 提交信息规范
│
├── src/
│   ├── main.ts                    # 应用入口
│   ├── App.vue                    # 根组件（NConfigProvider + 主题）
│   │
│   ├── modules/                   # ── 业务模块（对应后端模块）
│   │   └── sys/                   # 系统设置模块
│   │       ├── views/             #   页面组件
│   │       │   ├── Login.vue
│   │       │   ├── Dashboard.vue
│   │       │   ├── NotFound.vue
│   │       │   ├── user/          #   用户管理
│   │       │   ├── role/          #   角色管理
│   │       │   ├── menu/          #   菜单管理
│   │       │   ├── unit/          #   单位管理
│   │       │   └── config/        #   系统配置
│   │       ├── components/        #   模块私有组件
│   │       ├── composables/       #   模块私有逻辑
│   │       ├── api/               #   API 封装（http + 接口方法）
│   │       ├── routes.ts          #   模块路由（可选）
│   │       └── README.md          #   模块说明文档
│   │
│   ├── shared/                    # 跨模块共享
│   │   ├── components/            #   通用 UI 组件
│   │   │   └── Layout.vue         #   主布局（侧边栏 + 顶栏）
│   │   ├── composables/           #   通用 composables
│   │   ├── directives/            #   全局指令
│   │   │   └── permission.ts      #   v-permission 指令
│   │   ├── utils/                 #   工具函数
│   │   └── types/                 #   公共类型
│   │
│   ├── themes/                    # 主题系统
│   │   ├── index.ts               #   主题注册器
│   │   ├── presets/               #   预设主题
│   │   └── tokens/                #   设计 token
│   │
│   ├── router/                    # 全局路由
│   │   └── index.ts
│   │
│   ├── stores/                    # Pinia 状态管理
│   │   ├── auth.ts                #   认证状态
│   │   └── theme.ts               #   主题状态
│   │
│   └── styles/
│       └── index.css              # 全局样式 + 设计 Token (CSS Variables)
│
└── packages/                      # 可发布组件库（未来）
```

## 编码规范

### 命名规范

| 内容 | 规范 | 示例 |
|---|---|---|
| 组件文件 | PascalCase | `Login.vue`, `Layout.vue` |
| 文件夹 | kebab-case 或与路由一致 | `user/`, `shared/` |
| TypeScript 接口 | 名词（不加 I 前缀）| `UserVO`, `MenuTreeNode` |
| 函数/变量 | camelCase | `fetchData`, `handleLogin` |
| 常量 | UPPER_SNAKE_CASE | `PUBLIC_PATHS` |
| 路由名称 | PascalCase | `SysUser`, `SysRole` |
| 路由路径 | kebab-case 或一致 | `/sys/user`, `/sys/menu` |
| Store 名 | camelCase | `useAuthStore`, `useThemeStore` |
| API 模块名 | `camelCase + Api` | `userApi`, `menuApi` |
| Composable | `use` + PascalCase | `useAuth`, `useTheme` |

### TypeScript 规范

- **所有 `.ts` 和 `.vue` 文件必须通过 `vue-tsc --noEmit` 类型检查**
- 不要使用 `any`（确需使用时加 `// eslint-disable` 注释）
- 未使用的变量加 `_` 前缀或删除
- 使用 `interface` 定义对象类型（不用 `type`），保留 `type` 给联合类型
- API 响应类型统一定义在 `api/types.ts`

```typescript
// ✅ 推荐
export interface UserVO {
  id: number
  username: string
  status: number
}

// ❌ 避免
export type UserVO = { ... }
```

### Vue 组件规范

- 统一使用 `<script setup lang="ts">` + Composition API
- 组件导入使用 Naive UI 按需导入（`unplugin-vue-components` 自动处理）
- 使用 `defineProps<{...}>()` 类型推导，不用运行时 props
- Template 中复杂逻辑抽成 computed 或函数，不写内联三元

```vue
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { NButton, NSpace, useMessage } from 'naive-ui'
import { userApi } from '@/modules/sys/api/user'

const message = useMessage()
const loading = ref(false)
const data = ref([])

async function fetchData() {
  loading.value = true
  try {
    const res = await userApi.list({ pageNum: 1, pageSize: 10 })
    data.value = res.data.list
  } catch (e: any) {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>
```

### 样式规范

- 优先用 **Naive UI 内置组件**（NButton, NTable, NForm 等）
- 布局和间距用 **Tailwind class**（`flex`, `p-4`, `gap-2` 等）
- **不要**内联 `style=""`，除非动态计算的值
- 自定义颜色用 **CSS 变量**（设计 Token），不硬编码色值

```vue
<!-- ✅ 推荐 -->
<div class="flex items-center gap-2 p-4 bg-[rgb(var(--color-surface))]">
  <NButton type="primary">操作</NButton>
</div>

<!-- ❌ 避免 -->
<div style="display: flex; padding: 16px; background: #fff;">
```

### 主题 Token

设计 Token 定义在 `tailwind.config.ts` 的 `theme.extend.colors` 和 `src/styles/index.css` 的 `:root` / `.dark` CSS 变量中：

```css
:root {
  --color-primary: 64 158 255;
  --color-background: 240 242 245;
  --color-surface: 255 255 255;
  --color-text: 32 34 37;
  --color-border: 229 231 235;
}
```

在 Tailwind 中引用：`bg-[rgb(var(--color-primary))]`

**主题切换**通过 `useThemeStore().toggle()` 实现，同时更新：
1. Naive UI 的 `n-config-provider` 主题
2. `<html>` 的 `.dark` CSS class

## 权限体系

### v-permission 指令

控制元素显隐（元素级别）：

```vue
<NButton v-permission="'sys:user:add'">新增</NButton>
<NButton v-permission="['sys:user:add', 'sys:user:edit']">批量操作</NButton>
```

### 路由守卫

页面级权限在路由 meta 中声明：

```typescript
{
  path: 'sys/user',
  name: 'SysUser',
  component: () => import('@/modules/sys/views/user/index.vue'),
  meta: { title: '用户管理', permission: 'sys:user:list' },
}
```

### 权限判断函数

```typescript
const auth = useAuthStore()
auth.hasPermission('sys:user:add')   // boolean
```

admin 角色自动拥有所有权限（实现为：permissions 集合包含 `*` 时返回 true）。

## API 层规范

### http.ts（axios 实例）

```typescript
// src/modules/<name>/api/http.ts
import axios from 'axios'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

// 请求拦截器：自动注入 token
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：401 自动跳登录
http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)
```

### API 方法命名

```typescript
export const userApi = {
  list(query: UserQuery): Promise<Result<PageResult<UserVO>>> { ... }
  get(id: number): Promise<Result<UserVO>> { ... }
  create(data: UserCreateDTO): Promise<Result<number>> { ... }
  update(id: number, data: UserUpdateDTO): Promise<Result<void>> { ... }
  delete(id: number): Promise<Result<void>> { ... }
  assignRoles(id: number, roleIds: number[]): Promise<Result<void>> { ... }
}
```

- 方法名对应 HTTP 方法语义：`list` (GET 列表)、`get` (GET 单条)、`create` (POST)、`update` (PUT)、`delete` (DELETE)
- 每个 HTTP 方法封装返回泛型 `Result<T>`
- GET 参数用 `params`，POST/PUT 用 `data`

### 类型定义

所有类型集中定义在 `api/types.ts`：

```typescript
// 与后端一一对应
export interface UserVO { ... }
export interface PageResult<T> { list: T[]; total: number; pageNum: number; pageSize: number }
export interface Result<T> { code: number; message: string; data: T }
```

## Store 规范

### 认证 Store（auth.ts）

```typescript
export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref<UserVO | null>(null)
  const permissions = ref<Set<string>>(new Set())
  const menus = ref<MenuTreeNode[]>([])

  async function login(username: string, password: string) { ... }
  async function fetchUserInfo() { ... }
  function hasPermission(perm: string): boolean { ... }
  function logout() { ... }

  return { token, user, permissions, menus, login, fetchUserInfo, hasPermission, logout }
})
```

### 主题 Store（theme.ts）

```typescript
export const useThemeStore = defineStore('theme', () => {
  const isDark = ref(localStorage.getItem('theme') === 'dark')
  const token = computed<ThemeToken>(() => isDark.value ? DARK_TOKENS : LIGHT_TOKENS)

  function toggle() { ... }
  return { isDark, token, toggle }
})
```

**Store 使用原则**:
- 只在需要跨组件共享状态时用 Store
- 页面级别状态留在组件内（`ref`/`reactive`）
- Store 不能直接调 API（API 在组件/composable 中调用，Store 只存状态）

## 代码质量

### 强制检查

| 工具 | 命令 | 触发 |
|---|---|---|
| TypeScript 类型检查 | `npm run type-check` | pre-commit + CI |
| ESLint | `npm run lint` | pre-commit（lint-staged） |
| Prettier | `npm run format` | pre-commit（lint-staged） |
| commitlint | 自动 | commit-msg hook |

### 脚本

```bash
npm run dev          # 启动开发服务器
npm run build        # 类型检查 + 构建
npm run type-check   # 仅类型检查
npm run lint         # ESLint 自动修复
npm run lint:check   # ESLint 仅检查
npm run format       # Prettier 格式化
```

### 提交规范

```bash
feat: 新增用户管理页面
fix: 修复菜单树展开异常
docs: 更新 README
style: 调整按钮间距
refactor: 重构权限检查逻辑
chore: 升级依赖
```

## 常见任务（Agent 操作指南）

### 新增业务模块的前端页面

1. 在 `src/modules/<name>/` 创建目录
2. 创建 `api/types.ts`、`api/http.ts`、`api/<resource>.ts`
3. 创建 `views/<resource>/index.vue`（列表/管理页）
4. 在 `src/router/index.ts` 中添加路由（标注 `meta.permission`）
5. 在 `src/modules/<name>/` 下创建 `README.md`

### 页面组件模板

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NCard, NButton, NSpace, useMessage } from 'naive-ui'
import { xxxApi } from '@/modules/xxx/api/xxx'

const message = useMessage()
const loading = ref(false)
const data = ref([])

async function fetchData() {
  loading.value = true
  try {
    const res = await xxxApi.list()
    data.value = res.data
  } catch (e: any) {
    message.error(e.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<template>
  <NCard>
    <NSpace class="mb-4" justify="end">
      <NButton type="primary">新增</NButton>
    </NSpace>
    <!-- 数据展示 -->
  </NCard>
</template>
```

### 新增 API 方法

```typescript
// 1. 在 types.ts 添加 DTO 和 VO
export interface XxxCreateDTO { ... }
export interface XxxVO { ... }

// 2. 在 api/xxx.ts 添加方法
export const xxxApi = {
  list(): Promise<Result<XxxVO[]>> {
    return http.get('/xxx')
  },
  create(data: XxxCreateDTO): Promise<Result<number>> {
    return http.post('/xxx', data)
  },
}
```

### 处理树形数据

菜单、单位树使用 Naive UI 的 `NTree` 组件：

```vue
<NTree
  key-field="key"
  :data="treeData"
  checkable
  :checked-keys="checkedKeys"
  cascade
  expand-on-click
/>
```

树结构由后端返回，前端不做构建。

### Naive UI 组件使用约定

- Table: `NDataTable` + `columns`（DataTableColumns 类型）
- Form: `NForm` + `NFormItem`（label-placement="left" :label-width="80"）
- Modal: `NModal`（preset="card"）
- 消息提示: `useMessage()` → `message.success() / error() / warning()`
- 表格操作列用 `h()` 函数渲染（`render: (row) => h(NSpace, ...)`）

```typescript
const columns: DataTableColumns<UserVO> = [
  { title: '用户名', key: 'username' },
  {
    title: '操作', key: 'actions',
    render: (row) => h(NSpace, {}, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => handleEdit(row) }, { default: () => '编辑' }),
      ],
    }),
  },
]
```

## 注意事项

1. **不要**在 template 中使用复杂的 JavaScript 表达式，抽成 computed
2. **不要**在 vue 文件中硬编码颜色/尺寸，用 Tailwind class 或 CSS 变量
3. **不要**在组件中直接操作 localStorage，通过 Store 方法
4. **不要**忽略 TypeScript 错误（`vue-tsc --noEmit` 必须零错误）
5. **不要**使用 `any` 类型规避类型检查
6. Naive UI 组件通过 `unplugin-vue-components` 自动按需导入，无需手动 import
7. API 请求基路径通过 Vite proxy 转发到后端，开发时不跨域
8. 登录 Token 存储在 localStorage，每次请求自动注入（axios interceptor）
