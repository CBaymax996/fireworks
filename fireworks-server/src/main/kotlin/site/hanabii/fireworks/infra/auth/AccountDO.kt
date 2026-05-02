package site.hanabii.fireworks.infra.auth

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import org.ktorm.schema.varchar
import site.hanabii.fireworks.domain.auth.Account
import java.time.Instant

/**
 * Account 在 SQLite 中的表映射 (DO)。
 */
object AccountDO : Table<Nothing>("accounts") {
    val id = long("id").primaryKey()
    val username = varchar("username")
    val passwordHash = varchar("password_hash")
    val createdAt = timestamp("created_at")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS accounts (
            id            INTEGER PRIMARY KEY AUTOINCREMENT,
            username      TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            created_at    TIMESTAMP NOT NULL
        )
    """
}

fun QueryRowSet.toAccount(): Account = Account(
    id = this[AccountDO.id],
    username = this[AccountDO.username] ?: "",
    passwordHash = this[AccountDO.passwordHash] ?: "",
    createdAt = this[AccountDO.createdAt] ?: Instant.now()
)
