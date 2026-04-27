package site.hanabii.fireworks.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.ktorm.database.Database
import org.ktorm.dsl.deleteAll
import org.ktorm.support.sqlite.SQLiteDialect
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.sqlite.SQLiteDataSource
import site.hanabii.fireworks.domain.Users
import javax.sql.DataSource

@SpringBootTest
class UserServiceTest {

    @TestConfiguration
    class TestDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource {
            return SQLiteDataSource().apply {
                url = "jdbc:sqlite:fireworks-test.db"
            }
        }

        @Bean
        @Primary
        fun database(dataSource: DataSource): Database {
            return Database.connect(dataSource, dialect = SQLiteDialect()).apply {
                useConnection { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute(
                            """
                            CREATE TABLE IF NOT EXISTS users (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                name TEXT NOT NULL,
                                email TEXT NOT NULL UNIQUE,
                                created_at TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                    }
                }
            }
        }
    }

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var database: Database

    @BeforeEach
    fun cleanUp() {
        database.deleteAll(Users)
    }

    @Test
    fun `create should return user with generated id`() {
        val user = userService.create("Alice", "alice@example.com")

        assertNotNull(user.id)
        assertEquals("Alice", user.name)
        assertEquals("alice@example.com", user.email)
        assertNotNull(user.createdAt)
    }

    @Test
    fun `findById should return existing user`() {
        val created = userService.create("Bob", "bob@example.com")

        val found = userService.findById(created.id)

        assertNotNull(found)
        assertEquals("Bob", found!!.name)
    }

    @Test
    fun `findById should return null for non-existing user`() {
        val found = userService.findById(999)

        assertNull(found)
    }

    @Test
    fun `list should return all users`() {
        userService.create("User1", "user1@example.com")
        userService.create("User2", "user2@example.com")

        val list = userService.list()

        assertEquals(2, list.size)
        val emails = list.map { it.email }
        assertTrue(emails.contains("user1@example.com"))
        assertTrue(emails.contains("user2@example.com"))
    }
}
