# 密码本（Vault）功能设计文档

- 日期：2026-05-02
- 作者：chuhao
- 状态：已通过 brainstorm 阶段，待实现

## 1. 背景

`fireworks-server` 已有的 Vault 模块走「**加密存储**」路线：用户在前端输入完整密码，后端用 AES-GCM
加密后存入 SQLite，主密码经 PBKDF2 派生 AES 密钥用于解密。该实现包含：
`VaultCryptoService`、`VaultService`、`VaultController`、`VaultSessionService`、
`PasswordEntry`、`VaultConfig`，以及对应的 Ktorm DO 与仓库实现。

实际产品需求是「**派生生成**」路线（类 LessPass / Master Password）：
> 通过主密码 + 应用名（+ 用户名 + 计数器）派生出该应用的强随机密码；
> 数据库只存元数据与生成规则，不存密码本身。
> 用主密码解锁后，在前端可查看 / 复制各应用的派生密码。

本文档定义将现有实现切换为「派生生成」路线的设计。

## 2. 目标与非目标

### 2.1 目标

- 数据库不再保存任何形式的密码（密文亦不存）。
- 通过 `(主密码, 站点, 用户名, 计数器, 字符集规则, 长度)` 决定性地派生强随机密码。
- 主密码错误时能立即提示，未初始化时返回明确状态。
- 同一站点支持多账号（不同 username → 不同派生密码）。
- 网站强制改密码时，通过递增 counter 生成新密码；旧密码可通过指定 counter 回放派生。
- 严格区分应用登录账号（`Account`）与密码本条目（`PasswordEntry`），在 domain / infra 层物理隔离。
- 业务/不变性校验在 domain 层；Controller 仅做"格式级"基本校验；Service 层只协调用例。

### 2.2 非目标

- 多用户、共享密码本、组织级权限。
- 浏览器插件 / 自动填表。
- 前端派生（master password 不离开浏览器）—— 已决定后端派生，复用现有 session 模型。
- 旧加密数据迁移到新 schema —— 当前仅有 32 KB 开发数据库，直接重建。
- 自定义"排除字符"清单（YAGNI；后续如遇到不允许特殊字符的网站再加）。

## 3. 总体架构

三层职责严格分离：

