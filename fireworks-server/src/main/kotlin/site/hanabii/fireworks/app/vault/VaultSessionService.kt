package site.hanabii.fireworks.app.vault

import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Service

/**
 * 管理密码库会话中的派生种子（seed）。
 *
 * 登录成功后，PBKDF2 派生得到的 32 字节 seed 缓存在服务端 HttpSession 中（内存存储）。
 * Session 过期或服务端重启后需重新登录。
 */
@Service
class VaultSessionService {

    companion object {
        private const val VAULT_SEED_ATTR = "vaultSeed"
    }

    fun storeSeed(session: HttpSession, seed: ByteArray) {
        session.setAttribute(VAULT_SEED_ATTR, seed.copyOf())
    }

    fun getSeed(session: HttpSession): ByteArray? {
        val bytes = session.getAttribute(VAULT_SEED_ATTR) as? ByteArray ?: return null
        return bytes.copyOf()
    }

    fun clear(session: HttpSession) {
        session.removeAttribute(VAULT_SEED_ATTR)
    }
}
