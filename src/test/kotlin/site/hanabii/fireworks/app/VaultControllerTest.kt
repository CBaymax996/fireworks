package site.hanabii.fireworks.app

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import site.hanabii.fireworks.infra.PasswordEntryDO
import site.hanabii.fireworks.infra.VaultConfigDO

/**
 * 密码库控制器集成测试。
 *
 * 使用 MockMvc 对 /api/vault/ 全部端点进行端到端测试，覆盖：
 * setup / login / logout / status、entries CRUD、认证与初始化拦截、异常路径。
 */
@SpringBootTest
class VaultControllerTest {

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        jdbcTemplate.execute("DROP TABLE IF EXISTS password_entries")
        jdbcTemplate.execute("DROP TABLE IF EXISTS vault_config")
        jdbcTemplate.execute(VaultConfigDO.DDL.trimIndent())
        jdbcTemplate.execute(PasswordEntryDO.DDL.trimIndent())
    }

    // ---------- setup ----------

    @Test
    fun `setup should initialize vault`() {
        mockMvc.perform(
            post("/api/vault/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"masterPassword": "春风十里"}""")
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `setup should fail when vault already initialized`() {
        setupVault("密码")

        mockMvc.perform(
            post("/api/vault/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"masterPassword": "密码2"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
    }

    @Test
    fun `setup should fail with blank password`() {
        mockMvc.perform(
            post("/api/vault/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"masterPassword": ""}""")
        )
            .andExpect(status().isBadRequest)
    }

    // ---------- login / logout / status ----------

    @Test
    fun `login should succeed with correct password`() {
        setupVault("正确的密码")

        mockMvc.perform(
            post("/api/vault/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"masterPassword": "正确的密码"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `login should fail with wrong password`() {
        setupVault("正确的密码")

        mockMvc.perform(
            post("/api/vault/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"masterPassword": "错误的密码"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
    }

    @Test
    fun `login should fail with blank password`() {
        mockMvc.perform(
            post("/api/vault/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"masterPassword": ""}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `logout should succeed`() {
        val session = setupVaultAndLogin("密码")

        mockMvc.perform(post("/api/vault/logout").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `status should reflect initialized but not authenticated`() {
        setupVault("密码")

        mockMvc.perform(get("/api/vault/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.initialized").value(true))
            .andExpect(jsonPath("$.authenticated").value(false))
    }

    @Test
    fun `status should reflect authenticated after login`() {
        val session = setupVaultAndLogin("密码")

        mockMvc.perform(get("/api/vault/status").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.initialized").value(true))
            .andExpect(jsonPath("$.authenticated").value(true))
    }

    // ---------- entries CRUD ----------

    @Test
    fun `create and list entries should work`() {
        val session = setupVaultAndLogin("主密码")

        mockMvc.perform(
            post("/api/vault/entries")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"website": "github.com", "username": "alice", "password": "gh_secret", "notes": ""}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.website").value("github.com"))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.password").value("gh_secret"))

        mockMvc.perform(get("/api/vault/entries").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].website").value("github.com"))
    }

    @Test
    fun `create entry should fail with blank fields`() {
        val session = setupVaultAndLogin("主密码")

        mockMvc.perform(
            post("/api/vault/entries")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"website": "", "username": "u", "password": "p"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `get entry should return entry`() {
        val session = setupVaultAndLogin("主密码")
        val id = createEntry(session, "gmail.com", "bob", "gm_secret")

        mockMvc.perform(get("/api/vault/entries/$id").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.website").value("gmail.com"))
            .andExpect(jsonPath("$.password").value("gm_secret"))
    }

    @Test
    fun `get entry should return 404 for non-existent id`() {
        val session = setupVaultAndLogin("主密码")

        mockMvc.perform(get("/api/vault/entries/9999").session(session))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("ENTRY_NOT_FOUND"))
    }

    @Test
    fun `update entry should modify existing entry`() {
        val session = setupVaultAndLogin("主密码")
        val id = createEntry(session, "old.com", "u", "p")

        mockMvc.perform(
            put("/api/vault/entries/$id")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"website": "new.com", "username": "v", "password": "q", "notes": "n"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.website").value("new.com"))
            .andExpect(jsonPath("$.password").value("q"))

        mockMvc.perform(get("/api/vault/entries/$id").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.website").value("new.com"))
    }

    @Test
    fun `update entry should return 404 for non-existent id`() {
        val session = setupVaultAndLogin("主密码")

        mockMvc.perform(
            put("/api/vault/entries/9999")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"website": "x", "username": "u", "password": "p", "notes": ""}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `delete entry should remove entry`() {
        val session = setupVaultAndLogin("主密码")
        val id = createEntry(session, "del.com", "u", "p")

        mockMvc.perform(delete("/api/vault/entries/$id").session(session))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/vault/entries/$id").session(session))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `delete entry should return 404 for non-existent id`() {
        val session = setupVaultAndLogin("主密码")

        mockMvc.perform(delete("/api/vault/entries/9999").session(session))
            .andExpect(status().isNotFound)
    }

    // ---------- 认证/初始化检查 ----------

    @Test
    fun `entries endpoints should require authentication`() {
        setupVault("密码")

        mockMvc.perform(get("/api/vault/entries"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"))
    }

    @Test
    fun `entries endpoints should require vault initialization`() {
        val session = MockHttpSession()

        mockMvc.perform(get("/api/vault/entries").session(session))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("VAULT_NOT_INITIALIZED"))
    }

    @Test
    fun `create entry should fail when not authenticated`() {
        setupVault("密码")

        mockMvc.perform(
            post("/api/vault/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"website": "x", "username": "u", "password": "p", "notes": ""}""")
        )
            .andExpect(status().isUnauthorized)
    }

    // ---------- 辅助方法 ----------

    private fun setupVault(password: String) {
        mockMvc.perform(
            post("/api/vault/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"masterPassword": "$password"}""")
        )
            .andExpect(status().isCreated)
    }

    private fun setupVaultAndLogin(password: String): MockHttpSession {
        setupVault(password)
        val session = MockHttpSession()
        mockMvc.perform(
            post("/api/vault/login")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"masterPassword": "$password"}""")
        )
            .andExpect(status().isOk)
        return session
    }

    private fun createEntry(session: MockHttpSession, website: String, username: String, password: String): Long {
        val result = mockMvc.perform(
            post("/api/vault/entries")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"website": "$website", "username": "$username", "password": "$password", "notes": ""}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val response = result.response.contentAsString
        val idMatch = Regex(""""id":(\d+)""").find(response)
        return idMatch!!.groupValues[1].toLong()
    }
}
