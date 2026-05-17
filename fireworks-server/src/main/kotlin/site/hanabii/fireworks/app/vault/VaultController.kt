package site.hanabii.fireworks.app.vault

import jakarta.servlet.http.HttpSession
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import site.hanabii.fireworks.app.AppException
import site.hanabii.fireworks.app.ErrorCode
import site.hanabii.fireworks.domain.vault.PasswordEntry
import java.security.SecureRandom

@RestController
@RequestMapping("/api/vault")
class VaultController(
    private val vaultService: VaultService,
    private val vaultSessionService: VaultSessionService
) {

    // ---------- 初始化与认证 ----------

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    fun setup(@Valid @RequestBody req: SetupRequest) {
        vaultService.setup(req.masterPassword)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest, session: HttpSession): ResponseEntity<Map<String, Boolean>> {
        vaultService.login(req.masterPassword, session)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/logout")
    fun logout(session: HttpSession): ResponseEntity<Map<String, Boolean>> {
        vaultSessionService.clear(session)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/status")
    fun status(session: HttpSession): VaultStatusResponse {
        return VaultStatusResponse(
            initialized = vaultService.isInitialized(),
            authenticated = vaultSessionService.getSeed(session) != null
        )
    }

    // ---------- 密码条目 CRUD ----------

    @GetMapping("/entries")
    fun listEntries(session: HttpSession): List<PasswordEntry> {
        return vaultService.listEntries(session)
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    fun createEntry(@Valid @RequestBody req: EntryRequest, session: HttpSession): PasswordEntry {
        return vaultService.addEntry(session, req.toEntity())
    }

    @GetMapping("/entries/{id}")
    fun getEntry(@PathVariable id: Long, session: HttpSession): PasswordEntry {
        return vaultService.getEntry(session, id)
            ?: throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "Entry not found: $id"
            )
    }

    @PutMapping("/entries/{id}")
    fun updateEntry(@PathVariable id: Long, @Valid @RequestBody req: UpdateEntryRequest, session: HttpSession): PasswordEntry {
        val existing = vaultService.getEntry(session, id)
            ?: throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "Entry not found: $id"
            )
        return vaultService.updateEntry(session, existing.copy(
            website = req.website ?: existing.website,
            username = req.username ?: existing.username,
            notes = req.notes ?: existing.notes,
            password = req.password ?: existing.password,
            mode = req.mode ?: existing.mode,
            length = req.length ?: existing.length,
            useLowercase = req.useLowercase ?: existing.useLowercase,
            useUppercase = req.useUppercase ?: existing.useUppercase,
            useDigits = req.useDigits ?: existing.useDigits,
            useSymbols = req.useSymbols ?: existing.useSymbols
        ))
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEntry(@PathVariable id: Long, session: HttpSession) {
        vaultService.requireAuth(session)
        val deleted = vaultService.deleteEntry(id)
        if (!deleted) {
            throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "Entry not found: $id"
            )
        }
    }

    // ---------- 轮换与派生 ----------

    @PostMapping("/entries/{id}/rotate")
    fun rotateEntry(@PathVariable id: Long, session: HttpSession): PasswordEntry {
        return vaultService.rotateEntry(session, id)
    }

    @GetMapping("/entries/{id}/password")
    fun derivePassword(
        @PathVariable id: Long,
        @RequestParam(required = false) counter: Int?,
        session: HttpSession
    ): DerivedPasswordResponse {
        val derived = vaultService.derivePassword(session, id, counter)
        return DerivedPasswordResponse(
            entry = derived.entry,
            password = derived.password,
            counter = derived.counter
        )
    }

    // ---------- 随机密码生成 ----------

    @GetMapping("/generate-password")
    fun generatePassword(
        @RequestParam(defaultValue = "16") length: Int,
        @RequestParam(defaultValue = "true") lowercase: Boolean,
        @RequestParam(defaultValue = "true") uppercase: Boolean,
        @RequestParam(defaultValue = "true") digits: Boolean,
        @RequestParam(defaultValue = "true") symbols: Boolean
    ): Map<String, String> {
        require(length in 8..64) { "length must be 8..64, got $length" }
        require(lowercase || uppercase || digits || symbols) { "至少需要开启一个字符集" }

        val charset = buildString {
            if (lowercase) append("abcdefghijklmnopqrstuvwxyz")
            if (uppercase) append("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            if (digits) append("0123456789")
            if (symbols) append("!@#\$%^&*()-_=+[]{};:,.<>?")
        }
        val random = SecureRandom()
        val password = (1..length).map { charset[random.nextInt(charset.length)] }.joinToString("")
        return mapOf("password" to password)
    }
}

// ---------- 请求 / 响应 DTO ----------

data class SetupRequest(@field:NotBlank val masterPassword: String)

data class LoginRequest(@field:NotBlank val masterPassword: String)

data class VaultStatusResponse(
    val initialized: Boolean,
    val authenticated: Boolean
)

data class EntryRequest(
    val website: String? = null,
    val username: String? = null,
    val notes: String? = null,
    val password: String? = null,   // 仅 STORED 模式
    val mode: String? = null,       // "DERIVED" 或 "STORED"
    val counter: Int? = null,
    val length: Int? = null,
    val useLowercase: Boolean? = null,
    val useUppercase: Boolean? = null,
    val useDigits: Boolean? = null,
    val useSymbols: Boolean? = null
) {
    fun toEntity(): PasswordEntry = PasswordEntry(
        website = website ?: "",
        username = username ?: "",
        notes = notes ?: "",
        password = password,
        mode = mode ?: "DERIVED",
        counter = counter ?: 1,
        length = length ?: 16,
        useLowercase = useLowercase ?: true,
        useUppercase = useUppercase ?: true,
        useDigits = useDigits ?: true,
        useSymbols = useSymbols ?: true
    )
}

data class UpdateEntryRequest(
    val website: String? = null,
    val username: String? = null,
    val notes: String? = null,
    val password: String? = null,   // 仅 STORED 模式
    val mode: String? = null,       // "DERIVED" 或 "STORED"
    val length: Int? = null,
    val useLowercase: Boolean? = null,
    val useUppercase: Boolean? = null,
    val useDigits: Boolean? = null,
    val useSymbols: Boolean? = null
)

data class DerivedPasswordResponse(
    val entry: PasswordEntry,
    val password: String,
    val counter: Int
)
