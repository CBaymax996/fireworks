package site.hanabii.fireworks.domain.vault

import java.time.Instant

data class PasswordEntry(
    val id: Long? = null,
    val website: String,
    val username: String,
    val notes: String = "",
    val password: String? = null,  // 加密后的密码（仅 STORED 模式），AES-GCM + Base64
    val mode: String = "DERIVED",  // "DERIVED" 或 "STORED"
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
        require(mode == "DERIVED" || mode == "STORED") { "mode must be 'DERIVED' or 'STORED', got '$mode'" }
        require(mode != "STORED" || !password.isNullOrBlank()) { "password must not be blank in STORED mode" }
    }
}
