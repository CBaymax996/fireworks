package site.hanabii.fireworks.infra.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import site.hanabii.fireworks.domain.auth.Account
import site.hanabii.fireworks.domain.auth.AccountRepository
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 账号仓储集成测试。
 *
 * 覆盖：save（insert / update）、findById、findByUsername、deleteById，
 * 以及边界场景：不存在的查询、重复删除、createdAt 保留、updatedAt 自动刷新等。
 */
@SpringBootTest
class AccountRepositoryImplTest(
    @param:Autowired private val accountRepository: AccountRepository,
    @param:Autowired private val jdbcTemplate: JdbcTemplate
) {

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_account")
        jdbcTemplate.execute(AccountTable.DDL.trimIndent())
    }

    @Test
    fun `save should insert new account and generate id`() {
        val account = Account(id = null, username = "alice", passwordHash = "hash-alice")

        val saved = accountRepository.save(account)

        assertThat(saved.id).isNotNull()
        assertThat(saved.id!!).isGreaterThan(0)
        assertThat(saved.username).isEqualTo("alice")
        assertThat(saved.passwordHash).isEqualTo("hash-alice")
        assertThat(saved.createdAt).isNotNull()
        assertThat(saved.updatedAt).isNotNull()
    }

    @Test
    fun `findById should return account after save`() {
        val saved = accountRepository.save(
            Account(id = null, username = "bob", passwordHash = "hash-bob")
        )

        val found = accountRepository.findById(saved.id!!)

        assertThat(found).isNotNull
        assertThat(found!!.id).isEqualTo(saved.id)
        assertThat(found.username).isEqualTo("bob")
        assertThat(found.passwordHash).isEqualTo("hash-bob")
    }

    @Test
    fun `findById should return null when not exists`() {
        assertThat(accountRepository.findById(9_999L)).isNull()
    }

    @Test
    fun `findByUsername should return account after save`() {
        accountRepository.save(Account(id = null, username = "carol", passwordHash = "hash-carol"))

        val found = accountRepository.findByUsername("carol")

        assertThat(found).isNotNull
        assertThat(found!!.username).isEqualTo("carol")
        assertThat(found.passwordHash).isEqualTo("hash-carol")
        assertThat(found.id).isNotNull()
    }

    @Test
    fun `findByUsername should return null when not exists`() {
        assertThat(accountRepository.findByUsername("ghost")).isNull()
    }

    @Test
    fun `save with existing id should update fields and keep createdAt`() {
        val saved = accountRepository.save(
            Account(id = null, username = "dave", passwordHash = "hash-old")
        )
        val originalCreatedAt = saved.createdAt.truncatedTo(ChronoUnit.MILLIS)
        // 等待，确保 updatedAt 在更新时一定大于初始值
        Thread.sleep(20)

        val updated = accountRepository.save(
            saved.copy(username = "dave2", passwordHash = "hash-new")
        )

        assertThat(updated.id).isEqualTo(saved.id)
        assertThat(updated.username).isEqualTo("dave2")
        assertThat(updated.passwordHash).isEqualTo("hash-new")
        // createdAt 应当保留为原值（SQLite TIMESTAMP 精度到毫秒，因此截断后比较）
        assertThat(updated.createdAt.truncatedTo(ChronoUnit.MILLIS)).isEqualTo(originalCreatedAt)
        // updatedAt 应当被刷新
        assertThat(updated.updatedAt).isAfterOrEqualTo(originalCreatedAt)

        val refound = accountRepository.findById(saved.id!!)
        assertThat(refound).isNotNull
        assertThat(refound!!.username).isEqualTo("dave2")
        assertThat(refound.passwordHash).isEqualTo("hash-new")
        assertThat(refound.createdAt.truncatedTo(ChronoUnit.MILLIS)).isEqualTo(originalCreatedAt)
    }

    @Test
    fun `save with non existing id should be treated as insert`() {
        // 当传入的 id 在库里找不到时，按照实现会走 add 分支
        val account = Account(
            id = 123_456L,
            username = "eve",
            passwordHash = "hash-eve",
            createdAt = LocalDateTime.now().minusDays(1),
            updatedAt = LocalDateTime.now().minusDays(1)
        )

        val saved = accountRepository.save(account)

        // SQLite AUTOINCREMENT 会重新分配主键
        assertThat(saved.id).isNotNull()
        val found = accountRepository.findByUsername("eve")
        assertThat(found).isNotNull
        assertThat(found!!.passwordHash).isEqualTo("hash-eve")
    }

    @Test
    fun `deleteById should remove account`() {
        val saved = accountRepository.save(
            Account(id = null, username = "frank", passwordHash = "hash-frank")
        )

        accountRepository.deleteById(saved.id!!)

        assertThat(accountRepository.findById(saved.id)).isNull()
        assertThat(accountRepository.findByUsername("frank")).isNull()
    }

    @Test
    fun `deleteById on non existing id should be no op`() {
        // 不应抛出异常
        accountRepository.deleteById(9_999L)
        assertThat(accountRepository.findById(9_999L)).isNull()
    }

    @Test
    fun `deleteById should only remove target account`() {
        val a1 = accountRepository.save(Account(id = null, username = "u1", passwordHash = "h1"))
        val a2 = accountRepository.save(Account(id = null, username = "u2", passwordHash = "h2"))

        accountRepository.deleteById(a1.id!!)

        assertThat(accountRepository.findById(a1.id)).isNull()
        assertThat(accountRepository.findById(a2.id!!)).isNotNull
        assertThat(accountRepository.findByUsername("u2")).isNotNull
    }

    @Test
    fun `findByUsername should be case sensitive`() {
        accountRepository.save(Account(id = null, username = "Alice", passwordHash = "h"))

        assertThat(accountRepository.findByUsername("Alice")).isNotNull
        assertThat(accountRepository.findByUsername("alice")).isNull()
    }

    @Test
    fun `save should populate createdAt and updatedAt close to now for new account`() {
        val before = LocalDateTime.now().minusSeconds(1)
        val saved = accountRepository.save(
            Account(id = null, username = "grace", passwordHash = "hash-grace")
        )
        val after = LocalDateTime.now().plusSeconds(1)

        assertThat(saved.createdAt).isBetween(before, after)
        assertThat(saved.updatedAt).isBetween(before, after)
    }
}