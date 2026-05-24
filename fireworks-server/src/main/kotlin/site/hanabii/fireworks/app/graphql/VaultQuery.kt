package site.hanabii.fireworks.app.graphql

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import graphql.schema.DataFetchingEnvironment
import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Component
import site.hanabii.fireworks.app.vault.VaultService
import site.hanabii.fireworks.app.vault.VaultSessionService
import site.hanabii.fireworks.domain.vault.DerivedPassword
import site.hanabii.fireworks.domain.vault.PasswordEntry
import java.security.SecureRandom

/**
 * 密码本查询 — GraphQL Query resolver。
 */
@Component
class VaultQuery(
    private val vaultService: VaultService,
    private val vaultSessionService: VaultSessionService
) {

    /** 从 DataFetchingEnvironment 中提取会话 seed 是否存在 */
    private fun getSession(dfe: DataFetchingEnvironment): HttpSession? =
        dfe.graphQlContext.get<HttpSession>("session")

    @GraphQLDescription("查询密码库状态（是否已初始化、是否已解锁）")
    fun vaultStatus(dfe: DataFetchingEnvironment): VaultStatus {
        val session = getSession(dfe)
        return VaultStatus(
            initialized = vaultService.isInitialized(),
            authenticated = session?.let { vaultSessionService.getSeed(it) != null } ?: false
        )
    }

    @GraphQLDescription("列出所有密码条目（不含密码明文，STORED 模式会解密）")
    fun vaultEntries(dfe: DataFetchingEnvironment): List<PasswordEntry> {
        val session = getSession(dfe) ?: throw site.hanabii.fireworks.app.AppException(
            code = site.hanabii.fireworks.app.ErrorCode.NOT_AUTHENTICATED,
            status = org.springframework.http.HttpStatus.UNAUTHORIZED,
            message = "Not authenticated"
        )
        return vaultService.listEntries(session)
    }

    @GraphQLDescription("获取单条密码条目")
    fun vaultEntry(id: Long, dfe: DataFetchingEnvironment): PasswordEntry? {
        val session = getSession(dfe) ?: throw site.hanabii.fireworks.app.AppException(
            code = site.hanabii.fireworks.app.ErrorCode.NOT_AUTHENTICATED,
            status = org.springframework.http.HttpStatus.UNAUTHORIZED,
            message = "Not authenticated"
        )
        return vaultService.getEntry(session, id)
    }

    @GraphQLDescription("派生密码（支持历史 counter）")
    fun derivePassword(id: Long, counter: Int?, dfe: DataFetchingEnvironment): DerivedPassword {
        val session = getSession(dfe) ?: throw site.hanabii.fireworks.app.AppException(
            code = site.hanabii.fireworks.app.ErrorCode.NOT_AUTHENTICATED,
            status = org.springframework.http.HttpStatus.UNAUTHORIZED,
            message = "Not authenticated"
        )
        return vaultService.derivePassword(session, id, counter)
    }

    @GraphQLDescription("生成随机密码（基于指定字符集和长度）")
    fun generatePassword(
        length: Int = 16,
        lowercase: Boolean = true,
        uppercase: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true
    ): GeneratedPassword {
        require(length in 8..64) { "length must be 8..64, got $length" }
        require(lowercase || uppercase || digits || symbols) { "至少需要开启一个字符集" }

        val charset = buildString {
            if (lowercase) append("abcdefghijklmnopqrstuvwxyz")
            if (uppercase) append("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            if (digits) append("0123456789")
            if (symbols) append("!@#\$%^&*()-_=+[]{};:,.<>?")
        }
        val random = SecureRandom()
        val password = (1..length).map { charset[random.nextInt(charset.length)] }.joinToString("")
        return GeneratedPassword(password = password)
    }
}

/** GraphQL 密码库状态类型 */
data class VaultStatus(
    val initialized: Boolean,
    val authenticated: Boolean
)

/** GraphQL 随机密码生成结果 */
data class GeneratedPassword(
    val password: String
)
