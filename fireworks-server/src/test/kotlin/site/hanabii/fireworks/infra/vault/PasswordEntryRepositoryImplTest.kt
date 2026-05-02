package site.hanabii.fireworks.infra.vault

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import site.hanabii.fireworks.domain.vault.PasswordEntry
import site.hanabii.fireworks.domain.vault.PasswordEntryRepository
import java.time.Instant

/**
 * 密码条目仓储集成测试。
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
        val entry = PasswordEntry(website = "github.com", username = "alice", notes = "主账号")

        val saved = entryRepository.save(entry)

        assertThat(saved.id).isNotNull()
        assertThat(saved.website).isEqualTo("github.com")
        assertThat(saved.username).isEqualTo("alice")
        assertThat(saved.notes).isEqualTo("主账号")
        assertThat(saved.counter).isEqualTo(1)
        assertThat(saved.length).isEqualTo(16)
        assertThat(saved.useLowercase).isTrue()
        assertThat(saved.createdAt).isNotNull()
        assertThat(saved.updatedAt).isNotNull()
    }

    @Test
    fun `save should update existing entry`() {
        val saved = entryRepository.save(PasswordEntry(website = "a.com", username = "u"))
        val id = saved.id!!

        val updated = entryRepository.save(saved.copy(website = "b.com", username = "v", notes = "updated", length = 32))

        assertThat(updated.id).isEqualTo(id)
        assertThat(updated.website).isEqualTo("b.com")
        assertThat(updated.length).isEqualTo(32)

        val found = entryRepository.findById(id)
        assertThat(found!!.website).isEqualTo("b.com")
        assertThat(found.username).isEqualTo("v")
        assertThat(found.length).isEqualTo(32)
    }

    @Test
    fun `findById should return null for non-existent id`() {
        assertThat(entryRepository.findById(9999)).isNull()
    }

    @Test
    fun `findById should return saved entry`() {
        val saved = entryRepository.save(PasswordEntry(website = "gmail.com", username = "bob"))

        val found = entryRepository.findById(saved.id!!)

        assertThat(found).isNotNull
        assertThat(found!!.website).isEqualTo("gmail.com")
        assertThat(found.username).isEqualTo("bob")
    }

    @Test
    fun `findAll should return all entries`() {
        entryRepository.save(PasswordEntry(website = "x.com", username = "u1"))
        entryRepository.save(PasswordEntry(website = "y.com", username = "u2"))

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
        val saved = entryRepository.save(PasswordEntry(website = "z.com", username = "u"))

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

        entryRepository.save(PasswordEntry(website = "a.com", username = "u"))
        assertThat(entryRepository.count()).isEqualTo(1)

        entryRepository.save(PasswordEntry(website = "b.com", username = "u"))
        assertThat(entryRepository.count()).isEqualTo(2)
    }

    @Test
    fun `charset boolean fields should round-trip`() {
        val entry = PasswordEntry(
            website = "test.com", username = "user",
            useLowercase = true, useUppercase = false, useDigits = true, useSymbols = false
        )
        val saved = entryRepository.save(entry)

        val found = entryRepository.findById(saved.id!!)

        assertThat(found!!.useLowercase).isTrue()
        assertThat(found.useUppercase).isFalse()
        assertThat(found.useDigits).isTrue()
        assertThat(found.useSymbols).isFalse()
    }

    @Test
    fun `inserted entry should preserve createdAt`() {
        val createdAt = Instant.parse("2026-01-15T08:30:00Z")
        val entry = PasswordEntry(
            website = "test.com", username = "user",
            createdAt = createdAt, updatedAt = createdAt
        )

        val saved = entryRepository.save(entry)

        val found = entryRepository.findById(saved.id!!)
        assertThat(found!!.createdAt).isEqualTo(createdAt)
    }
}
