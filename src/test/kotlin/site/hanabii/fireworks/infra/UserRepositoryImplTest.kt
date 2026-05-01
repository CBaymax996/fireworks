package site.hanabii.fireworks.infra

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import site.hanabii.fireworks.domain.User
import site.hanabii.fireworks.domain.UserRepository
import java.time.Instant

/**
 * 用户仓储集成测试。
 *
 * 覆盖：save（insert / update）、findById、findAll、count、deleteById。
 */
@SpringBootTest
class UserRepositoryImplTest(
    @param:Autowired private val userRepository: UserRepository,
    @param:Autowired private val jdbcTemplate: JdbcTemplate
) {

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS users")
        jdbcTemplate.execute(UserDO.DDL.trimIndent())
    }

    @Test
    fun `save and findById should persist user`() {
        val createdAt = Instant.parse("2026-01-01T00:00:00Z")
        val saved = userRepository.save(
            User(username = "alice", email = "alice@example.com", createdAt = createdAt)
        )

        assertThat(saved.id).isNotNull()

        val found = userRepository.findById(saved.id!!)
        assertThat(found).isNotNull
        assertThat(found!!.username).isEqualTo("alice")
        assertThat(found.email).isEqualTo("alice@example.com")
        assertThat(found.createdAt).isEqualTo(createdAt)
    }

    @Test
    fun `save with existing id should update user`() {
        val saved = userRepository.save(User(username = "bob", email = "bob@example.com"))
        val updated = userRepository.save(saved.copy(username = "bobby", email = "bobby@example.com"))

        assertThat(updated.id).isEqualTo(saved.id)

        val found = userRepository.findById(saved.id!!)
        assertThat(found).isNotNull
        assertThat(found!!.username).isEqualTo("bobby")
        assertThat(found.email).isEqualTo("bobby@example.com")
    }

    @Test
    fun `findAll count and deleteById should work`() {
        val u1 = userRepository.save(User(username = "charlie", email = "charlie@example.com"))
        userRepository.save(User(username = "david", email = "david@example.com"))

        assertThat(userRepository.count()).isEqualTo(2)
        assertThat(userRepository.findAll()).hasSize(2)

        assertThat(userRepository.deleteById(u1.id!!)).isTrue
        assertThat(userRepository.deleteById(u1.id)).isFalse
        assertThat(userRepository.count()).isEqualTo(1)
    }
}