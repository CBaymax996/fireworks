package site.hanabii.fireworks.infra

import org.ktorm.database.Database
import org.ktorm.dsl.QueryRowSet
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.update
import org.ktorm.dsl.where
import org.springframework.stereotype.Repository
import site.hanabii.fireworks.domain.User
import site.hanabii.fireworks.domain.UserRepository

/**
 * 基于 Ktorm + SQLite 的 UserRepository 实现。
 */
@Repository
class UserRepositoryImpl(
    private val database: Database
) : UserRepository {

    override fun save(user: User): User {
        return if (user.id == null) {
            database.insert(UserDO) {
                set(it.username, user.username)
                set(it.email, user.email)
                set(it.createdAt, user.createdAt)
            }
            findByUsername(user.username)
                ?: throw IllegalStateException("User insert succeeded but cannot be loaded: ${user.username}")
        } else {
            database.update(UserDO) {
                set(it.username, user.username)
                set(it.email, user.email)
                set(it.createdAt, user.createdAt)
                where { it.id eq user.id }
            }
            user
        }
    }

    override fun findById(id: Long): User? {
        return database.from(UserDO)
            .select()
            .where { UserDO.id eq id }
            .map { row: QueryRowSet -> row.toUser() }
            .firstOrNull()
    }

    override fun findByUsername(username: String): User? {
        return database.from(UserDO)
            .select()
            .where { UserDO.username eq username }
            .map { it.toUser() }
            .firstOrNull()
    }

    override fun findAll(): List<User> {
        return database.from(UserDO)
            .select()
            .map { it.toUser() }
    }

    override fun deleteById(id: Long): Boolean {
        val affected = database.delete(UserDO) { it.id eq id }
        return affected > 0
    }

    override fun count(): Long {
        return database.from(UserDO).select().totalRecordsInAllPages.toLong()
    }
}