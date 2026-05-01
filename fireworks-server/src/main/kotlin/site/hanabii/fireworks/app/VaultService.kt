package site.hanabii.fireworks.app

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import site.hanabii.fireworks.app.crypto.VaultCryptoService
import site.hanabii.fireworks.domain.PasswordEntry
import site.hanabii.fireworks.domain.PasswordEntryRepository
import site.hanabii.fireworks.domain.VaultConfig
import site.hanabii.fireworks.domain.VaultRepository
import java.time.Instant
import javax.crypto.SecretKey

/**
 * 密码库业务服务。
 */
@Service
class VaultService(
    private val vaultRepository: VaultRepository,
    private val entryRepository: PasswordEntryRepository,
    private val cryptoService: VaultCryptoService
) {

    private val passwordEncoder = BCryptPasswordEncoder()

    fun isInitialized(): Boolean = vaultRepository.exists()

    fun setup(masterPassword: String) {
        require(masterPassword.isNotBlank()) { "Master password must not be blank" }
        if (vaultRepository.exists()) {
            throw IllegalStateException("Vault already initialized")
        }
        val salt = cryptoService.generateSalt()
        val hash = passwordEncoder.encode(masterPassword) ?: throw IllegalStateException("Failed to hash master password")
        vaultRepository.save(VaultConfig(passwordHash = hash, encryptionSalt = salt))
    }

    fun authenticate(masterPassword: String): VaultConfig? {
        val config = vaultRepository.find() ?: return null
        return if (passwordEncoder.matches(masterPassword, config.passwordHash)) config else null
    }

    fun addEntry(key: SecretKey, entry: PasswordEntry): PasswordEntry {
        val encrypted = cryptoService.encrypt(entry.password, key)
        val toSave = entry.copy(password = encrypted, updatedAt = Instant.now())
        val saved = entryRepository.save(toSave)
        return saved.copy(password = entry.password)
    }

    fun getEntry(key: SecretKey, id: Long): PasswordEntry? {
        val stored = entryRepository.findById(id) ?: return null
        val decrypted = cryptoService.decrypt(stored.password, key)
        return stored.copy(password = decrypted)
    }

    fun listEntries(key: SecretKey): List<PasswordEntry> {
        return entryRepository.findAll().map { stored ->
            val decrypted = cryptoService.decrypt(stored.password, key)
            stored.copy(password = decrypted)
        }
    }

    fun updateEntry(key: SecretKey, entry: PasswordEntry): PasswordEntry {
        require(entry.id != null) { "Entry id must not be null for update" }
        val encrypted = cryptoService.encrypt(entry.password, key)
        val toSave = entry.copy(password = encrypted, updatedAt = Instant.now())
        val saved = entryRepository.save(toSave)
        return saved.copy(password = entry.password)
    }

    fun deleteEntry(id: Long): Boolean {
        return entryRepository.deleteById(id)
    }
}
