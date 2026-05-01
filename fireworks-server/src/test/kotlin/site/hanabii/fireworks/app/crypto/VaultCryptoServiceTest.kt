package site.hanabii.fireworks.app.crypto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 密码库加密服务单元测试。
 *
 * 覆盖：中文主密码密钥派生、AES-GCM 加解密、不同 IV 产出不同密文、不同密钥互斥。
 */
class VaultCryptoServiceTest {

    private val cryptoService = VaultCryptoService()

    @Test
    fun `encrypt and decrypt with Chinese master password should work`() {
        val masterPassword = "我爱北京天安门"
        val salt = cryptoService.generateSalt()
        val key = cryptoService.deriveKey(masterPassword, salt)

        val plaintext = "mySecretPassword123!@#"
        val encrypted = cryptoService.encrypt(plaintext, key)
        val decrypted = cryptoService.decrypt(encrypted, key)

        assertThat(decrypted).isEqualTo(plaintext)
        assertThat(encrypted).isNotEqualTo(plaintext)
    }

    @Test
    fun `encrypt should produce different results for same plaintext with different IVs`() {
        val masterPassword = "白日依山尽"
        val salt = cryptoService.generateSalt()
        val key = cryptoService.deriveKey(masterPassword, salt)

        val plaintext = "github_password_123"
        val encrypted1 = cryptoService.encrypt(plaintext, key)
        val encrypted2 = cryptoService.encrypt(plaintext, key)

        assertThat(encrypted1).isNotEqualTo(encrypted2)
        assertThat(cryptoService.decrypt(encrypted1, key)).isEqualTo(plaintext)
        assertThat(cryptoService.decrypt(encrypted2, key)).isEqualTo(plaintext)
    }

    @Test
    fun `different master passwords should produce different keys`() {
        val salt = cryptoService.generateSalt()
        val key1 = cryptoService.deriveKey("密码一", salt)
        val key2 = cryptoService.deriveKey("密码二", salt)

        val plaintext = "test"
        val encrypted1 = cryptoService.encrypt(plaintext, key1)

        // 用 key2 解密 key1 加密的数据应该失败或产生乱码
        // AES-GCM 会抛出 AEADBadTagException
        org.junit.jupiter.api.assertThrows<javax.crypto.AEADBadTagException> {
            cryptoService.decrypt(encrypted1, key2)
        }
    }
}
