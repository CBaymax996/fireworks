package site.hanabii.fireworks.app.vault

import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 密码库加密服务。
 *
 * 用于存储模式（STORED）下加密/解密用户手动输入的密码原文。
 * 加密密钥从会话中的 PBKDF2 种子派生（SHA-256 → 32 字节 AES-256 密钥）。
 * 使用 AES-256-GCM 加密，输出格式：Base64(IV || ciphertext || authTag)。
 */
@Service
class VaultCryptoService(
    private val vaultSessionService: VaultSessionService
) {
    companion object {
        private const val AES_ALGORITHM = "AES/GCM/NoPadding"
        private const val AES_KEY_ALGORITHM = "AES"
        private const val GCM_IV_LENGTH = 12   // 字节
        private const val GCM_TAG_LENGTH = 128  // bits
    }

    private val secureRandom = SecureRandom()

    /**
     * 加密明文密码。
     * @param session 当前 HTTP 会话，用于获取种子
     * @param plaintext 明文密码
     * @return Base64 编码的加密结果（IV + ciphertext + authTag）
     */
    fun encrypt(session: HttpSession, plaintext: String): String {
        val seed = vaultSessionService.getSeed(session)
            ?: throw IllegalStateException("No vault seed in session — vault must be unlocked first")
        val aesKey = deriveAesKey(seed)

        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // 拼接 IV + ciphertext + authTag（GCM 模式下 cipher.doFinal 已包含 authTag 在尾部）
        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * 解密密文密码。
     * @param session 当前 HTTP 会话，用于获取种子
     * @param ciphertext Base64 编码的加密数据（IV + ciphertext + authTag）
     * @return 明文密码
     */
    fun decrypt(session: HttpSession, ciphertext: String): String {
        val seed = vaultSessionService.getSeed(session)
            ?: throw IllegalStateException("No vault seed in session — vault must be unlocked first")
        val aesKey = deriveAesKey(seed)

        val combined = Base64.getDecoder().decode(ciphertext)

        // 格式：IV（12字节） + ciphertext + authTag（16字节）
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encryptedData = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val plaintext = cipher.doFinal(encryptedData)
        return String(plaintext, Charsets.UTF_8)
    }

    /**
     * 从 PBKDF2 种子派生 AES-256 密钥。
     * 对种子做 SHA-256 得到 32 字节密钥。
     */
    private fun deriveAesKey(seed: ByteArray): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(seed)
        return SecretKeySpec(hash, AES_KEY_ALGORITHM)
    }
}
