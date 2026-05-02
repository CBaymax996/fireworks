package site.hanabii.fireworks.infra.vault

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import org.ktorm.schema.varchar
import site.hanabii.fireworks.domain.vault.VaultConfig
import java.time.Instant
import java.util.Base64

/**
 * VaultConfig 在 SQLite 中的表映射。
 * encryption_salt 以 Base64 TEXT 存储，避免 SQLite JDBC 不支持 setBlob。
 */
object VaultConfigDO : Table<Nothing>("vault_config") {
    val id = long("id").primaryKey()
    val passwordHash = varchar("password_hash")
    val encryptionSalt = varchar("encryption_salt")
    val createdAt = timestamp("created_at")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS vault_config (
            id              INTEGER PRIMARY KEY CHECK (id = 1),
            password_hash   TEXT NOT NULL,
            encryption_salt TEXT NOT NULL,
            created_at      TIMESTAMP NOT NULL
        )
    """
}

private val base64Decoder = Base64.getDecoder()

fun QueryRowSet.toVaultConfig(): VaultConfig = VaultConfig(
    id = this[VaultConfigDO.id] ?: 1,
    passwordHash = this[VaultConfigDO.passwordHash] ?: "",
    encryptionSalt = this[VaultConfigDO.encryptionSalt]?.let { base64Decoder.decode(it) } ?: ByteArray(0),
    createdAt = this[VaultConfigDO.createdAt] ?: Instant.now()
)
