package site.hanabii.fireworks.domain.vault

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * PasswordDerivation 算法单元测试。
 */
class PasswordDerivationTest {

    @Test
    fun `deriveSeed should produce deterministic output`() {
        val password = "我爱北京天安门"
        val salt = PasswordDerivation.generateSalt()

        val seed1 = PasswordDerivation.deriveSeed(password, salt)
        val seed2 = PasswordDerivation.deriveSeed(password, salt)

        assertThat(seed1).isEqualTo(seed2)
        assertThat(seed1).hasSize(32)
    }

    @Test
    fun `deriveSeed should produce different output for different passwords`() {
        val salt = PasswordDerivation.generateSalt()

        val seed1 = PasswordDerivation.deriveSeed("密码甲", salt)
        val seed2 = PasswordDerivation.deriveSeed("密码乙", salt)

        assertThat(seed1).isNotEqualTo(seed2)
    }

    @Test
    fun `derivePassword should be deterministic`() {
        val seed = PasswordDerivation.deriveSeed("主密码", PasswordDerivation.generateSalt())
        val entry = PasswordEntry(website = "github.com", username = "alice")

        val pwd1 = PasswordDerivation.derivePassword(seed, entry)
        val pwd2 = PasswordDerivation.derivePassword(seed, entry)

        assertThat(pwd1).isEqualTo(pwd2)
    }

    @Test
    fun `derivePassword should produce different passwords for different entries`() {
        val seed = PasswordDerivation.deriveSeed("主密码", PasswordDerivation.generateSalt())
        val entry1 = PasswordEntry(website = "github.com", username = "alice")
        val entry2 = PasswordEntry(website = "gmail.com", username = "alice")

        val pwd1 = PasswordDerivation.derivePassword(seed, entry1)
        val pwd2 = PasswordDerivation.derivePassword(seed, entry2)

        assertThat(pwd1).isNotEqualTo(pwd2)
    }

    @Test
    fun `different counters should produce different passwords`() {
        val seed = PasswordDerivation.deriveSeed("主密码", PasswordDerivation.generateSalt())
        val entry = PasswordEntry(website = "github.com", username = "alice", counter = 3)

        val pwd1 = PasswordDerivation.derivePassword(seed, entry, 1)
        val pwd2 = PasswordDerivation.derivePassword(seed, entry, 2)
        val pwd3 = PasswordDerivation.derivePassword(seed, entry, 3)

        assertThat(pwd1).isNotEqualTo(pwd2)
        assertThat(pwd2).isNotEqualTo(pwd3)
        assertThat(pwd1).isNotEqualTo(pwd3)
    }

    @Test
    fun `length 8 should produce 8 character password`() {
        val seed = PasswordDerivation.deriveSeed("主密码", PasswordDerivation.generateSalt())
        val entry = PasswordEntry(website = "github.com", username = "alice", length = 8)

        val pwd = PasswordDerivation.derivePassword(seed, entry)

        assertThat(pwd).hasSize(8)
    }

    @Test
    fun `length 64 should produce 64 character password`() {
        val seed = PasswordDerivation.deriveSeed("主密码", PasswordDerivation.generateSalt())
        val entry = PasswordEntry(website = "github.com", username = "alice", length = 64)

        val pwd = PasswordDerivation.derivePassword(seed, entry)

        assertThat(pwd).hasSize(64)
    }

    @Test
    fun `lowercase only should produce only lowercase`() {
        val seed = PasswordDerivation.deriveSeed("主密码", PasswordDerivation.generateSalt())
        val entry = PasswordEntry(
            website = "github.com", username = "alice",
            useLowercase = true, useUppercase = false, useDigits = false, useSymbols = false
        )

        val pwd = PasswordDerivation.derivePassword(seed, entry)

        assertThat(pwd).matches("^[a-z]+\$")
    }

    @Test
    fun `digits only should produce only digits`() {
        val seed = PasswordDerivation.deriveSeed("主密码", PasswordDerivation.generateSalt())
        val entry = PasswordEntry(
            website = "github.com", username = "alice",
            useLowercase = false, useUppercase = false, useDigits = true, useSymbols = false
        )

        val pwd = PasswordDerivation.derivePassword(seed, entry)

        assertThat(pwd).matches("^[0-9]+\$")
    }

    @Test
    fun `uppercase only should produce only uppercase`() {
        val seed = PasswordDerivation.deriveSeed("主密码", PasswordDerivation.generateSalt())
        val entry = PasswordEntry(
            website = "github.com", username = "alice",
            useLowercase = false, useUppercase = true, useDigits = false, useSymbols = false
        )

        val pwd = PasswordDerivation.derivePassword(seed, entry)

        assertThat(pwd).matches("^[A-Z]+\$")
    }

    @Test
    fun `symbols only should produce only symbols`() {
        val seed = PasswordDerivation.deriveSeed("主密码", PasswordDerivation.generateSalt())
        val entry = PasswordEntry(
            website = "github.com", username = "alice",
            useLowercase = false, useUppercase = false, useDigits = false, useSymbols = true
        )

        val pwd = PasswordDerivation.derivePassword(seed, entry)

        val symbolChars = "!@#\$%^&*()-_=+[]{};:,.<>?"
        assertThat(pwd.all { it in symbolChars }).isTrue()
    }

    @Test
    fun `all charsets enabled should contain at least one from each`() {
        val seed = PasswordDerivation.deriveSeed("主密码", PasswordDerivation.generateSalt())
        val entry = PasswordEntry(website = "github.com", username = "alice")

        val pwd = PasswordDerivation.derivePassword(seed, entry)

        val hasLower = pwd.any { it in 'a'..'z' }
        val hasUpper = pwd.any { it in 'A'..'Z' }
        val hasDigit = pwd.any { it in '0'..'9' }
        val symbolChars = "!@#\$%^&*()-_=+[]{};:,.<>?"
        val hasSymbol = pwd.any { it in symbolChars }

        // With length 16 and all 4 charsets, forced injection guarantees all appear
        assertThat(hasLower).isTrue()
        assertThat(hasUpper).isTrue()
        assertThat(hasDigit).isTrue()
        assertThat(hasSymbol).isTrue()
    }

    @Test
    fun `generateSalt should produce 16 bytes`() {
        val salt = PasswordDerivation.generateSalt()
        assertThat(salt).hasSize(16)
    }

    @Test
    fun `generateSalt should produce different values`() {
        val salt1 = PasswordDerivation.generateSalt()
        val salt2 = PasswordDerivation.generateSalt()
        assertThat(salt1).isNotEqualTo(salt2)
    }
}
