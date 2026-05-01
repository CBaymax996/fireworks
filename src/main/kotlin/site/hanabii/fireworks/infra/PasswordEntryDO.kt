package site.hanabii.fireworks.infra

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import org.ktorm.schema.varchar
import java.time.Instant

/**
 * PasswordEntry 在 SQLite 中的表映射。
 * encrypted_password 列存储 AES-GCM 密文的 Base64 字符串。
 */
object PasswordEntryDO : Table<Nothing>("password_entries") {
    val id = long("id").primaryKey()
    val website = varchar("website")
    val username = varchar("username")
    val encryptedPassword = varchar("encrypted_password")
    val notes = varchar("notes")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS password_entries (
            id               INTEGER PRIMARY KEY AUTOINCREMENT,
            website          TEXT NOT NULL,
            username         TEXT NOT NULL,
            encrypted_password TEXT NOT NULL,
            notes            TEXT,
            created_at       TIMESTAMP NOT NULL,
            updated_at       TIMESTAMP NOT NULL
        )
    """
}

fun QueryRowSet.toPasswordEntry(): site.hanabii.fireworks.domain.PasswordEntry =
    site.hanabii.fireworks.domain.PasswordEntry(
        id = this[PasswordEntryDO.id],
        website = this[PasswordEntryDO.website] ?: "",
        username = this[PasswordEntryDO.username] ?: "",
        password = this[PasswordEntryDO.encryptedPassword] ?: "", // 密文，由应用层解密
        notes = this[PasswordEntryDO.notes] ?: "",
        createdAt = this[PasswordEntryDO.createdAt] ?: Instant.now(),
        updatedAt = this[PasswordEntryDO.updatedAt] ?: Instant.now()
    )
