package site.hanabii.fireworks.app.graphql

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import org.springframework.stereotype.Component
import site.hanabii.fireworks.domain.User
import site.hanabii.fireworks.domain.UserRepository

/**
 * 用户查询 — GraphQL Query resolver。
 */
@Component
class UserQuery(
    private val userRepository: UserRepository
) {
    @GraphQLDescription("列出所有用户")
    fun users(): List<User> = userRepository.findAll()

    @GraphQLDescription("获取单个用户")
    fun user(id: Long): User? = userRepository.findById(id)
}
