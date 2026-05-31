package site.hanabii.fireworks.domain.auth

import java.time.LocalDateTime

/**
 * Web 登录账号领域实体。
 */
data class Account(
    val id: Long?,
    val username: String,
    val passwordHash: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
