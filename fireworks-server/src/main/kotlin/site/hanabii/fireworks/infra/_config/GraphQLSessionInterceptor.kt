package site.hanabii.fireworks.infra._config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import reactor.core.publisher.Mono

/**
 * 将 HttpSession 注入 GraphQL 执行上下文。
 * 替代原 GraphQLController 中的手动 context 组装。
 */
@Component
class GraphQLSessionInterceptor : WebGraphQlInterceptor {
    override fun intercept(request: WebGraphQlRequest, chain: WebGraphQlInterceptor.Chain): Mono<WebGraphQlResponse> {
        val session = getHttpSession()

        request.configureExecutionInput { _, builder ->
            if (session != null) {
                builder.graphQLContext { it.put("session", session) }
            }
            builder.build()
        }
        return chain.next(request)
    }

    private fun getHttpSession() =
        ((RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)
            ?.request as? HttpServletRequest)
            ?.getSession(true)
}
