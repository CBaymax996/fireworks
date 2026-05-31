package site.hanabii.fireworks.domain.auth

/**
 * 账号仓储接口。
 */
interface AccountRepository {

    fun findById(id: Long): Account?
    fun findByUsername(username: String): Account?


    fun save(account: Account): Account
    fun deleteById(id: Long)
}
