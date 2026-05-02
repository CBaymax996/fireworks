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
        vaultService.requireAuth(session)
        return vaultService.listEntries()
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    fun createEntry(@Valid @RequestBody req: EntryRequest, session: HttpSession): PasswordEntry {
        vaultService.requireAuth(session)
        return vaultService.addEntry(req.toEntity())
    }

    @GetMapping("/entries/{id}")
    fun getEntry(@PathVariable id: Long, session: HttpSession): PasswordEntry {
        vaultService.requireAuth(session)
        return vaultService.getEntry(id)
            ?: throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "Entry not found: $id"
            )
    }

    @PutMapping("/entries/{id}")
    fun updateEntry(@PathVariable id: Long, @Valid @RequestBody req: UpdateEntryRequest, session: HttpSession): PasswordEntry {
        vaultService.requireAuth(session)
        val existing = vaultService.getEntry(id)
            ?: throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "Entry not found: $id"
            )
        return vaultService.updateEntry(existing.copy(
            website = req.website ?: existing.website,
            username = req.username ?: existing.username,
            notes = req.notes ?: existing.notes,
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
