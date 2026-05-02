package site.hanabii.fireworks.app.vault

import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import site.hanabii.fireworks.app.AppException
import site.hanabii.fireworks.app.ErrorCode
import site.hanabii.fireworks.domain.vault.DerivedPassword
import site.hanabii.fireworks.domain.vault.PasswordDerivation
import site.hanabii.fireworks.domain.vault.PasswordEntry
import site.hanabii.fireworks.domain.vault.PasswordEntryRepository
import site.hanabii.fireworks.domain.vault.VaultConfig
import site.hanabii.fireworks.domain.vault.VaultRepository
import java.time.Instant

/**
 * 密码库业务服务。
 * 派生生成路线：不存储密码，通过主密码 seed + 条目规则确定性派生。
 */
@Service
class VaultService(
    private val vaultRepository: VaultRepository,
    private val entryRepository: PasswordEntryRepository,
    private val vaultSessionService: VaultSessionService
) {

    private val passwordEncoder = BCryptPasswordEncoder()

    fun isInitialized(): Boolean = vaultRepository.exists()

    fun setup(masterPassword: String) {
        if (vaultRepository.exists()) {
            throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.BAD_REQUEST,
                message = "Vault already initialized"
            )
        }
        val salt = PasswordDerivation.generateSalt()
        val hash = passwordEncoder.encode(masterPassword)
            ?: throw IllegalStateException("Failed to hash master password")
        vaultRepository.save(VaultConfig(passwordHash = hash, encryptionSalt = salt))
    }

    fun login(masterPassword: String, session: HttpSession) {
        val config = vaultRepository.find()
            ?: throw AppException(
                code = ErrorCode.VAULT_NOT_INITIALIZED,
                status = HttpStatus.FORBIDDEN,
                message = "Vault not initialized"
            )
        if (!passwordEncoder.matches(masterPassword, config.passwordHash)) {
            throw AppException(
                code = ErrorCode.INVALID_CREDENTIALS,
                status = HttpStatus.UNAUTHORIZED,
                message = "Invalid master password"
            )
        }
        val seed = PasswordDerivation.deriveSeed(masterPassword, config.encryptionSalt)
        vaultSessionService.storeSeed(session, seed)
    }

    fun requireAuth(session: HttpSession): ByteArray {
        if (!vaultRepository.exists()) {
            throw AppException(
                code = ErrorCode.VAULT_NOT_INITIALIZED,
                status = HttpStatus.FORBIDDEN,
                message = "Vault not initialized"
            )
        }
        return vaultSessionService.getSeed(session)
            ?: throw AppException(
                code = ErrorCode.NOT_AUTHENTICATED,
                status = HttpStatus.UNAUTHORIZED,
                message = "Not authenticated"
            )
    }

    fun addEntry(entry: PasswordEntry): PasswordEntry {
        return entryRepository.save(entry)
    }

    fun listEntries(): List<PasswordEntry> {
        return entryRepository.findAll()
    }

    fun getEntry(id: Long): PasswordEntry? {
        return entryRepository.findById(id)
    }

    fun updateEntry(entry: PasswordEntry): PasswordEntry {
        require(entry.id != null) { "Entry id must not be null for update" }
        return entryRepository.save(entry.copy(updatedAt = Instant.now()))
    }

    fun deleteEntry(id: Long): Boolean {
        return entryRepository.deleteById(id)
    }

    fun rotateEntry(session: HttpSession, id: Long): PasswordEntry {
        requireAuth(session)
        val entry = entryRepository.findById(id)
            ?: throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "Entry not found: $id"
            )
        return entryRepository.save(entry.copy(counter = entry.counter + 1, updatedAt = Instant.now()))
    }

    fun derivePassword(session: HttpSession, id: Long, counter: Int?): DerivedPassword {
        val seed = requireAuth(session)
        val entry = entryRepository.findById(id)
            ?: throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "Entry not found: $id"
            )
        val effectiveCounter = counter ?: entry.counter
        if (effectiveCounter < 1 || effectiveCounter > entry.counter) {
            throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.BAD_REQUEST,
                message = "counter must be between 1 and ${entry.counter}, got $effectiveCounter"
            )
        }
        val password = PasswordDerivation.derivePassword(seed, entry, effectiveCounter)
        return DerivedPassword(entry = entry, password = password, counter = effectiveCounter)
    }
}
