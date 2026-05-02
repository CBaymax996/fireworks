package site.hanabii.fireworks.infra.vault

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import org.ktorm.schema.varchar
import site.hanabii.fireworks.domain.vault.PasswordEntry
import java.time.Instant

/**
 * PasswordEntry 在 SQLite 中的表映射。
 * 派生生成路线：不存密码，只存元数据与生成规则。
 */
object PasswordEntryDO : Table<Nothing>("password_entries") {
    val id = long("id").primaryKey()
    val website = varchar("website")
    val username = varchar("username")
    val notes = varchar("notes")
    val counter = int("counter")
    val length = int("length")
    val useLowercase = int("use_lowercase")
    val useUppercase = int("use_uppercase")
    val useDigits = int("use_digits")
    val useSymbols = int("use_symbols")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS password_entries (
            id            INTEGER PRIMARY KEY AUTOINCREMENT,
            website       TEXT    NOT NULL,
            username      TEXT    NOT NULL,
            notes         TEXT,
            counter       INTEGER NOT NULL DEFAULT 1,
            length        INTEGER NOT NULL DEFAULT 16,
            use_lowercase INTEGER NOT NULL DEFAULT 1,
            use_uppercase INTEGER NOT NULL DEFAULT 1,
            use_digits    INTEGER NOT NULL DEFAULT 1,
            use_symbols   INTEGER NOT NULL DEFAULT 1,
            created_at    TIMESTAMP NOT NULL,
            updated_at    TIMESTAMP NOT NULL
        )
    """
}

fun QueryRowSet.toPasswordEntry(): PasswordEntry = PasswordEntry(
    id = this[PasswordEntryDO.id],
    website = this[PasswordEntryDO.website] ?: "",
    username = this[PasswordEntryDO.username] ?: "",
    notes = this[PasswordEntryDO.notes] ?: "",
    counter = this[PasswordEntryDO.counter] ?: 1,
    length = this[PasswordEntryDO.length] ?: 16,
    useLowercase = this[PasswordEntryDO.useLowercase] == 1,
    useUppercase = this[PasswordEntryDO.useUppercase] == 1,
    useDigits = this[PasswordEntryDO.useDigits] == 1,
    useSymbols = this[PasswordEntryDO.useSymbols] == 1,
    createdAt = this[PasswordEntryDO.createdAt] ?: Instant.now(),
    updatedAt = this[PasswordEntryDO.updatedAt] ?: Instant.now()
)
