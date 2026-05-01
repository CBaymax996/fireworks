package site.hanabii.fireworks.domain

import java.time.Instant

/**
 * 密码库配置。全局只有一条记录（id 固定为 1）。
 */
data class VaultConfig(
    val id: Long = 1,
    val passwordHash: String,
    val encryptionSalt: ByteArray,
    val createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VaultConfig
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
