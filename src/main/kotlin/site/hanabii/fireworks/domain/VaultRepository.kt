package site.hanabii.fireworks.domain

/**
 * 密码库配置仓储接口。
 */
interface VaultRepository {
    fun save(config: VaultConfig): VaultConfig
    fun find(): VaultConfig?
    fun exists(): Boolean
}
