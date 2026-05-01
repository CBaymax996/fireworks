package site.hanabii.fireworks.app

import jakarta.servlet.http.HttpSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor
import site.hanabii.fireworks.app.crypto.VaultCryptoService

/**
 * 密码库会话服务单元测试。
 *
 * 覆盖：session 中密钥的存储、读取、清除，以及不同主密码产生不同密钥。
 */
class VaultSessionServiceTest {

    private val cryptoService = VaultCryptoService()
    private val sessionService = VaultSessionService(cryptoService)

    @Test
    fun `storeKey and getKey should round-trip with Chinese master password`() {
        val session = mock(HttpSession::class.java)
        val masterPassword = "山高水长"
        val salt = cryptoService.generateSalt()

        sessionService.storeKey(session, masterPassword, salt)

        val captor = argumentCaptor<String>()
        val byteCaptor = argumentCaptor<ByteArray>()
        verify(session).setAttribute(captor.capture(), byteCaptor.capture())

        assertThat(captor.firstValue).isEqualTo("vaultKey")
        assertThat(byteCaptor.firstValue).isNotEmpty()
    }

    @Test
    fun `getKey should return null when session has no vault key`() {
        val session = mock(HttpSession::class.java)
        org.mockito.Mockito.`when`(session.getAttribute("vaultKey")).thenReturn(null)

        val key = sessionService.getKey(session)

        assertThat(key).isNull()
    }

    @Test
    fun `getKey should reconstruct SecretKey when session has vault key bytes`() {
        val session = mock(HttpSession::class.java)
        val masterPassword = "春风十里"
        val salt = cryptoService.generateSalt()
        val derivedKey = cryptoService.deriveKey(masterPassword, salt)

        org.mockito.Mockito.`when`(session.getAttribute("vaultKey")).thenReturn(derivedKey.encoded)

        val key = sessionService.getKey(session)

        assertThat(key).isNotNull
        assertThat(key!!.encoded).isEqualTo(derivedKey.encoded)
        assertThat(key.algorithm).isEqualTo("AES")
    }

    @Test
    fun `clear should remove vaultKey from session`() {
        val session = mock(HttpSession::class.java)

        sessionService.clear(session)

        verify(session).removeAttribute("vaultKey")
    }

    @Test
    fun `different master passwords should produce different session bytes`() {
        val session1 = mock(HttpSession::class.java)
        val session2 = mock(HttpSession::class.java)
        val salt = cryptoService.generateSalt()

        sessionService.storeKey(session1, "密码甲", salt)
        sessionService.storeKey(session2, "密码乙", salt)

        val captor1 = argumentCaptor<ByteArray>()
        val captor2 = argumentCaptor<ByteArray>()
        verify(session1).setAttribute(org.mockito.kotlin.any(), captor1.capture())
        verify(session2).setAttribute(org.mockito.kotlin.any(), captor2.capture())

        assertThat(captor1.firstValue).isNotEqualTo(captor2.firstValue)
    }
}
