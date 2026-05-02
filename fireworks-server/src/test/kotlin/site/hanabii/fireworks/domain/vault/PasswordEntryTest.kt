package site.hanabii.fireworks.domain.vault

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * PasswordEntry 不变性约束单元测试。
 */
class PasswordEntryTest {

    @Test
    fun `valid entry should not throw`() {
        val entry = PasswordEntry(website = "github.com", username = "alice")
        assertThat(entry.website).isEqualTo("github.com")
    }

    @Test
    fun `blank website should throw`() {
        assertThrows<IllegalArgumentException> {
            PasswordEntry(website = "", username = "alice")
        }
        assertThrows<IllegalArgumentException> {
            PasswordEntry(website = "  ", username = "alice")
        }
    }

    @Test
    fun `blank username should throw`() {
        assertThrows<IllegalArgumentException> {
            PasswordEntry(website = "github.com", username = "")
        }
    }

    @Test
    fun `counter zero should throw`() {
        assertThrows<IllegalArgumentException> {
            PasswordEntry(website = "x.com", username = "u", counter = 0)
        }
    }

    @Test
    fun `counter negative should throw`() {
        assertThrows<IllegalArgumentException> {
            PasswordEntry(website = "x.com", username = "u", counter = -1)
        }
    }

    @Test
    fun `length 7 should throw`() {
        assertThrows<IllegalArgumentException> {
            PasswordEntry(website = "x.com", username = "u", length = 7)
        }
    }

    @Test
    fun `length 65 should throw`() {
        assertThrows<IllegalArgumentException> {
            PasswordEntry(website = "x.com", username = "u", length = 65)
        }
    }

    @Test
    fun `length 8 and 64 should be valid`() {
        PasswordEntry(website = "x.com", username = "u", length = 8)
        PasswordEntry(website = "x.com", username = "u", length = 64)
    }

    @Test
    fun `all charsets false should throw`() {
        assertThrows<IllegalArgumentException> {
            PasswordEntry(
                website = "x.com", username = "u",
                useLowercase = false, useUppercase = false, useDigits = false, useSymbols = false
            )
        }
    }

    @Test
    fun `copy should preserve invariants`() {
        val entry = PasswordEntry(website = "github.com", username = "alice")
        val copied = entry.copy(length = 32)
        assertThat(copied.length).isEqualTo(32)
    }
}
