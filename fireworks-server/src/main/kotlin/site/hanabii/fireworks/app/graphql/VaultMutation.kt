package site.hanabii.fireworks.app.graphql

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import graphql.schema.DataFetchingEnvironment
import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import site.hanabii.fireworks.app.AppException
import site.hanabii.fireworks.app.ErrorCode
import site.hanabii.fireworks.app.vault.VaultService
import site.hanabii.fireworks.app.vault.VaultSessionService
import site.hanabii.fireworks.domain.vault.PasswordEntry

/**
 * 密码本变更 — GraphQL Mutation resolver。
 */
@Component
class VaultMutation(
    private val vaultService: VaultService,
    private val vaultSessionService: VaultSessionService
) {

    private fun getSession(dfe: DataFetchingEnvironment): HttpSession? =
        dfe.graphQlContext.get<HttpSession>("session")

    @GraphQLDescription("初始化密码库（设置主密码）")
    fun vaultSetup(masterPassword: String): Boolean {
        vaultService.setup(masterPassword)
        return true
    }

    @GraphQLDescription("解锁密码库（验证主密码）")
    fun vaultLogin(masterPassword: String, dfe: DataFetchingEnvironment): Boolean {
        val session = getSession(dfe)
            ?: throw AppException(
                code = ErrorCode.INTERNAL_ERROR,
                status = HttpStatus.INTERNAL_SERVER_ERROR,
                message = "No HTTP session available"
            )
        vaultService.login(masterPassword, session)
        return true
    }

    @GraphQLDescription("锁定密码库（清除会话中的派生种子）")
    fun vaultLogout(dfe: DataFetchingEnvironment): Boolean {
        val session = getSession(dfe)
        if (session != null) {
            vaultSessionService.clear(session)
        }
        return true
    }

    @GraphQLDescription("创建密码条目")
    fun createVaultEntry(input: VaultEntryInput, dfe: DataFetchingEnvironment): PasswordEntry {
        val session = getSession(dfe)
            ?: throw AppException(
                code = ErrorCode.NOT_AUTHENTICATED,
                status = HttpStatus.UNAUTHORIZED,
                message = "Not authenticated"
            )
        val entry = PasswordEntry(
            website = input.website,
            username = input.username,
            notes = input.notes ?: "",
            password = input.password,
            mode = input.mode ?: "DERIVED",
            counter = input.counter ?: 1,
            length = input.length ?: 16,
            useLowercase = input.useLowercase ?: true,
            useUppercase = input.useUppercase ?: true,
            useDigits = input.useDigits ?: true,
            useSymbols = input.useSymbols ?: true
        )
        return vaultService.addEntry(session, entry)
    }

    @GraphQLDescription("更新密码条目")
    fun updateVaultEntry(id: Long, input: VaultEntryUpdateInput, dfe: DataFetchingEnvironment): PasswordEntry {
        val session = getSession(dfe)
            ?: throw AppException(
                code = ErrorCode.NOT_AUTHENTICATED,
                status = HttpStatus.UNAUTHORIZED,
                message = "Not authenticated"
            )
        val existing = vaultService.getEntry(session, id)
            ?: throw AppException(
                code = ErrorCode.ENTRY_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "Entry not found: $id"
            )
        return vaultService.updateEntry(session, existing.copy(
            website = input.website ?: existing.website,
            username = input.username ?: existing.username,
            notes = input.notes ?: existing.notes,
            password = input.password ?: existing.password,
            mode = input.mode ?: existing.mode,
            length = input.length ?: existing.length,
            useLowercase = input.useLowercase ?: existing.useLowercase,
            useUppercase = input.useUppercase ?: existing.useUppercase,
            useDigits = input.useDigits ?: existing.useDigits,
            useSymbols = input.useSymbols ?: existing.useSymbols
        ))
    }

    @GraphQLDescription("删除密码条目")
    fun deleteVaultEntry(id: Long, dfe: DataFetchingEnvironment): Boolean {
        val session = getSession(dfe)
            ?: throw AppException(
                code = ErrorCode.NOT_AUTHENTICATED,
                status = HttpStatus.UNAUTHORIZED,
                message = "Not authenticated"
            )
        vaultService.requireAuth(session)
        return vaultService.deleteEntry(id)
    }

    @GraphQLDescription("轮换密码条目（counter +1，仅 DERIVED 模式支持）")
    fun rotateVaultEntry(id: Long, dfe: DataFetchingEnvironment): PasswordEntry {
        val session = getSession(dfe)
            ?: throw AppException(
                code = ErrorCode.NOT_AUTHENTICATED,
                status = HttpStatus.UNAUTHORIZED,
                message = "Not authenticated"
            )
        return vaultService.rotateEntry(session, id)
    }
}

/** GraphQL 密码条目创建输入 */
data class VaultEntryInput(
    val website: String,
    val username: String,
    val notes: String? = null,
    val password: String? = null,
    val mode: String? = null,
    val counter: Int? = null,
    val length: Int? = null,
    val useLowercase: Boolean? = null,
    val useUppercase: Boolean? = null,
    val useDigits: Boolean? = null,
    val useSymbols: Boolean? = null
)

/** GraphQL 密码条目更新输入 */
data class VaultEntryUpdateInput(
    val website: String? = null,
    val username: String? = null,
    val notes: String? = null,
    val password: String? = null,
    val mode: String? = null,
    val length: Int? = null,
    val useLowercase: Boolean? = null,
    val useUppercase: Boolean? = null,
    val useDigits: Boolean? = null,
    val useSymbols: Boolean? = null
)
