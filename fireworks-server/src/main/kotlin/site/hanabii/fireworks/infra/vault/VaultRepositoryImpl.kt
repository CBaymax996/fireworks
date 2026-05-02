package site.hanabii.fireworks.infra.vault

import org.ktorm.database.Database
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import org.ktorm.dsl.update
import org.ktorm.dsl.where
import org.springframework.stereotype.Repository
import site.hanabii.fireworks.domain.vault.VaultConfig
import site.hanabii.fireworks.domain.vault.VaultRepository
import java.util.Base64

/**
 * 密码库配置仓储实现。
 *
 * 全局仅允许一条记录（id 固定为 1）。
 * salt 以 Base64 字符串存入 SQLite，读取时再解码为 ByteArray。
 */
@Repository
class VaultRepositoryImpl(
    private val database: Database
) : VaultRepository {

    private val base64 = Base64.getEncoder()

    override fun save(config: VaultConfig): VaultConfig {
        val saltBase64 = base64.encodeToString(config.encryptionSalt)
        val existing = find()
        if (existing == null) {
            database.insert(VaultConfigDO) {
                set(it.id, config.id)
                set(it.passwordHash, config.passwordHash)
                set(it.encryptionSalt, saltBase64)
                set(it.createdAt, config.createdAt)
            }
        } else {
            database.update(VaultConfigDO) {
                set(it.passwordHash, config.passwordHash)
                set(it.encryptionSalt, saltBase64)
                set(it.createdAt, config.createdAt)
                where { it.id eq config.id }
            }
        }
        return find()
            ?: throw IllegalStateException("VaultConfig insert/update succeeded but cannot be loaded")
    }

    override fun find(): VaultConfig? {
        return database.from(VaultConfigDO)
            .select()
            .where { VaultConfigDO.id eq 1 }
            .map { it.toVaultConfig() }
            .firstOrNull()
    }

    override fun exists(): Boolean {
        return database.from(VaultConfigDO)
            .select()
            .totalRecordsInAllPages > 0
    }
}
