# My Platform — 全栈模块化框架

基于 Spring Boot 3.3 + Spring Modulith + Vue 3 + Naive UI + Tailwind CSS 的模块化开发平台。

## 技术栈

### 后端
- Java 21 + Spring Boot 3.3.5 + Spring Modulith 1.3
- Spring Data JPA + Flyway + PostgreSQL
- Spring Security + JWT + 自定义权限注解 (@RequiresPermission)
- Redis 缓存
- 代码质量：Spotless + Checkstyle + SpotBugs + JaCoCo

### 前端
- Vue 3 + TypeScript + Vite
- Naive UI + Tailwind CSS（双层主题覆写，支持暗色切换）
- Pinia + Vue Router
- v-permission 指令 + 路由守卫
- 代码质量：ESLint + Prettier + vue-tsc + commitlint

### 基础设施
- Gitea（代码托管 + Actions CI/CD）
- Nexus（Maven + Npm + Docker 私有仓库）
- PostgreSQL + Redis（NAS 上的开发环境）
- Docker Compose 部署

## 项目结构

```
my-platform/
├── .gitea/workflows/          # CI/CD 配置
├── .githooks/                 # Git hooks（pre-commit 格式检查）
├── docker/                    # Docker 构建文件
├── docs/                      # 文档
├── backend/                   # 后端 Maven 多模块
│   ├── platform-common/       # 公共基础模块
│   ├── platform-starter/      # 聚合 starter
│   ├── modules/sys/           # 系统设置模块（用户/角色/菜单/单位/配置/权限）
│   └── app/                   # 主应用启动入口
└── frontend/                  # 前端 Vue 3 项目
    └── src/
        ├── modules/sys/       # 系统设置模块页面
        ├── shared/            # 跨模块共享组件
        ├── themes/            # 主题系统
        ├── router/            # 路由
        └── stores/            # Pinia 状态管理
```

## 快速开始

### 后端

```bash
cd backend
mvn clean install -DskipTests
cd app
mvn spring-boot:run
# 默认运行在 http://localhost:8090
# Swagger 文档: http://localhost:8090/swagger-ui.html
```

### 前端

```bash
cd frontend
npm install
npm run dev
# 默认运行在 http://localhost:5173
```

### 默认管理员
- 用户名: admin
- 密码: admin123

## 模块复用

其他项目复用系统设置模块：
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>sys-module</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

详见各模块的 `MODULE.md`。

## 开发约定

### 后端代码质量
- 提交前自动执行 Spotless 格式检查（pre-commit hook）
- CI 检查：Spotless + Checkstyle + SpotBugs + JaCoCo（覆盖率 ≥ 60%）
- 启用 pre-commit hook：`git config core.hooksPath .githooks`

### 前端代码质量
- 提交前自动执行 ESLint + Prettier（husky + lint-staged）
- 提交信息必须符合 Conventional Commits 规范
- CI 检查：vue-tsc 类型检查 + ESLint + 构建

### 环境变量
| 变量 | 说明 | 默认值 |
|---|---|---|
| DB_USERNAME | 数据库用户名 | postgres |
| DB_PASSWORD | 数据库密码 | postgres |
| JWT_SECRET | JWT 签名密钥 | (内置默认) |
| NEXUS_USER | Nexus 用户名 | - |
| NEXUS_PASS | Nexus 密码 | - |
