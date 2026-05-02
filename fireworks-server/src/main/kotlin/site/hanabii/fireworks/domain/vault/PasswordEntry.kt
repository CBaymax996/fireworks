package site.hanabii.fireworks.domain.vault

import java.time.Instant

data class PasswordEntry(
    val id: Long? = null,
    val website: String,
    val username: String,
    val notes: String = "",
    val counter: Int = 1,
    val length: Int = 16,
    val useLowercase: Boolean = true,
    val useUppercase: Boolean = true,
    val useDigits: Boolean = true,
    val useSymbols: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(website.isNotBlank()) { "website must not be blank" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(counter >= 1) { "counter must be >= 1, got $counter" }
        require(length in 8..64) { "length must be 8..64, got $length" }
        require(useLowercase || useUppercase || useDigits || useSymbols) {
            "at least one charset must be enabled"
        }
    }
}
