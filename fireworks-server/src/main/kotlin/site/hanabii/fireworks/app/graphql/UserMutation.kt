package site.hanabii.fireworks.app.graphql

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import site.hanabii.fireworks.app.AppException
import site.hanabii.fireworks.app.ErrorCode
import site.hanabii.fireworks.domain.User
import site.hanabii.fireworks.domain.UserRepository

/**
 * 用户变更 — GraphQL Mutation resolver。
 */
@Component
class UserMutation(
    private val userRepository: UserRepository
) {
    @GraphQLDescription("创建用户")
    fun createUser(username: String, email: String): User {
        if (username.isBlank() || email.isBlank()) {
            throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.BAD_REQUEST,
                message = "username and email must not be blank"
            )
        }
        return userRepository.save(User(username = username, email = email))
    }

    @GraphQLDescription("更新用户")
    fun updateUser(id: Long, username: String, email: String): User {
        if (username.isBlank() || email.isBlank()) {
            throw AppException(
                code = ErrorCode.INVALID_REQUEST,
                status = HttpStatus.BAD_REQUEST,
                message = "username and email must not be blank"
            )
        }
        val existing = userRepository.findById(id)
            ?: throw AppException(
                code = ErrorCode.USER_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "User not found: $id"
            )
        return userRepository.save(existing.copy(username = username, email = email))
    }

    @GraphQLDescription("删除用户")
    fun deleteUser(id: Long): Boolean {
        val deleted = userRepository.deleteById(id)
        if (!deleted) {
            throw AppException(
                code = ErrorCode.USER_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "User not found: $id"
            )
        }
        return true
    }
}
