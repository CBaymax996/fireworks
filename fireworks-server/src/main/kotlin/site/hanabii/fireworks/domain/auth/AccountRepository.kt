package site.hanabii.fireworks.domain.auth

/**
 * 账号仓储接口。
 */
interface AccountRepository {
    fun save(account: Account): Account
    fun findById(id: Long): Account?
    fun findByUsername(username: String): Account?
    fun existsByUsername(username: String): Boolean
    fun deleteById(id: Long): Boolean
}
