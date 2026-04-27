package site.hanabii.fireworks.infra

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import org.ktorm.schema.varchar
import java.time.Instant

/**
 * User 在 SQLite 中的表映射 (DO - Data Object)。
 * 负责 domain.User 与数据库行之间的映射。
 */
object UserDO : Table<Nothing>("users") {
    val id = long("id").primaryKey()
    val username = varchar("username")
    val email = varchar("email")
    val createdAt = timestamp("created_at")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS users (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            username   TEXT NOT NULL UNIQUE,
            email      TEXT NOT NULL,
            created_at TIMESTAMP NOT NULL
        )
    """
}

/**
 * 将 Ktorm QueryRowSet 转换为 domain.User。
 */
fun QueryRowSet.toUser(): site.hanabii.fireworks.domain.User = site.hanabii.fireworks.domain.User(
    id = this[UserDO.id],
    username = this[UserDO.username] ?: "",
    email = this[UserDO.email] ?: "",
    createdAt = this[UserDO.createdAt] ?: Instant.now()
)