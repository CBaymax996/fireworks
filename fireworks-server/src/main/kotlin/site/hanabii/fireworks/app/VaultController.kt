package site.hanabii.fireworks.app

import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import site.hanabii.fireworks.domain.PasswordEntry

@RestController
@RequestMapping("/api/vault")
class VaultController(
    private val vaultService: VaultService,
    private val vaultSessionService: VaultSessionService
) {

    // ---------- 初始化与认证 ----------

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    fun setup(@RequestBody req: SetupRequest) {
        if (vaultService.isInitialized()) {
            throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.BAD_REQUEST,
                message = "Vault already initialized"
            )
        }
        if (req.masterPassword.isBlank()) {
            throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.BAD_REQUEST,
                message = "Master password must not be blank"
            )
        }
        vaultService.setup(req.masterPassword)
    }

    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest, session: HttpSession): ResponseEntity<Map<String, Boolean>> {
        if (req.masterPassword.isBlank()) {
            throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.BAD_REQUEST,
                message = "Master password must not be blank"
            )
        }
        val config = vaultService.authenticate(req.masterPassword)
            ?: throw AppException(
                code = ErrorCode.INVALID_CREDENTIALS,
                status = HttpStatus.UNAUTHORIZED,
                message = "Invalid master password"
            )
        vaultSessionService.storeKey(session, req.masterPassword, config.encryptionSalt)
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
            authenticated = vaultSessionService.getKey(session) != null
        )
    }

    // ---------- 密码条目 CRUD ----------

    @GetMapping("/entries")
    fun listEntries(session: HttpSession): List<PasswordEntry> {
        val key = requireKey(session)
        return vaultService.listEntries(key)
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    fun createEntry(@RequestBody req: EntryRequest, session: HttpSession): PasswordEntry {
        val key = requireKey(session)
        req.validate()
        val entry = PasswordEntry(
            website = req.website,
            username = req.username,
            password = req.password,
            notes = req.notes
        )
        return vaultService.addEntry(key, entry)
    }

    @GetMapping("/entries/{id}")
    fun getEntry(@PathVariable id: Long, session: HttpSession): PasswordEntry {
        val key = requireKey(session)
        return vaultService.getEntry(key, id)
            ?: throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "Entry not found: $id"
            )
    }

    @PutMapping("/entries/{id}")
    fun updateEntry(@PathVariable id: Long, @RequestBody req: EntryRequest, session: HttpSession): PasswordEntry {
        val key = requireKey(session)
        req.validate()
        vaultService.getEntry(key, id)
            ?: throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "Entry not found: $id"
            )
        val entry = PasswordEntry(
            id = id,
            website = req.website,
            username = req.username,
            password = req.password,
            notes = req.notes
        )
        return vaultService.updateEntry(key, entry)
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEntry(@PathVariable id: Long, session: HttpSession) {
        requireKey(session)
        val deleted = vaultService.deleteEntry(id)
        if (!deleted) {
            throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "Entry not found: $id"
            )
        }
    }

    // ---------- 辅助方法 ----------

    private fun requireKey(session: HttpSession): javax.crypto.SecretKey {
        if (!vaultService.isInitialized()) {
            throw AppException(
                code = ErrorCode.VAULT_NOT_INITIALIZED,
                status = HttpStatus.FORBIDDEN,
                message = "Vault not initialized"
            )
        }
        return vaultSessionService.getKey(session)
            ?: throw AppException(
                code = ErrorCode.NOT_AUTHENTICATED,
                status = HttpStatus.UNAUTHORIZED,
                message = "Not authenticated"
            )
    }
}

data class SetupRequest(val masterPassword: String)

data class LoginRequest(val masterPassword: String)

data class VaultStatusResponse(
    val initialized: Boolean,
    val authenticated: Boolean
)

data class EntryRequest(
    val website: String,
    val username: String,
    val password: String,
    val notes: String = ""
)

private fun EntryRequest.validate() {
    if (website.isBlank() || username.isBlank() || password.isBlank()) {
        throw AppException(
            code = ErrorCode.INVALID_REQUEST,
            status = HttpStatus.BAD_REQUEST,
            message = "website, username and password must not be blank"
        )
    }
}
