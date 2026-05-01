package site.hanabii.fireworks.domain

import java.time.Instant

/**
 * 密码条目领域模型。
 * password 字段为明文，仅在内存中流转；持久化层只保存 encryptedPassword。
 */
data class PasswordEntry(
    val id: Long? = null,
    val website: String,
    val username: String,
    val password: String,
    val notes: String = "",
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
