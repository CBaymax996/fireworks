package site.hanabii.fireworks.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import site.hanabii.fireworks.app.crypto.VaultCryptoService
import site.hanabii.fireworks.domain.PasswordEntry
import site.hanabii.fireworks.infra.PasswordEntryDO
import site.hanabii.fireworks.infra.VaultConfigDO

/**
 * 密码库业务服务集成测试。
 *
 * 覆盖：vault 初始化与认证（含中文主密码）、密码条目完整 CRUD、错误密钥解密失败。
 */
@SpringBootTest
class VaultServiceTest(
    @param:Autowired private val vaultService: VaultService,
    @param:Autowired private val cryptoService: VaultCryptoService,
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
    fun `setup with Chinese master password and CRUD entries should work`() {
        val masterPassword = "我爱北京天安门"

        assertThat(vaultService.isInitialized()).isFalse()
        vaultService.setup(masterPassword)
        assertThat(vaultService.isInitialized()).isTrue()

        val config = vaultService.authenticate(masterPassword)
        assertThat(config).isNotNull

        assertThrows<IllegalStateException> {
            vaultService.setup(masterPassword)
        }

        assertThat(vaultService.authenticate("错误的密码")).isNull()
    }

    @Test
    fun `add list get update and delete entry should work`() {
        val masterPassword = "黄河之水天上来"
        vaultService.setup(masterPassword)
        val config = vaultService.authenticate(masterPassword)!!
        val key = cryptoService.deriveKey(masterPassword, config.encryptionSalt)

        val entry1 = vaultService.addEntry(
            key, PasswordEntry(website = "github.com", username = "alice", password = "gh_secret_123")
        )
        assertThat(entry1.id).isNotNull()
        assertThat(entry1.password).isEqualTo("gh_secret_123")

        val entry2 = vaultService.addEntry(
            key, PasswordEntry(website = "gmail.com", username = "alice@gmail.com", password = "gm_secret_456", notes = "主邮箱")
        )

        val list = vaultService.listEntries(key)
        assertThat(list).hasSize(2)
        assertThat(list.map { it.website }).containsExactlyInAnyOrder("github.com", "gmail.com")
        assertThat(list.map { it.password }).containsExactlyInAnyOrder("gh_secret_123", "gm_secret_456")

        val found = vaultService.getEntry(key, entry1.id!!)
        assertThat(found).isNotNull
        assertThat(found!!.username).isEqualTo("alice")
        assertThat(found.password).isEqualTo("gh_secret_123")

        val updated = vaultService.updateEntry(
            key, entry1.copy(password = "new_secret_999")
        )
        assertThat(updated.password).isEqualTo("new_secret_999")
        val reloaded = vaultService.getEntry(key, entry1.id)
        assertThat(reloaded!!.password).isEqualTo("new_secret_999")

        val deleted = vaultService.deleteEntry(entry1.id)
        assertThat(deleted).isTrue()
        assertThat(vaultService.getEntry(key, entry1.id)).isNull()
        assertThat(vaultService.listEntries(key)).hasSize(1)
    }

    @Test
    fun `decrypt with wrong key should fail`() {
        val masterPassword = "正确的密码"
        vaultService.setup(masterPassword)
        val config = vaultService.authenticate(masterPassword)!!
        val key = cryptoService.deriveKey(masterPassword, config.encryptionSalt)
        val wrongKey = cryptoService.deriveKey("错误的密码", config.encryptionSalt)

        vaultService.addEntry(key, PasswordEntry(website = "test.com", username = "u", password = "secret"))

        assertThrows<javax.crypto.AEADBadTagException> {
            vaultService.listEntries(wrongKey)
        }
    }
}
