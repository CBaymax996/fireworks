# Fireworks

个人网站，包含两个功能模块：

- **密码本** — 支持派生生成和加密存储两种模式
- **族谱** — G6 图可视化家族关系

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Kotlin + Spring Boot 4.0 + Ktorm + SQLite |
| 前端 | Vue 3 + Vite + TypeScript + Element Plus + Pinia |
| 构建 | Gradle 9.4 + JDK 25 |

## 项目结构

```
fireworks/
├── fireworks-server/          # 后端
│   └── src/main/kotlin/site/hanabii/fireworks/
│       ├── domain/            # 数据模型 + 仓储接口
│       ├── app/               # 业务逻辑 + Controller
│       └── infra/             # 持久化实现 + 配置
├── fireworks-web/             # 前端
│   └── src/
│       ├── api/               # 后端 API 调用
│       ├── stores/            # Pinia 状态管理
│       └── views/             # 页面组件
└── CLAUDE.md                  # AI 辅助开发规则
```

## 功能

- **密码本** — 两种模式：
  - 派生模式：主密码 + 网站规则 → 确定性生成密码，不存密码原文
  - 存储模式：手动输入或随机生成密码，AES-256-GCM 加密存储
- **族谱** — G6 图可视化家族关系
- **登录系统** — 账号注册/登录（开发中）

## 快速开始

### 环境要求

- JDK 25
- Node.js ≥ 22
- 8080 和 5173 端口可用

### 正式启动

前端构建后嵌入后端，单端口访问：

```bash
./gradlew :fireworks-server:bootRun
```

浏览器打开 http://localhost:8080

### 开发启动

前端热部署 + 后端并行运行，改前端代码即时生效：

```bash
./gradlew :fireworks-server:bootRunDev
```

浏览器打开 http://localhost:5173（Vite 自动代理 `/api` 到后端 8080）

### 运行测试

```bash
./gradlew :fireworks-server:test
```

## 数据库

两个 SQLite 数据库，启动时自动创建：

| 文件 | 用途 | 规则 |
|---|---|---|
| `fireworks-server/fireworks.db` | 正式数据 | AI 工具禁止直接读写 |
| `fireworks-server/fireworks-test.db` | 测试数据 | 可随意操作 |

`*.db` 已加入 `.gitignore`。
