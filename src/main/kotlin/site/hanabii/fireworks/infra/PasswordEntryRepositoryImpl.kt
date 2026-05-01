package site.hanabii.fireworks.infra

import org.ktorm.database.Database
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.update
import org.ktorm.dsl.where
import org.springframework.stereotype.Repository
import site.hanabii.fireworks.domain.PasswordEntry
import site.hanabii.fireworks.domain.PasswordEntryRepository
import java.time.Instant

/**
 * 密码条目仓储实现。
 *
 * 使用 Ktorm DSL 操作 SQLite，save 区分 insert / update：
 * - id 为 null 时插入新记录，通过 last_insert_rowid() 获取自增主键后回查完整数据
 * - id 不为 null 时更新已有记录
 */
@Repository
class PasswordEntryRepositoryImpl(
    private val database: Database
) : PasswordEntryRepository {

    override fun save(entry: PasswordEntry): PasswordEntry {
        val now = Instant.now()
        return if (entry.id == null) {
            database.insert(PasswordEntryDO) {
                set(it.website, entry.website)
                set(it.username, entry.username)
                set(it.encryptedPassword, entry.password)
                set(it.notes, entry.notes)
                set(it.createdAt, entry.createdAt)
                set(it.updatedAt, now)
            }
            val lastId = database.useConnection { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT last_insert_rowid()").use { rs ->
                        if (rs.next()) rs.getLong(1) else throw IllegalStateException("Failed to get last insert rowid")
                    }
                }
            }
            database.from(PasswordEntryDO)
                .select()
                .where { PasswordEntryDO.id eq lastId }
                .map { it.toPasswordEntry() }
                .firstOrNull()
                ?: throw IllegalStateException("PasswordEntry insert succeeded but cannot be loaded")
        } else {
            database.update(PasswordEntryDO) {
                set(it.website, entry.website)
                set(it.username, entry.username)
                set(it.encryptedPassword, entry.password)
                set(it.notes, entry.notes)
                set(it.updatedAt, now)
                where { it.id eq entry.id }
            }
            entry.copy(updatedAt = now)
        }
    }

    override fun findById(id: Long): PasswordEntry? {
        return database.from(PasswordEntryDO)
            .select()
            .where { PasswordEntryDO.id eq id }
            .map { it.toPasswordEntry() }
            .firstOrNull()
    }

    override fun findAll(): List<PasswordEntry> {
        return database.from(PasswordEntryDO)
            .select()
            .map { it.toPasswordEntry() }
    }

    override fun deleteById(id: Long): Boolean {
        val affected = database.delete(PasswordEntryDO) { it.id eq id }
        return affected > 0
    }

    override fun count(): Long {
        return database.from(PasswordEntryDO).select().totalRecordsInAllPages.toLong()
    }
}
