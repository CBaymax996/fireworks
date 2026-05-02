package site.hanabii.fireworks.infra

import org.ktorm.database.Database
import org.ktorm.dsl.QueryRowSet
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import org.springframework.stereotype.Repository
import site.hanabii.fireworks.domain.Account
import site.hanabii.fireworks.domain.AccountRepository

/**
 * 基于 Ktorm + SQLite 的 AccountRepository 实现。
 */
@Repository
class AccountRepositoryImpl(
    private val database: Database
) : AccountRepository {

    override fun save(account: Account): Account {
        return if (account.id == null) {
            database.insert(AccountDO) {
                set(it.username, account.username)
                set(it.passwordHash, account.passwordHash)
                set(it.createdAt, account.createdAt)
            }
            findByUsername(account.username)
                ?: throw IllegalStateException("Account insert succeeded but cannot be loaded: ${account.username}")
        } else {
            throw UnsupportedOperationException("Account update is not supported")
        }
    }

    override fun findById(id: Long): Account? {
        return database.from(AccountDO)
            .select()
            .where { AccountDO.id eq id }
            .map { row: QueryRowSet -> row.toAccount() }
            .firstOrNull()
    }

    override fun findByUsername(username: String): Account? {
        return database.from(AccountDO)
            .select()
            .where { AccountDO.username eq username }
            .map { it.toAccount() }
            .firstOrNull()
    }

    override fun existsByUsername(username: String): Boolean {
        return findByUsername(username) != null
    }

    override fun deleteById(id: Long): Boolean {
        val affected = database.delete(AccountDO) { it.id eq id }
        return affected > 0
    }
}
