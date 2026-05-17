# CLAUDE.md

项目文档见 [README.md](./README.md)。本文件仅包含 AI 辅助开发所需的规则和约定。

## 数据库规则

- `fireworks.db` — **正式库**，禁止 Hermes / Claude Code 直接读写。只能通过运行中的应用操作。
- `fireworks-test.db` — **测试库**，可随意读写、添加测试数据。测试套件默认使用此库。

## 常用命令

```sh
# 正式启动（前端打包到后端 static/，访问 :8080）
./gradlew :fireworks-server:bootRun

# 开发启动（前端 HMR :5173 + 后端 :8080，访问 :5173）
./gradlew :fireworks-server:bootRunDev

# 运行全部测试
./gradlew :fireworks-server:test

# 运行指定测试
./gradlew :fireworks-server:test --tests "*VaultServiceTest"
```

## 后端架构

三层分离，根包 `site.hanabii.fireworks`：

- `domain/` — 纯 Kotlin 数据类和仓储接口，不引用框架
- `app/` — `@RestController` + `@Service`，业务编排
- `infra/` — Ktorm 表定义（`*DO.kt`，含 DDL 常量和 `toXxx()` 映射）+ `*RepositoryImpl`

新增实体按以下模式：`domain/Foo` → `domain/FooRepository` → `infra/FooDO`（含 DDL）→ `infra/FooRepositoryImpl`，然后在 `SchemaInitializer.init()` 注册 DDL。DDL 必须用 `IF NOT EXISTS`。

## 重要约定

- 代码注释和提交信息用中文
- `BCryptPasswordEncoder` 在 Service 中作为 `private val` 实例化，不是 Spring Bean——保持这个模式
- DDL 定义在 `*DO` 文件中，用 `IF NOT EXISTS`
- 会话密钥（vault key）以原始字节存于 HttpSession，重启后需重新解锁
- 所有 API 错误通过 `AppException(code, status, message)` 统一处理，不在 Controller 里写 ad-hoc `ResponseEntity`
- 主密码支持中文等 Unicode，`deriveKey` 用 `String.toCharArray()` 确保 PBKDF2 看到的是码点

## 密码本两种模式

- **DERIVED（派生）**：不存密码，PBKDF2 派生种子 → HMAC 扩展熵 → 字符集编码。支持 counter 递增轮换。
- **STORED（存储）**：密码 AES-256-GCM 加密存储，密钥从会话种子 SHA-256 派生。不支持轮换/派生。
