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
 * 支持两种模式：
 * - DERIVED（派生模式）：不存储密码，通过主密码 seed + 条目规则确定性派生。
 * - STORED（存储模式）：用户手动输入密码原文，AES-256-GCM 加密后存储到数据库。
 */
@Service
class VaultService(
    private val vaultRepository: VaultRepository,
    private val entryRepository: PasswordEntryRepository,
    private val vaultSessionService: VaultSessionService,
    private val vaultCryptoService: VaultCryptoService
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

    fun addEntry(session: HttpSession, entry: PasswordEntry): PasswordEntry {
        requireAuth(session)
        // 存储模式：加密密码再落库
        val entryToSave = if (entry.mode == "STORED") {
            entry.copy(password = vaultCryptoService.encrypt(session, entry.password!!))
        } else {
            entry
        }
        val saved = entryRepository.save(entryToSave)
        return decryptIfStored(session, saved)
    }

    fun listEntries(session: HttpSession): List<PasswordEntry> {
        requireAuth(session)
        return entryRepository.findAll().map { decryptIfStored(session, it) }
    }

    fun getEntry(session: HttpSession, id: Long): PasswordEntry? {
        requireAuth(session)
        return entryRepository.findById(id)?.let { decryptIfStored(session, it) }
    }

    fun updateEntry(session: HttpSession, entry: PasswordEntry): PasswordEntry {
        requireAuth(session)
        require(entry.id != null) { "Entry id must not be null for update" }
        // 存储模式：加密密码再落库
        val entryToSave = if (entry.mode == "STORED") {
            entry.copy(password = vaultCryptoService.encrypt(session, entry.password!!), updatedAt = Instant.now())
        } else {
            entry.copy(updatedAt = Instant.now())
        }
        val saved = entryRepository.save(entryToSave)
        return decryptIfStored(session, saved)
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
        // 存储模式条目不支持轮换
        if (entry.mode == "STORED") {
            throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.BAD_REQUEST,
                message = "存储模式条目不支持轮换"
            )
        }
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
        // 存储模式条目不支持派生密码
        if (entry.mode == "STORED") {
            throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.BAD_REQUEST,
                message = "存储模式条目不支持派生密码，请直接查看密码"
            )
        }
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

    /**
     * 如果是存储模式且密码不为空，对密码解密后返回；否则原样返回。
     */
    private fun decryptIfStored(session: HttpSession, entry: PasswordEntry): PasswordEntry {
        return if (entry.mode == "STORED" && entry.password != null) {
            entry.copy(password = vaultCryptoService.decrypt(session, entry.password))
        } else {
            entry
        }
    }
}
