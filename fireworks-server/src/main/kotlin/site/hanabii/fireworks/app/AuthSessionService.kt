package site.hanabii.fireworks.app

import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Service

/**
 * 管理 Web 登录会话中的账号 ID。
 */
@Service
class AuthSessionService {

    companion object {
        private const val ACCOUNT_ID_ATTR = "accountId"
    }

    fun storeAccountId(session: HttpSession, accountId: Long) {
        session.setAttribute(ACCOUNT_ID_ATTR, accountId)
    }

    fun getAccountId(session: HttpSession): Long? {
        return session.getAttribute(ACCOUNT_ID_ATTR) as? Long
    }

    fun isAuthenticated(session: HttpSession): Boolean {
        return getAccountId(session) != null
    }

    fun clear(session: HttpSession) {
        session.removeAttribute(ACCOUNT_ID_ATTR)
    }
}
