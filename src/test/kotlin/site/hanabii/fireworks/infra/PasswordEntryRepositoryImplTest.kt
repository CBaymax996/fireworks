package site.hanabii.fireworks.infra

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import site.hanabii.fireworks.domain.PasswordEntry
import site.hanabii.fireworks.domain.PasswordEntryRepository
import java.time.Instant

/**
 * 密码条目仓储集成测试。
 *
 * 覆盖：save（insert / update）、findById、findAll、deleteById、count、timestamp 保留。
 */
@SpringBootTest
class PasswordEntryRepositoryImplTest(
    @param:Autowired private val entryRepository: PasswordEntryRepository,
    @param:Autowired private val jdbcTemplate: JdbcTemplate
) {

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS password_entries")
        jdbcTemplate.execute(PasswordEntryDO.DDL.trimIndent())
    }

    @Test
    fun `save should insert new entry with auto-generated id`() {
        val entry = PasswordEntry(website = "github.com", username = "alice", password = "encrypted_gh", notes = "主账号")

        val saved = entryRepository.save(entry)

        assertThat(saved.id).isNotNull()
        assertThat(saved.website).isEqualTo("github.com")
        assertThat(saved.username).isEqualTo("alice")
        assertThat(saved.password).isEqualTo("encrypted_gh")
        assertThat(saved.notes).isEqualTo("主账号")
        assertThat(saved.createdAt).isNotNull()
        assertThat(saved.updatedAt).isNotNull()
    }

    @Test
    fun `save should update existing entry`() {
        val saved = entryRepository.save(PasswordEntry(website = "a.com", username = "u", password = "p"))
        val id = saved.id!!

        val updated = entryRepository.save(saved.copy(website = "b.com", username = "v", password = "q", notes = "updated"))

        assertThat(updated.id).isEqualTo(id)
        assertThat(updated.website).isEqualTo("b.com")
        assertThat(updated.password).isEqualTo("q")

        val found = entryRepository.findById(id)
        assertThat(found!!.website).isEqualTo("b.com")
        assertThat(found.username).isEqualTo("v")
    }

    @Test
    fun `findById should return null for non-existent id`() {
        assertThat(entryRepository.findById(9999)).isNull()
    }

    @Test
    fun `findById should return saved entry`() {
        val saved = entryRepository.save(PasswordEntry(website = "gmail.com", username = "bob", password = "enc_gm"))

        val found = entryRepository.findById(saved.id!!)

        assertThat(found).isNotNull
        assertThat(found!!.website).isEqualTo("gmail.com")
        assertThat(found.username).isEqualTo("bob")
    }

    @Test
    fun `findAll should return all entries`() {
        entryRepository.save(PasswordEntry(website = "x.com", username = "u1", password = "p1"))
        entryRepository.save(PasswordEntry(website = "y.com", username = "u2", password = "p2"))

        val all = entryRepository.findAll()

        assertThat(all).hasSize(2)
        assertThat(all.map { it.website }).containsExactlyInAnyOrder("x.com", "y.com")
    }

    @Test
    fun `findAll should return empty list when no entries`() {
        assertThat(entryRepository.findAll()).isEmpty()
    }

    @Test
    fun `deleteById should remove entry and return true`() {
        val saved = entryRepository.save(PasswordEntry(website = "z.com", username = "u", password = "p"))

        val deleted = entryRepository.deleteById(saved.id!!)

        assertThat(deleted).isTrue()
        assertThat(entryRepository.findById(saved.id!!)).isNull()
    }

    @Test
    fun `deleteById should return false for non-existent id`() {
        assertThat(entryRepository.deleteById(9999)).isFalse()
    }

    @Test
    fun `count should reflect number of entries`() {
        assertThat(entryRepository.count()).isEqualTo(0)

        entryRepository.save(PasswordEntry(website = "a.com", username = "u", password = "p"))
        assertThat(entryRepository.count()).isEqualTo(1)

        entryRepository.save(PasswordEntry(website = "b.com", username = "u", password = "p"))
        assertThat(entryRepository.count()).isEqualTo(2)
    }

    @Test
    fun `inserted entry should preserve createdAt and updatedAt`() {
        val createdAt = Instant.parse("2026-01-15T08:30:00Z")
        val entry = PasswordEntry(
            website = "test.com",
            username = "user",
            password = "enc",
            createdAt = createdAt,
            updatedAt = createdAt
        )

        val saved = entryRepository.save(entry)

        val found = entryRepository.findById(saved.id!!)
        assertThat(found!!.createdAt).isEqualTo(createdAt)
    }
}
