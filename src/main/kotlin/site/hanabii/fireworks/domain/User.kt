package site.hanabii.fireworks.domain

import java.time.Instant

/**
 * 领域层的纯净 User 实体，不依赖任何 ORM / 持久化框架。
 */
data class User(
    val id: Long? = null,
    val username: String,
    val email: String,
    val createdAt: Instant = Instant.now()
)