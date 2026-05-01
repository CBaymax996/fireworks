package site.hanabii.fireworks.app

import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Service
import site.hanabii.fireworks.app.crypto.VaultCryptoService
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * 管理密码库会话中的派生密钥。
 *
 * 登录成功后，AES 密钥缓存在服务端 HttpSession 中（内存存储）。
 * Session 过期或服务端重启后需重新登录。
 */
@Service
class VaultSessionService(
    private val cryptoService: VaultCryptoService
) {

    companion object {
        private const val VAULT_KEY_ATTR = "vaultKey"
    }

    fun storeKey(session: HttpSession, masterPassword: String, salt: ByteArray) {
        val key = cryptoService.deriveKey(masterPassword, salt)
        session.setAttribute(VAULT_KEY_ATTR, key.encoded)
    }

    fun getKey(session: HttpSession): SecretKey? {
        val bytes = session.getAttribute(VAULT_KEY_ATTR) as? ByteArray ?: return null
        return SecretKeySpec(bytes, "AES")
    }

    fun clear(session: HttpSession) {
        session.removeAttribute(VAULT_KEY_ATTR)
    }
}
