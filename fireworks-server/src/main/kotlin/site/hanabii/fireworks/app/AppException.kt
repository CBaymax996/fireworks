package site.hanabii.fireworks.app

import org.springframework.http.HttpStatus

class AppException(
    val code: ErrorCode,
    val status: HttpStatus,
    override val message: String
) : RuntimeException(message)
