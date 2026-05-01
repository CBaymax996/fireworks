package site.hanabii.fireworks.app.crypto

import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 密码库加密服务。
 *
 * 使用 PBKDF2 从主密码派生 AES-256 密钥，
 * 使用 AES-GCM 加密各网站的密码。
 *
 * 中文主密码通过 UTF-8 编码为字节后参与密钥派生，无需转拼音。
 */
@Service
class VaultCryptoService {

    companion object {
        private const val AES_ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val GCM_IV_LENGTH = 12          // 96 bits
        private const val GCM_TAG_LENGTH = 128        // bits
        private const val KEY_LENGTH = 256            // bits
        private const val ITERATIONS = 100_000
        private const val SALT_LENGTH = 16            // bytes
    }

    private val base64 = Base64.getEncoder()
    private val base64Decoder = Base64.getDecoder()
    private val secureRandom = SecureRandom()

    /**
     * 生成随机 salt。
     */
    fun generateSalt(): ByteArray = ByteArray(SALT_LENGTH).apply { secureRandom.nextBytes(this) }

    /**
     * 从主密码和 salt 派生 AES-256 密钥。
     * 主密码支持中文，使用 toCharArray() 将 Unicode 码点交给 PBKDF2。
     */
    fun deriveKey(masterPassword: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(masterPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }

    /**
     * 加密明文，返回 Base64 字符串（便于 JSON 传输）。
     * 格式: Base64(IV(12) || ciphertext || authTag(16))
     */
    fun encrypt(plaintext: String, key: SecretKey): String {
        val iv = ByteArray(GCM_IV_LENGTH).apply { secureRandom.nextBytes(this) }
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return base64.encodeToString(iv + ciphertext)
    }

    /**
     * 解密 Base64 编码的密文。
     */
    fun decrypt(encryptedBase64: String, key: SecretKey): String {
        val encryptedData = base64Decoder.decode(encryptedBase64)
        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
