package site.hanabii.fireworks.app.graphql

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * GraphQL 认证模块集成测试。
 * 测试 authStatus / register / login / logout 的完整流程。
 */
@SpringBootTest
class AuthGraphQLTest {

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        // 清理测试数据
        jdbcTemplate.execute("DELETE FROM accounts")
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.execute("DELETE FROM accounts")
    }

    // ==================== authStatus ====================

    @Test
    fun `authStatus 未登录应返回 authenticated=false`() {
        val query = """query { authStatus { authenticated accountId } }"""

        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.authStatus.authenticated").value(false))
            .andExpect(jsonPath("$.data.authStatus.accountId").doesNotExist())
    }

    @Test
    fun `authStatus 已登录应返回 authenticated=true`() {
        val session = MockHttpSession()
        registerAndLogin(session, "testuser", "testpass123")

        val query = """query { authStatus { authenticated accountId } }"""

        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.authStatus.authenticated").value(true))
            .andExpect(jsonPath("$.data.authStatus.accountId").isNumber)
    }

    // ==================== register ====================

    @Test
    fun `register 应成功注册新账号`() {
        val mutation = """mutation { register(username: \"newuser\", password: \"pass123\") { id username } }"""

        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.register.id").isNumber)
            .andExpect(jsonPath("$.data.register.username").value("newuser"))
    }

    @Test
    fun `register 重复用户名应返回错误`() {
        // 先注册一个用户
        val mutation1 = """mutation { register(username: \"dupuser\", password: \"pass123\") { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation1"}""")
        )
            .andExpect(status().isOk)

        // 再次用相同用户名注册
        val mutation2 = """mutation { register(username: \"dupuser\", password: \"pass456\") { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation2"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("ACCOUNT_EXISTS"))
    }

    // ==================== login ====================

    @Test
    fun `login 正确密码应登录成功`() {
        // 先注册
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "mutation { register(username: \"loginuser\", password: \"mypassword\") { id } }"}""")
        )
            .andExpect(status().isOk)

        // 登录
        val session = MockHttpSession()
        val mutation = """mutation { login(username: \"loginuser\", password: \"mypassword\") { success account { id username } } }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.login.success").value(true))
            .andExpect(jsonPath("$.data.login.account.username").value("loginuser"))
    }

    @Test
    fun `login 错误密码应返回错误`() {
        // 先注册
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "mutation { register(username: \"wrongpwd\", password: \"correct\") { id } }"}""")
        )
            .andExpect(status().isOk)

        val mutation = """mutation { login(username: \"wrongpwd\", password: \"wrongpassword\") { success } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("INVALID_CREDENTIALS"))
    }

    @Test
    fun `login 不存在的用户应返回错误`() {
        val mutation = """mutation { login(username: \"nosuchuser\", password: \"pass\") { success } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("INVALID_CREDENTIALS"))
    }

    // ==================== logout ====================

    @Test
    fun `logout 应清除会话中的登录状态`() {
        val session = MockHttpSession()
        registerAndLogin(session, "logoutuser", "pass123")

        // 确认已登录
        val authQuery = """query { authStatus { authenticated } }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$authQuery"}""")
        )
            .andExpect(jsonPath("$.data.authStatus.authenticated").value(true))

        // 登出
        val logoutMutation = """mutation { logout }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$logoutMutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.logout").value(true))

        // 确认已登出
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$authQuery"}""")
        )
            .andExpect(jsonPath("$.data.authStatus.authenticated").value(false))
    }

    // ==================== 辅助方法 ====================

    /** 注册并登录，返回 session */
    private fun registerAndLogin(session: MockHttpSession, username: String, password: String) {
        // 注册
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "mutation { register(username: \"$username\", password: \"$password\") { id } }"}""")
        )
            .andExpect(status().isOk)
        // 登录
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "mutation { login(username: \"$username\", password: \"$password\") { success } }"}""")
        )
            .andExpect(status().isOk)
    }
}
