package site.hanabii.fireworks.app.graphql

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import graphql.schema.DataFetchingEnvironment
import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Component
import site.hanabii.fireworks.app.AuthSessionService

/**
 * 认证查询 — GraphQL Query resolver。
 */
@Component
class AuthQuery(
    private val authSessionService: AuthSessionService
) {
    @GraphQLDescription("查询当前认证状态")
    fun authStatus(dfe: DataFetchingEnvironment): AuthStatus {
        val session = dfe.graphQlContext.get<HttpSession>("session")
        val accountId = session?.let { authSessionService.getAccountId(it) }
        return AuthStatus(
            authenticated = accountId != null,
            accountId = accountId
        )
    }
}

/** GraphQL 认证状态类型 */
data class AuthStatus(
    val authenticated: Boolean,
    val accountId: Long?
)
