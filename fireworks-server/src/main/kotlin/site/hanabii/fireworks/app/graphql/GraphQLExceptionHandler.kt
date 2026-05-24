package site.hanabii.fireworks.app.graphql

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolver
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import site.hanabii.fireworks.app.AppException

/**
 * GraphQL 全局异常处理器。
 * 将业务层 AppException 映射为 GraphQL errors 返回给客户端，
 * 在 errors extensions 中包含 code 和 HTTP status。
 */
@Component
class GraphQLExceptionHandler : DataFetcherExceptionResolver {

    override fun resolveException(exception: Throwable, env: DataFetchingEnvironment): Mono<List<GraphQLError>> {
        val error: GraphQLError = when (exception) {
            is AppException -> {
                GraphqlErrorBuilder.newError()
                    .message(exception.message ?: "Internal error")
                    .errorType(graphql.ErrorType.DataFetchingException)
                    .extensions(
                        mapOf(
                            "code" to exception.code.name,
                            "status" to exception.status.value()
                        )
                    )
                    .build()
            }
            is IllegalArgumentException -> {
                GraphqlErrorBuilder.newError()
                    .message(exception.message ?: "Invalid request")
                    .errorType(graphql.ErrorType.ValidationError)
                    .extensions(mapOf("code" to "INVALID_REQUEST", "status" to 400))
                    .build()
            }
            else -> {
                GraphqlErrorBuilder.newError()
                    .message(exception.message ?: "Internal server error")
                    .errorType(graphql.ErrorType.DataFetchingException)
                    .build()
            }
        }
        return Mono.just(listOf(error))
    }
}
