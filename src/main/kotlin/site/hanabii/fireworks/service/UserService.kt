package site.hanabii.fireworks.service

import org.ktorm.database.Database
import org.ktorm.dsl.eq
import org.ktorm.entity.add
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.toList
import org.springframework.stereotype.Service
import site.hanabii.fireworks.domain.User
import site.hanabii.fireworks.domain.Users
import java.time.LocalDateTime

@Service
class UserService(private val database: Database) {

    private val Database.users get() = this.sequenceOf(Users)

    fun list(): List<User> = database.users.toList()

    fun findById(id: Int): User? = database.users.find { it.id eq id }

    fun create(name: String, email: String): User {
        val user = User {
            this.name = name
            this.email = email
            this.createdAt = LocalDateTime.now()
        }
        database.users.add(user)
        return user
    }
}
