package site.hanabii.fireworks.domain.vault

data class DerivedPassword(
    val entry: PasswordEntry,
    val password: String,
    val counter: Int
)
