package site.hanabii.fireworks.infra.auth

import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.entity.*
import org.springframework.stereotype.Repository
import site.hanabii.fireworks.domain.auth.Account
import site.hanabii.fireworks.domain.auth.AccountRepository
import java.time.LocalDateTime

/**
 * 基于 Ktorm + SQLite 的 AccountRepository 实现。
 */
@Repository
class AccountRepositoryImpl(private val database: Database) : AccountRepository {

    val Database.accounts get() = this.sequenceOf(AccountTable)


    override fun save(account: Account): Account {

        val old: AccountPO? = account.id?.let { id ->
            database.accounts.find { it.id eq id }
        }

        val accountPO = account.toPO(old)


        if (old == null) {
            database.accounts.add(accountPO)
        } else {
            database.accounts.update(accountPO)
        }

        return accountPO.toEntity()

    }

    override fun findById(id: Long): Account? {
        return database.accounts.find { it.id eq id }?.toEntity()
    }

    override fun findByUsername(username: String): Account? {
        return database.accounts.find { it.username eq username }?.toEntity()
    }


    override fun deleteById(id: Long) {
        database.accounts.find { it.id eq id }?.delete()
    }


}

/**
 * 扩展函数
 */
fun AccountPO.toEntity(): Account = Account(
    id = this.id,
    username = this.username,
    passwordHash = this.passwordHash,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt
)


fun Account.toPO(old: AccountPO?): AccountPO {
    val accountPO: AccountPO = old ?: AccountPO {
        createdAt = LocalDateTime.now()
    }
    accountPO.username = this.username
    accountPO.passwordHash = this.passwordHash
    accountPO.updatedAt = LocalDateTime.now()
    return accountPO
}
