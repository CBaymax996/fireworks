package site.hanabii.fireworks.domain.auth

import java.time.Instant

/**
 * Web 登录账号领域实体。
 */
data class Account(
    val id: Long? = null,
    val username: String,
    val passwordHash: String,
    val createdAt: Instant = Instant.now()
)
