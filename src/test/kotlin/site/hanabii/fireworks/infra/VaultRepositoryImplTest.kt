package site.hanabii.fireworks.infra

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import site.hanabii.fireworks.domain.VaultConfig
import site.hanabii.fireworks.domain.VaultRepository

/**
 * 密码库配置仓储集成测试。
 *
 * 覆盖：insert / update、find / exists、salt 的 Base64 编解码往返。
 */
@SpringBootTest
class VaultRepositoryImplTest(
    @param:Autowired private val vaultRepository: VaultRepository,
    @param:Autowired private val jdbcTemplate: JdbcTemplate
) {

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS vault_config")
        jdbcTemplate.execute(VaultConfigDO.DDL.trimIndent())
    }

    @Test
    fun `save should insert when no existing config`() {
        val salt = byteArrayOf(1, 2, 3, 4, 5)
        val config = VaultConfig(passwordHash = "hash123", encryptionSalt = salt)

        val saved = vaultRepository.save(config)

        assertThat(saved.id).isEqualTo(1)
        assertThat(saved.passwordHash).isEqualTo("hash123")
        assertThat(saved.encryptionSalt).isEqualTo(salt)
    }

    @Test
    fun `save should update when config already exists`() {
        val salt1 = byteArrayOf(1, 2, 3)
        vaultRepository.save(VaultConfig(passwordHash = "hash1", encryptionSalt = salt1))

        val salt2 = byteArrayOf(4, 5, 6)
        val updated = vaultRepository.save(VaultConfig(passwordHash = "hash2", encryptionSalt = salt2))

        assertThat(updated.passwordHash).isEqualTo("hash2")
        assertThat(updated.encryptionSalt).isEqualTo(salt2)

        val found = vaultRepository.find()
        assertThat(found!!.passwordHash).isEqualTo("hash2")
    }

    @Test
    fun `find should return null when not initialized`() {
        assertThat(vaultRepository.find()).isNull()
    }

    @Test
    fun `find should return config after save`() {
        val salt = byteArrayOf(9, 8, 7)
        vaultRepository.save(VaultConfig(passwordHash = "abc", encryptionSalt = salt))

        val found = vaultRepository.find()

        assertThat(found).isNotNull
        assertThat(found!!.passwordHash).isEqualTo("abc")
        assertThat(found.encryptionSalt).isEqualTo(salt)
    }

    @Test
    fun `exists should be false before initialization`() {
        assertThat(vaultRepository.exists()).isFalse()
    }

    @Test
    fun `exists should be true after save`() {
        vaultRepository.save(VaultConfig(passwordHash = "h", encryptionSalt = byteArrayOf(1)))
        assertThat(vaultRepository.exists()).isTrue()
    }

    @Test
    fun `salt should survive base64 round trip`() {
        val salt = ByteArray(16) { it.toByte() }
        vaultRepository.save(VaultConfig(passwordHash = "pwd", encryptionSalt = salt))

        val found = vaultRepository.find()
        assertThat(found!!.encryptionSalt).isEqualTo(salt)
    }
}
