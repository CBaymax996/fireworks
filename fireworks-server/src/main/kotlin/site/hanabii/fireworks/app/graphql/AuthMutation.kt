package site.hanabii.fireworks.app.graphql

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import graphql.schema.DataFetchingEnvironment
import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import site.hanabii.fireworks.app.AppException
import site.hanabii.fireworks.app.AuthService
import site.hanabii.fireworks.app.AuthSessionService
import site.hanabii.fireworks.app.ErrorCode
import site.hanabii.fireworks.domain.auth.Account
import java.time.Instant

/**
 * 认证变更 — GraphQL Mutation resolver。
 */
@Component
class AuthMutation(
    private val authService: AuthService,
    private val authSessionService: AuthSessionService
) {
    @GraphQLDescription("注册新账号")
    fun register(username: String, password: String, dfe: DataFetchingEnvironment): Account {
        val account = authService.register(username, password)
        val session = dfe.graphQlContext.get<HttpSession>("session")
        if (session != null) {
            authSessionService.storeAccountId(session, account.id!!)
        }
        return account
    }

    @GraphQLDescription("登录")
    fun login(username: String, password: String, dfe: DataFetchingEnvironment): LoginResult {
        val account = authService.authenticate(username, password)
        if (account != null) {
            val session = dfe.graphQlContext.get<HttpSession>("session")
            if (session != null) {
                authSessionService.storeAccountId(session, account.id!!)
            }
            return LoginResult(success = true, account = account)
        }
        throw AppException(
            code = ErrorCode.INVALID_CREDENTIALS,
            status = HttpStatus.UNAUTHORIZED,
            message = "Invalid username or password"
        )
    }

    @GraphQLDescription("登出，清除当前会话")
    fun logout(dfe: DataFetchingEnvironment): Boolean {
        val session = dfe.graphQlContext.get<HttpSession>("session")
        if (session != null) {
            authSessionService.clear(session)
        }
        return true
    }
}

/** GraphQL 登录结果类型 */
data class LoginResult(
    val success: Boolean,
    val account: Account
)
