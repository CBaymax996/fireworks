package site.hanabii.fireworks.domain

import org.ktorm.entity.Entity
import org.ktorm.schema.Table
import org.ktorm.schema.datetime
import org.ktorm.schema.int
import org.ktorm.schema.varchar
import java.time.LocalDateTime

interface User : Entity<User> {
    companion object : Entity.Factory<User>()
    var id: Int
    var name: String
    var email: String
    var createdAt: LocalDateTime
}

object Users : Table<User>("users") {
    val id = int("id").primaryKey().bindTo { it.id }
    val name = varchar("name").bindTo { it.name }
    val email = varchar("email").bindTo { it.email }
    val createdAt = datetime("created_at").bindTo { it.createdAt }
}
