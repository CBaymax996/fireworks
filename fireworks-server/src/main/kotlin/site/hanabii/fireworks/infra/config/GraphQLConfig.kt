package site.hanabii.fireworks.infra.config

import com.expediagroup.graphql.generator.SchemaGenerator
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.hooks.NoopSchemaGeneratorHooks
import com.expediagroup.graphql.generator.hooks.SchemaGeneratorHooks
import graphql.Scalars
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.DataFetcherExceptionResolver
import org.springframework.graphql.execution.GraphQlSource
import site.hanabii.fireworks.app.graphql.AuthMutation
import site.hanabii.fireworks.app.graphql.AuthQuery
import site.hanabii.fireworks.app.graphql.FamilyTreeMutation
import site.hanabii.fireworks.app.graphql.FamilyTreeQuery
import site.hanabii.fireworks.app.graphql.PortfolioMutation
import site.hanabii.fireworks.app.graphql.PortfolioQuery
import site.hanabii.fireworks.app.graphql.UserMutation
import site.hanabii.fireworks.app.graphql.UserQuery
import site.hanabii.fireworks.app.graphql.VaultMutation
import site.hanabii.fireworks.app.graphql.VaultQuery
import kotlin.reflect.KType

/**
 * GraphQL 配置。
 *
 * 使用 graphql-kotlin-schema-generator 扫描 resolver bean 生成 GraphQLSchema，
 * Spring for GraphQL 自动提供 /graphql 端点并管理执行引擎。
 * HttpSession 上下文由 GraphQLSessionInterceptor 注入。
 */
@Configuration
class GraphQLConfig(
    private val exceptionResolvers: List<DataFetcherExceptionResolver>,
    // Query resolver beans
    private val authQuery: AuthQuery,
    private val vaultQuery: VaultQuery,
    private val portfolioQuery: PortfolioQuery,
    private val familyTreeQuery: FamilyTreeQuery,
    private val userQuery: UserQuery,
    // Mutation resolver beans
    private val authMutation: AuthMutation,
    private val vaultMutation: VaultMutation,
    private val portfolioMutation: PortfolioMutation,
    private val familyTreeMutation: FamilyTreeMutation,
    private val userMutation: UserMutation
) {
    /**
     * 用 schema-generator 扫描所有 resolver bean 生成 GraphQLSchema。
     * TopLevelObject 包装 resolver 实例，SchemaGenerator 自动发现其中的 Query/Mutation 方法。
     *
     * 通过 hooks 将 kotlin.Long 映射为 GraphQL Int，
     * 因为 graphql-kotlin 8.5.0 的默认 scalar 不包含 Long。
     */
    @Bean
    fun graphQLSchema(): GraphQLSchema {
        val queries = listOf(
            TopLevelObject(authQuery),
            TopLevelObject(vaultQuery),
            TopLevelObject(portfolioQuery),
            TopLevelObject(familyTreeQuery),
            TopLevelObject(userQuery)
        )
        val mutations = listOf(
            TopLevelObject(authMutation),
            TopLevelObject(vaultMutation),
            TopLevelObject(portfolioMutation),
            TopLevelObject(familyTreeMutation),
            TopLevelObject(userMutation)
        )
        val config = SchemaGeneratorConfig(
            supportedPackages = listOf("site.hanabii.fireworks"),
            hooks = object : SchemaGeneratorHooks by NoopSchemaGeneratorHooks {
                override fun willGenerateGraphQLType(type: KType): GraphQLType? {
                    return when (type.classifier) {
                        Long::class -> Scalars.GraphQLInt
                        java.time.Instant::class -> Scalars.GraphQLString
                        java.time.LocalDate::class -> Scalars.GraphQLString
                        java.time.LocalDateTime::class -> Scalars.GraphQLString
                        else -> null
                    }
                }
            }
        )
        return SchemaGenerator(config).generateSchema(queries, mutations, subscriptions = emptyList())
    }

    /**
     * 构建 GraphQlSource，注册自定义异常处理器。
     * GraphQLSchema 已包含 data fetcher 接线，直接传入 builder。
     */
    @Bean
    fun graphQlSource(schema: GraphQLSchema): GraphQlSource {
        return GraphQlSource.builder(schema)
            .exceptionResolvers(exceptionResolvers)
            .build()
    }
}
