package site.hanabii.fireworks.app.vault

import jakarta.servlet.http.HttpSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor

/**
 * 密码库会话服务单元测试。
 */
class VaultSessionServiceTest {

    private val sessionService = VaultSessionService()

    @Test
    fun `storeSeed and getSeed should round-trip`() {
        val session = mock(HttpSession::class.java)
        val seed = ByteArray(32) { it.toByte() }

        sessionService.storeSeed(session, seed)

        val captor = argumentCaptor<String>()
        val byteCaptor = argumentCaptor<ByteArray>()
        verify(session).setAttribute(captor.capture(), byteCaptor.capture())

        assertThat(captor.firstValue).isEqualTo("vaultSeed")
        assertThat(byteCaptor.firstValue).isEqualTo(seed)
    }

    @Test
    fun `getSeed should return null when session has no seed`() {
        val session = mock(HttpSession::class.java)
        org.mockito.Mockito.`when`(session.getAttribute("vaultSeed")).thenReturn(null)

        val seed = sessionService.getSeed(session)

        assertThat(seed).isNull()
    }

    @Test
    fun `getSeed should return copy of stored bytes`() {
        val session = mock(HttpSession::class.java)
        val seed = ByteArray(32) { it.toByte() }

        org.mockito.Mockito.`when`(session.getAttribute("vaultSeed")).thenReturn(seed)

        val result = sessionService.getSeed(session)

        assertThat(result).isEqualTo(seed)
        assertThat(result).isNotSameAs(seed) // 防御性拷贝
    }

    @Test
    fun `clear should remove vaultSeed from session`() {
        val session = mock(HttpSession::class.java)

        sessionService.clear(session)

        verify(session).removeAttribute("vaultSeed")
    }
}
