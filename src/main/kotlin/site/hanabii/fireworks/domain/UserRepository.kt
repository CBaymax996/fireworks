package site.hanabii.fireworks.domain

/**
 * 领域层定义的 User 仓储接口，具体实现位于 infra 层。
 */
interface UserRepository {
    fun save(user: User): User
    fun findById(id: Long): User?
    fun findByUsername(username: String): User?
    fun findAll(): List<User>
    fun deleteById(id: Long): Boolean
    fun count(): Long
}