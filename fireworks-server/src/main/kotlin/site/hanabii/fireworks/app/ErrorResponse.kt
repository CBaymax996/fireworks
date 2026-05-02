package site.hanabii.fireworks.app

import java.time.Instant

data class ErrorResponse(
    val code: String,
    val message: String,
    val path: String,
    val timestamp: Instant = Instant.now()
)
