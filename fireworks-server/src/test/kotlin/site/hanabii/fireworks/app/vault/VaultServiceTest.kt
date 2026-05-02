package site.hanabii.fireworks.app.vault

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import site.hanabii.fireworks.app.AppException
import site.hanabii.fireworks.app.ErrorCode
import site.hanabii.fireworks.domain.vault.PasswordEntry
import site.hanabii.fireworks.infra.vault.PasswordEntryDO
import site.hanabii.fireworks.infra.vault.VaultConfigDO

/**
 * 密码库业务服务集成测试。
 */
@SpringBootTest
class VaultServiceTest(
    @param:Autowired private val vaultService: VaultService,
    @param:Autowired private val jdbcTemplate: JdbcTemplate
) {

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS password_entries")
        jdbcTemplate.execute("DROP TABLE IF EXISTS vault_config")
        jdbcTemplate.execute(VaultConfigDO.DDL.trimIndent())
        jdbcTemplate.execute(PasswordEntryDO.DDL.trimIndent())
    }

    @Test
    fun `setup with Chinese master password should initialize vault`() {
        val masterPassword = "我爱北京天安门"

        assertThat(vaultService.isInitialized()).isFalse()
        vaultService.setup(masterPassword)
        assertThat(vaultService.isInitialized()).isTrue()
    }

    @Test
    fun `setup twice should throw AppException`() {
        vaultService.setup("密码")

        val ex = assertThrows<AppException> {
            vaultService.setup("密码2")
        }
        assertThat(ex.code).isEqualTo(ErrorCode.INVALID_REQUEST)
        assertThat(ex.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `login should succeed with correct password and store seed`() {
        val masterPassword = "正确的密码"
        vaultService.setup(masterPassword)
        val session = MockHttpSession()

        vaultService.login(masterPassword, session)
    }

    @Test
    fun `login should fail with wrong password`() {
        vaultService.setup("正确的密码")
        val session = MockHttpSession()

        val ex = assertThrows<AppException> {
            vaultService.login("错误的密码", session)
        }
        assertThat(ex.code).isEqualTo(ErrorCode.INVALID_CREDENTIALS)
        assertThat(ex.status).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `login should fail when vault not initialized`() {
        val session = MockHttpSession()

        val ex = assertThrows<AppException> {
            vaultService.login("密码", session)
        }
        assertThat(ex.code).isEqualTo(ErrorCode.VAULT_NOT_INITIALIZED)
    }

    @Test
    fun `listEntries should throw when not authenticated`() {
        val session = MockHttpSession()

        val ex = assertThrows<AppException> {
            vaultService.requireAuth(session)
        }
        assertThat(ex.code).isEqualTo(ErrorCode.VAULT_NOT_INITIALIZED)
    }

    @Test
    fun `addEntry and listEntries should work without encryption`() {
        vaultService.setup("主密码")
        val session = MockHttpSession()
        vaultService.login("主密码", session)

        val entry1 = vaultService.addEntry(PasswordEntry(website = "github.com", username = "alice"))
        assertThat(entry1.id).isNotNull()
        assertThat(entry1.counter).isEqualTo(1)
        assertThat(entry1.length).isEqualTo(16)

        val entry2 = vaultService.addEntry(
            PasswordEntry(website = "gmail.com", username = "bob", length = 32, useDigits = false)
        )

        val list = vaultService.listEntries()
        assertThat(list).hasSize(2)
        assertThat(list.map { it.website }).containsExactlyInAnyOrder("github.com", "gmail.com")
    }

    @Test
    fun `getEntry and updateEntry should work`() {
        vaultService.setup("主密码")
        val session = MockHttpSession()
        vaultService.login("主密码", session)

        val saved = vaultService.addEntry(PasswordEntry(website = "old.com", username = "u"))
        val id = saved.id!!

        val found = vaultService.getEntry(id)
        assertThat(found).isNotNull
        assertThat(found!!.website).isEqualTo("old.com")

        val updated = vaultService.updateEntry(found.copy(website = "new.com", length = 24))
        assertThat(updated.website).isEqualTo("new.com")
        assertThat(updated.length).isEqualTo(24)
    }

    @Test
    fun `deleteEntry should remove entry`() {
        vaultService.setup("主密码")
        val session = MockHttpSession()
        vaultService.login("主密码", session)

        val saved = vaultService.addEntry(PasswordEntry(website = "del.com", username = "u"))

        val deleted = vaultService.deleteEntry(saved.id!!)
        assertThat(deleted).isTrue()
        assertThat(vaultService.getEntry(saved.id)).isNull()
    }

    @Test
    fun `rotateEntry should increment counter`() {
        vaultService.setup("主密码")
        val session = MockHttpSession()
        vaultService.login("主密码", session)

        val saved = vaultService.addEntry(PasswordEntry(website = "rotate.com", username = "u"))
        assertThat(saved.counter).isEqualTo(1)

        val rotated = vaultService.rotateEntry(session, saved.id!!)
        assertThat(rotated.counter).isEqualTo(2)

        val rotated2 = vaultService.rotateEntry(session, saved.id)
        assertThat(rotated2.counter).isEqualTo(3)
    }

    @Test
    fun `derivePassword should produce deterministic output`() {
        vaultService.setup("主密码")
        val session = MockHttpSession()
        vaultService.login("主密码", session)

        val saved = vaultService.addEntry(PasswordEntry(website = "github.com", username = "alice"))

        val derived1 = vaultService.derivePassword(session, saved.id!!, null)
        val derived2 = vaultService.derivePassword(session, saved.id, null)

        assertThat(derived1.password).isEqualTo(derived2.password)
        assertThat(derived1.password).hasSize(16)
    }

    @Test
    fun `derivePassword should support historical counter`() {
        vaultService.setup("主密码")
        val session = MockHttpSession()
        vaultService.login("主密码", session)

        val saved = vaultService.addEntry(PasswordEntry(website = "history.com", username = "u"))

        val pwd1 = vaultService.derivePassword(session, saved.id!!, 1).password

        vaultService.rotateEntry(session, saved.id)
        vaultService.rotateEntry(session, saved.id)

        val pwd3 = vaultService.derivePassword(session, saved.id, 3).password
        val pwd1Replay = vaultService.derivePassword(session, saved.id, 1).password

        assertThat(pwd1).isNotEqualTo(pwd3)
        assertThat(pwd1Replay).isEqualTo(pwd1)
    }

    @Test
    fun `derivePassword should throw for counter out of range`() {
        vaultService.setup("主密码")
        val session = MockHttpSession()
        vaultService.login("主密码", session)

        val saved = vaultService.addEntry(PasswordEntry(website = "range.com", username = "u", counter = 2))

        val ex = assertThrows<AppException> {
            vaultService.derivePassword(session, saved.id!!, 3)
        }
        assertThat(ex.code).isEqualTo(ErrorCode.INVALID_REQUEST)
    }
}