| 层 | 职责 | 不允许的事 |
|---|---|---|
| **domain/vault/** | 实体、不变性约束、派生算法（纯函数） | 引入 Spring / Ktorm；处理 HTTP / Session |
| **app/vault/** | 业务用例编排、BCrypt 验证、Session 读写、抛 `AppException` | 重复 domain 已校验的字段约束；编写派生算法 |
| **infra/vault/** | Ktorm 表映射、仓库实现 | 业务规则；密码学计算 |
| **controller** | 反序列化、`@Valid` 基本校验（NotBlank、Size）、调用 service、序列化 | 任何 `if/throw`、状态判断、业务逻辑 |

### 3.1 包结构调整

```
domain/
  auth/
    Account.kt                        ← 移自 domain/Account.kt
    AccountRepository.kt              ← 移自 domain/AccountRepository.kt
  vault/
    VaultConfig.kt                    ← 移自 domain/VaultConfig.kt
    VaultRepository.kt                ← 移自 domain/VaultRepository.kt
    PasswordEntry.kt                  ← 移自 domain/PasswordEntry.kt（结构改造）
    PasswordEntryRepository.kt        ← 移自 domain/PasswordEntryRepository.kt（签名调整）
    PasswordDerivation.kt             ← 新增；纯 Kotlin object
    DerivedPassword.kt                ← 新增；值对象
  User.kt                             ← 暂保留原位（与本次改动无关）
  UserRepository.kt                   ← 暂保留原位
infra/
  auth/
    AccountDO.kt                      ← 移自 infra/AccountDO.kt
    AccountRepositoryImpl.kt          ← 移自 infra/AccountRepositoryImpl.kt
  vault/
    VaultConfigDO.kt                  ← 移自 infra/VaultConfigDO.kt
    VaultRepositoryImpl.kt            ← 移自 infra/VaultRepositoryImpl.kt
    PasswordEntryDO.kt                ← 移自 infra/PasswordEntryDO.kt（schema 改造）
    PasswordEntryRepositoryImpl.kt    ← 移自 infra/PasswordEntryRepositoryImpl.kt
  UserDO.kt                           ← 暂保留原位
  UserRepositoryImpl.kt               ← 暂保留原位
  config/
    SchemaInitializer.kt              ← 引用更新后的 DDL 路径
    KtormConfig.kt                    ← 不变
app/
  AuthController.kt / AuthService.kt / AuthSessionService.kt   ← 不动（应用登录）
  vault/
    VaultController.kt                ← 重写为纯 dispatcher
    VaultService.kt                   ← 重写：业务规则汇聚于此
    VaultSessionService.kt            ← 仅封装 session 读写（语义：vaultSeed）
  UserController.kt                   ← 不动
  GlobalExceptionHandler              ← 新增 MethodArgumentNotValidException 处理
```

> `app/crypto/VaultCryptoService.kt` 整体废弃；其 PBKDF2 部分上移到
> `domain/vault/PasswordDerivation.kt`（去掉 `@Service` 注解、改为纯 object），
> AES 加解密代码删除。

## 4. 派生算法

### 4.1 输入与输出

```
登录时：
  master_seed = PBKDF2WithHmacSHA256(masterPassword, encryptionSalt, 100_000, 256-bit)
  → 32 字节，以 ByteArray 存入 HttpSession（attribute 名：vaultSeed）

派生单条密码时：
  label   = "${entry.website}|${entry.username}|${counter}"
  raw     = HMAC-SHA256(master_seed, label.toByteArray(UTF-8))   // 32 字节
  password = encode(raw, length=entry.length, charsets=entry.useLower/Upper/Digit/Symbol)
```

> 现有 `VaultSessionService` 的 session attribute `vaultKey` 重命名为 `vaultSeed`，
> 存储类型从 `SecretKey.encoded` 改为直接的 `ByteArray`；不再需要 `SecretKeySpec` 重构。

### 4.2 字符集

- `lowercase`: `abcdefghijklmnopqrstuvwxyz`
- `uppercase`: `ABCDEFGHIJKLMNOPQRSTUVWXYZ`
- `digits`: `0123456789`
- `symbols`: `!@#$%^&*()-_=+[]{};:,.<>?`（避开 `'"\\\` 等转义易混字符）

### 4.3 编码（保证字符集覆盖）

熵扩展 + LessPass 风格的两步法：

0. **熵扩展**：HMAC-SHA256 单次只输出 32 字节；当 `length > 36`（约 256 bit / log2(94)）时
   不够。串接 `HMAC(seed, "<label>#<i>".toByteArray(UTF-8))` 其中 i = 0, 1, 2, ...，
   拼接得到 `length × 2` 字节的字节流 `raw`，足够任何 8..64 长度。
   `<label>` 即 §4.1 的 `${entry.website}|${entry.username}|${counter}`。
1. 把 `raw` 视为 base-N 大整数（N = 启用字符集合并后的字符数），按 `length` 个字符长度展开，
   得到候选 password。
2. 强制注入：当某个被启用的字符集在候选 password 中没出现，从 `raw` 后续字节派生一个索引把该字符集
   中的某个字符替换进去。注入位置和字符均由 `raw` 决定，结果仍是确定性的。

> 算法可参考 LessPass `lesspass-core`（ISC 许可证，可商用兼容）的 JS 实现思路并翻译为 Kotlin。
> 选择性翻译不直接复制源码，规避许可证混入。

### 4.4 性能

- PBKDF2 100k 仅在登录时计算一次。
- HMAC-SHA256 派生单条密码 < 1 ms，列表 100 条派生 < 100 ms。

## 5. 数据模型

### 5.1 `domain/vault/PasswordEntry.kt`

```kotlin
data class PasswordEntry(
    val id: Long? = null,
    val website: String,
    val username: String,           // 第三方网站账号；与 Account.username 无关。
    val notes: String = "",
    val counter: Int = 1,
    val length: Int = 16,
    val useLowercase: Boolean = true,
    val useUppercase: Boolean = true,
    val useDigits: Boolean = true,
    val useSymbols: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(website.isNotBlank()) { "website must not be blank" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(counter >= 1) { "counter must be >= 1, got $counter" }
        require(length in 8..64) { "length must be 8..64, got $length" }
        require(useLowercase || useUppercase || useDigits || useSymbols) {
            "at least one charset must be enabled"
        }
    }
}
```

任何路径（构造、`copy()`、ORM 反序列化）都会触发 `init`，不变性约束无法绕过。

### 5.2 `domain/vault/DerivedPassword.kt`

```kotlin
data class DerivedPassword(
    val entry: PasswordEntry,
    val password: String,
    val counter: Int            // 真正用于派生的 counter（可能为历史值）
)
```

### 5.3 `domain/vault/PasswordDerivation.kt`

```kotlin
object PasswordDerivation {
    fun generateSalt(): ByteArray
    fun deriveSeed(masterPassword: String, salt: ByteArray): ByteArray   // 32 字节
    fun derivePassword(
        seed: ByteArray,
        entry: PasswordEntry,
        counter: Int = entry.counter
    ): String
}
```

仅用 JDK `javax.crypto` / `java.security`，不引入 Spring。

### 5.4 `infra/vault/PasswordEntryDO.kt`

```sql
CREATE TABLE IF NOT EXISTS password_entries (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    website       TEXT    NOT NULL,
    username      TEXT    NOT NULL,
    notes         TEXT,
    counter       INTEGER NOT NULL DEFAULT 1,
    length        INTEGER NOT NULL DEFAULT 16,
    use_lowercase INTEGER NOT NULL DEFAULT 1,    -- SQLite 没有原生 BOOLEAN
    use_uppercase INTEGER NOT NULL DEFAULT 1,
    use_digits    INTEGER NOT NULL DEFAULT 1,
    use_symbols   INTEGER NOT NULL DEFAULT 1,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL
)
```

旧表 `password_entries.encrypted_password` 字段被移除。dev 环境删除
`fireworks-test.db` 后由 `SchemaInitializer` 重建即可。

### 5.5 `domain/vault/VaultConfig.kt` —— 不变

```kotlin
data class VaultConfig(
    val id: Long = 1,
    val passwordHash: String,         // BCrypt(masterPassword)
    val encryptionSalt: ByteArray,    // PBKDF2 salt（命名保留，语义改为 seed salt）
    val createdAt: Instant = Instant.now()
)
```

## 6. API 端点

均在 `/api/vault/*`。Controller 仅 dispatcher，每个方法 1 行（不计 `@Valid` 注解）。

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/setup` | 初始化主密码（仅一次） |
| `POST` | `/login` | 主密码解锁，写 session seed |
| `POST` | `/logout` | 清除 session seed |
| `GET`  | `/status` | `{ initialized: bool, authenticated: bool }` |
| `GET`  | `/entries` | 列出元数据（**不含密码**） |
| `POST` | `/entries` | 新增条目 |
| `GET`  | `/entries/{id}` | 取单条元数据 |
| `PUT`  | `/entries/{id}` | 更新条目（不变 counter；轮换走 `/rotate`） |
| `DELETE` | `/entries/{id}` | 删除条目 |
| `POST` | `/entries/{id}/rotate` | counter += 1，返回新元数据 |
| `GET`  | `/entries/{id}/password?counter=N` | 派生密码（默认 `counter = entry.counter`，可传历史 counter） |

历史回放通过 `counter` 查询参数实现，不需要单独的 history 表。

## 7. 数据流

### 7.1 初始化

```
POST /api/vault/setup { masterPassword }
  → controller @Valid (NotBlank)
  → vaultService.setup(masterPassword)
       if vaultRepository.exists() → throw AppException(INVALID_REQUEST, BAD_REQUEST, "Vault already initialized")
       salt = PasswordDerivation.generateSalt()
       hash = BCrypt(masterPassword)
       vaultRepository.save(VaultConfig(passwordHash=hash, encryptionSalt=salt))
  → 201 Created
```

### 7.2 登录解锁

```
POST /api/vault/login { masterPassword }
  → controller @Valid (NotBlank)
  → vaultService.login(masterPassword, session)
       config = vaultRepository.find() ?: throw AppException(VAULT_NOT_INITIALIZED, FORBIDDEN, ...)
       if !BCrypt.matches(masterPassword, config.passwordHash) → throw INVALID_CREDENTIALS, UNAUTHORIZED
       seed = PasswordDerivation.deriveSeed(masterPassword, config.encryptionSalt)
       vaultSessionService.storeSeed(session, seed)
  → 200 OK { success: true }
```

### 7.3 列表（仅元数据）

```
GET /api/vault/entries
  → vaultService.listEntries(session)
       requireAuth(session)   // seed 不存在 → throw NOT_AUTHENTICATED
       return entryRepository.findAll()
  → 200 OK [{ id, website, username, notes, counter, length, useLowercase, ... }]
```

### 7.4 派生单条密码（含历史回放）

```
GET /api/vault/entries/{id}/password?counter=N
  → vaultService.derive(session, id, counter)
       seed   = requireAuth(session)
       entry  = entryRepository.findById(id) ?: throw ENTRY_NOT_FOUND
       effC   = counter ?: entry.counter
       if (effC < 1 || effC > entry.counter) → throw INVALID_REQUEST, BAD_REQUEST, "counter out of range"
       pwd    = PasswordDerivation.derivePassword(seed, entry, effC)
       return DerivedPassword(entry, pwd, effC)
  → 200 OK { entry: {...}, password: "...", counter: 3 }
```

### 7.5 新增 / 更新 / 删除 / 轮换

```
POST /api/vault/entries { website, username, counter?=1, length?=16, useLowercase?=true, ... }
  → controller @Valid
  → vaultService.addEntry(session, req.toEntity())
       requireAuth(session)
       entryRepository.save(entry)        // domain init 失败 → IllegalArgumentException → 400
  → 201 Created { id, website, username, ... }

POST /api/vault/entries/{id}/rotate
  → vaultService.rotateEntry(session, id)
       requireAuth(session)
       entry = repository.findById(id) ?: throw ENTRY_NOT_FOUND
       repository.save(entry.copy(counter = entry.counter + 1, updatedAt = Instant.now()))
  → 200 OK { ..., counter: 2 }
```

## 8. 校验分层

| 校验项 | 位置 | 实现 |
|---|---|---|
| `website` / `username` 非空、≤256 字符 | Controller | `@NotBlank @Size(max=256)` |
| `notes` ≤ 1024 字符 | Controller | `@Size(max=1024)` |
| `masterPassword` 非空 | Controller | `@NotBlank` |
| `counter ≥ 1` | Domain | `init { require(counter >= 1) }` |
| `length ∈ [8, 64]` | Domain | `init { require(length in 8..64) }` |
| 至少一个字符集开启 | Domain | `init { require(...) }` |
| 主密码 BCrypt 匹配 | Service | `vaultService.login` |
| Vault 未初始化 / 未认证 | Service | `requireAuth(session)` |
| `counter` 历史区间合法 | Service | `vaultService.derive` |
| Entry 存在 | Service | repository 返回 null → `ENTRY_NOT_FOUND` |

## 9. 错误处理

继续走 `AppException(code, status, message)` + `GlobalExceptionHandler`。

**`ErrorCode` 不需新增** —— 现有枚举 `VAULT_NOT_INITIALIZED`, `INVALID_CREDENTIALS`,
`NOT_AUTHENTICATED`, `ENTRY_NOT_FOUND`, `INVALID_REQUEST` 全够用。

**`GlobalExceptionHandler` 新增一个 handler：**

```kotlin
@ExceptionHandler(MethodArgumentNotValidException::class)
fun handleValidation(ex: MethodArgumentNotValidException, req: HttpServletRequest)
        : ResponseEntity<ErrorResponse> {
    val message = ex.bindingResult.fieldErrors.joinToString("; ") {
        "${it.field}: ${it.defaultMessage}"
    }
    return ResponseEntity.badRequest().body(ErrorResponse(
        code = ErrorCode.INVALID_REQUEST.name,
        message = message,
        path = req.requestURI,
        timestamp = Instant.now()
    ))
}
```

`IllegalArgumentException` 已被现有 handler 映射为 400，domain `init` 抛出的约束错误自动接住。

**未初始化 vs 主密码错误：** 使用 `VAULT_NOT_INITIALIZED` (403) 与 `INVALID_CREDENTIALS` (401)
两个不同 code，便于前端 UX 显示「先去 Setup」或「主密码错误」。单用户自托管不存在枚举攻击者。

## 10. 测试策略

### 10.1 Domain（纯单测，无 Spring 上下文）

- `PasswordDerivationTest`
  - `deriveSeed` 同输入 → 同 `byte[]`
  - `derivePassword` 同 `(seed, entry, counter)` → 同 `String`
  - 不同 counter / username / website → 不同密码（≥ 100 样本无碰撞）
  - 字符集开关：仅小写时全为小写；仅数字时全为数字
  - 长度边界：8 / 64 都能输出对应长度
  - 至少一个字符集启用时不抛
- `PasswordEntryTest`（不变性约束）
  - `length=7` / `length=65` → `IllegalArgumentException`
  - `counter=0` → `IllegalArgumentException`
  - 全部字符集 false → `IllegalArgumentException`
  - `website` 空 / `username` 空 → `IllegalArgumentException`

### 10.2 App（mock repository）

- `VaultServiceTest`
  - `setup` 重复初始化抛 `AppException(INVALID_REQUEST)`
  - `login` 主密码错抛 `AppException(INVALID_CREDENTIALS)`；正确则写 session seed
  - 未初始化 `login` 抛 `AppException(VAULT_NOT_INITIALIZED)`
  - 未认证状态 `listEntries` / `derive` 抛 `AppException(NOT_AUTHENTICATED)`
  - `derive` `counter` 越界抛 `AppException(INVALID_REQUEST)`
  - `rotateEntry` counter 自增并持久化
- `VaultSessionServiceTest`
  - `storeSeed` 后 `getSeed` 返回相同 byte[]
  - `clear` 后 `getSeed` 返回 null

### 10.3 Controller（`@WebMvcTest` + MockMvc）

- `VaultControllerTest`
  - 端到端：setup → login → POST entry → GET `/password` 验证派生密码合理（长度、字符集）
  - jakarta validation 失败：`website` 空 → 400 INVALID_REQUEST
  - 不同主密码登录后获取同一 entry → 派生密码不同
  - 历史回放：先 rotate 三次，请求 `?counter=1` 派生出与首次创建时相同的密码

### 10.4 Infra

- `PasswordEntryRepositoryImplTest`：schema 变更后增删改查（含新字段）
- `VaultRepositoryImplTest`：仅 import 路径调整（移到 `infra/vault/` 后），用例本身不变
- `AccountRepositoryImplTest`（如有）同样仅 import 路径调整

## 11. 迁移与发布

- **Dev**：删除 `fireworks-server/fireworks-test.db`，启动后 `SchemaInitializer` 重建。
- **Prod**：当前无生产部署，不处理向后兼容。
- **代码改动顺序建议**（细节延后到 writing-plans）：
  1. domain 层（`PasswordEntry`、`PasswordDerivation`、`DerivedPassword` + 子包整理）
  2. infra 层（schema、DO、Repository 实现 + 子包整理）
  3. app 层（`VaultService` 重写、`VaultSessionService` 字段重命名为 seed、`VaultController` 收缩）
  4. `GlobalExceptionHandler` 增 MethodArgumentNotValidException 处理
  5. 测试（按上述分组）
  6. 删旧 db、跑 `./gradlew :fireworks-server:bootRunDev` 联调

## 12. 风险与开放问题

- **算法稳定性**：派生算法的字符集顺序、注入策略一旦发布到生产，未来变更会让所有历史密码失效。
  本次为首次发布，定稿后**不可再改**；后续若需变体加 `version` 字段。
- **Session 内存敏感性**：seed 仍保留在 JVM 堆中。本次不做 secure memory 处理；自托管单用户场景可接受。
- **前端改造**：本文档仅覆盖后端。前端 `fireworks-web` 表单与 store 须随 API 改造，视为后续另一个 spec。
- **国际化与符号兼容性**：当前 symbol 集为定值；如遇网站不允许特定符号需要后续加 `excludedChars` 字段，YAGNI。
