package site.hanabii.fireworks.infra.auth

import org.ktorm.entity.Entity
import org.ktorm.schema.*
import java.time.LocalDateTime

/**
 * table define
 */


/**
 * Account PO define
 */

interface AccountPO : Entity<AccountPO> {
    companion object : Entity.Factory<AccountPO>()

    var id: Long
    var username: String
    var passwordHash: String
    var createdAt: LocalDateTime
    var updatedAt: LocalDateTime

}


@Suppress("UNUSED")
object AccountTable : Table<AccountPO>("t_account") {
    val id = long("id").primaryKey().bindTo { it.id }
    val username = varchar("username").bindTo { it.username }
    val passwordHash = varchar("password_hash").bindTo { it.passwordHash }
    val createdAt = datetime("created_at").bindTo { it.createdAt }
    val updatedAt = datetime("updated_at").bindTo { it.updatedAt }


    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS t_account (
            id            INTEGER PRIMARY KEY AUTOINCREMENT,
            username      TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            created_at    TIMESTAMP NOT NULL,
            updated_at    TIMESTAMP NOT NULL
        )
    """
}
