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
 * GraphQL 密码库模块集成测试。
 * 测试 vaultSetup / vaultLogin / vaultStatus / 条目 CRUD / derivePassword / generatePassword 等。
 */
@SpringBootTest
class VaultGraphQLTest {

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        // 重建密码库相关表
        jdbcTemplate.execute("DROP TABLE IF EXISTS password_entries")
        jdbcTemplate.execute("DROP TABLE IF EXISTS vault_config")
        jdbcTemplate.execute(site.hanabii.fireworks.infra.vault.VaultConfigDO.DDL.trimIndent())
        jdbcTemplate.execute(site.hanabii.fireworks.infra.vault.PasswordEntryDO.DDL.trimIndent())
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS password_entries")
        jdbcTemplate.execute("DROP TABLE IF EXISTS vault_config")
    }

    // ==================== vaultSetup ====================

    @Test
    fun `vaultSetup 应成功初始化密码库`() {
        val mutation = """mutation { vaultSetup(masterPassword: \"主密码\") }"""

        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.vaultSetup").value(true))
    }

    @Test
    fun `vaultSetup 重复初始化应返回错误`() {
        val mutation = """mutation { vaultSetup(masterPassword: \"密码1\") }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.vaultSetup").value(true))

        // 再次初始化
        val mutation2 = """mutation { vaultSetup(masterPassword: \"密码2\") }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation2"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("INVALID_REQUEST"))
    }

    // ==================== vaultLogin / vaultStatus ====================

    @Test
    fun `vaultLogin 正确密码应解锁成功`() {
        setupVault("春风十里")

        val session = MockHttpSession()
        val mutation = """mutation { vaultLogin(masterPassword: \"春风十里\") }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.vaultLogin").value(true))
    }

    @Test
    fun `vaultLogin 错误密码应返回错误`() {
        setupVault("正确密码")

        val session = MockHttpSession()
        val mutation = """mutation { vaultLogin(masterPassword: \"错误密码\") }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("INVALID_CREDENTIALS"))
    }

    @Test
    fun `vaultStatus 未初始化应返回 initialized=false`() {
        val query = """query { vaultStatus { initialized authenticated } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.vaultStatus.initialized").value(false))
            .andExpect(jsonPath("$.data.vaultStatus.authenticated").value(false))
    }

    @Test
    fun `vaultStatus 已初始化未登录应返回 initialized=true, authenticated=false`() {
        setupVault("测试密码")

        val query = """query { vaultStatus { initialized authenticated } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.vaultStatus.initialized").value(true))
            .andExpect(jsonPath("$.data.vaultStatus.authenticated").value(false))
    }

    @Test
    fun `vaultStatus 已登录应返回 authenticated=true`() {
        val session = setupVaultAndLogin("我的密码")

        val query = """query { vaultStatus { initialized authenticated } }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.vaultStatus.initialized").value(true))
            .andExpect(jsonPath("$.data.vaultStatus.authenticated").value(true))
    }

    // ==================== createVaultEntry ====================

    @Test
    fun `createVaultEntry 应成功创建 DERIVED 条目`() {
        val session = setupVaultAndLogin("主密码")

        // 单行 mutation
        val mutation = """mutation { createVaultEntry(input: { website: \"github.com\", username: \"alice\", notes: \"代码托管\", mode: \"DERIVED\", length: 20, useLowercase: true, useUppercase: true, useDigits: true, useSymbols: false }) { id website username mode counter length } }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.createVaultEntry.id").isNumber)
            .andExpect(jsonPath("$.data.createVaultEntry.website").value("github.com"))
            .andExpect(jsonPath("$.data.createVaultEntry.username").value("alice"))
            .andExpect(jsonPath("$.data.createVaultEntry.mode").value("DERIVED"))
            .andExpect(jsonPath("$.data.createVaultEntry.counter").value(1))
            .andExpect(jsonPath("$.data.createVaultEntry.length").value(20))
    }

    @Test
    fun `createVaultEntry 应成功创建 STORED 条目`() {
        val session = setupVaultAndLogin("主密码")

        // 单行 mutation
        val mutation = """mutation { createVaultEntry(input: { website: \"bank.com\", username: \"user1\", mode: \"STORED\", password: \"银行卡密码\" }) { id website username mode password } }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.createVaultEntry.id").isNumber)
            .andExpect(jsonPath("$.data.createVaultEntry.mode").value("STORED"))
            .andExpect(jsonPath("$.data.createVaultEntry.password").value("银行卡密码"))
    }

    @Test
    fun `createVaultEntry 未登录应返回错误`() {
        setupVault("密码")

        // 单行 mutation
        val mutation = """mutation { createVaultEntry(input: { website: \"x.com\", username: \"u\" }) { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("NOT_AUTHENTICATED"))
    }

    // ==================== vaultEntries / vaultEntry ====================

    @Test
    fun `vaultEntries 应列出所有条目`() {
        val session = setupVaultAndLogin("主密码")
        createEntry(session, "site1.com", "u1")
        createEntry(session, "site2.com", "u2")

        val query = """query { vaultEntries { id website username } }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.vaultEntries.length()").value(2))
    }

    @Test
    fun `vaultEntry 应返回单条条目`() {
        val session = setupVaultAndLogin("主密码")
        val id = createEntry(session, "target.com", "bob")

        val query = """query { vaultEntry(id: $id) { id website username } }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.vaultEntry.website").value("target.com"))
            .andExpect(jsonPath("$.data.vaultEntry.username").value("bob"))
    }

    @Test
    fun `vaultEntry 不存在的 ID 应返回 null`() {
        val session = setupVaultAndLogin("主密码")

        val query = """query { vaultEntry(id: 99999) { id } }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.vaultEntry").doesNotExist())
    }

    // ==================== updateVaultEntry ====================

    @Test
    fun `updateVaultEntry 应更新条目属性`() {
        val session = setupVaultAndLogin("主密码")
        val id = createEntry(session, "old.com", "olduser")

        // 单行 mutation
        val mutation = """mutation { updateVaultEntry(id: $id, input: { website: \"new.com\", username: \"newuser\", notes: \"更新后的备注\", length: 32 }) { id website username notes length } }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.updateVaultEntry.website").value("new.com"))
            .andExpect(jsonPath("$.data.updateVaultEntry.username").value("newuser"))
            .andExpect(jsonPath("$.data.updateVaultEntry.notes").value("更新后的备注"))
            .andExpect(jsonPath("$.data.updateVaultEntry.length").value(32))
    }

    @Test
    fun `updateVaultEntry 不存在的 ID 应返回错误`() {
        val session = setupVaultAndLogin("主密码")

        // 单行 mutation
        val mutation = """mutation { updateVaultEntry(id: 99999, input: { website: \"x.com\" }) { id } }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("ENTRY_NOT_FOUND"))
    }

    // ==================== deleteVaultEntry ====================

    @Test
    fun `deleteVaultEntry 应删除条目`() {
        val session = setupVaultAndLogin("主密码")
        val id = createEntry(session, "todelete.com", "u")

        val mutation = """mutation { deleteVaultEntry(id: $id) }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.deleteVaultEntry").value(true))

        // 确认已删除
        val query = """query { vaultEntry(id: $id) { id } }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(jsonPath("$.data.vaultEntry").doesNotExist())
    }

    // ==================== derivePassword ====================

    @Test
    fun `derivePassword 应返回派生密码`() {
        val session = setupVaultAndLogin("主密码")
        val id = createEntry(session, "github.com", "alice")

        val query = """query { derivePassword(id: $id, counter: null) { password counter } }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.derivePassword.password").isString)
            .andExpect(jsonPath("$.data.derivePassword.counter").value(1))
    }

    @Test
    fun `derivePassword 指定 counter 应返回历史密码`() {
        val session = setupVaultAndLogin("主密码")
        val id = createEntry(session, "hist.com", "u")

        val query = """query { derivePassword(id: $id, counter: 1) { password } }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.derivePassword.password").isString)
    }

    // ==================== generatePassword ====================

    @Test
    fun `generatePassword 应生成默认长度 16 的密码`() {
        val query = """query { generatePassword(length: 16, lowercase: true, uppercase: true, digits: true, symbols: true) { password } }"""
        val result = mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.generatePassword.password").isString)
            .andReturn()

        val response = result.response.contentAsString
        val password = Regex(""""password":"([^"]+)"""").find(response)!!.groupValues[1]
        assert(password.length == 16) { "默认密码长度应为 16，实际为 ${password.length}" }
    }

    @Test
    fun `generatePassword 可指定长度`() {
        val query = """query { generatePassword(length: 32, lowercase: true, uppercase: true, digits: true, symbols: true) { password } }"""
        val result = mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.generatePassword.password").isString)
            .andReturn()

        val response = result.response.contentAsString
        val password = Regex(""""password":"([^"]+)"""").find(response)!!.groupValues[1]
        assert(password.length == 32) { "密码长度应为 32，实际为 ${password.length}" }
    }

    @Test
    fun `generatePassword 全部字符集关闭应报错`() {
        // 单行 query
        val query = """query { generatePassword(lowercase: false, uppercase: false, digits: false, symbols: false) { password } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors").exists())
    }

    // ==================== vaultLogout ====================

    @Test
    fun `vaultLogout 应锁定密码库`() {
        val session = setupVaultAndLogin("主密码")

        // 确认已解锁
        val statusQuery = """query { vaultStatus { authenticated } }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$statusQuery"}""")
        )
            .andExpect(jsonPath("$.data.vaultStatus.authenticated").value(true))

        // 锁定
        val logoutMutation = """mutation { vaultLogout }"""
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$logoutMutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.vaultLogout").value(true))

        // 确认已锁定
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$statusQuery"}""")
        )
            .andExpect(jsonPath("$.data.vaultStatus.authenticated").value(false))
    }

    // ==================== 辅助方法 ====================

    private fun setupVault(password: String) {
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "mutation { vaultSetup(masterPassword: \"$password\") }"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.vaultSetup").value(true))
    }

    private fun setupVaultAndLogin(password: String): MockHttpSession {
        setupVault(password)
        val session = MockHttpSession()
        mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "mutation { vaultLogin(masterPassword: \"$password\") }"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.vaultLogin").value(true))
        return session
    }

    private fun createEntry(session: MockHttpSession, website: String, username: String): Long {
        // 单行 mutation
        val mutation = """mutation { createVaultEntry(input: { website: \"$website\", username: \"$username\" }) { id } }"""
        val result = mockMvc.perform(
            post("/graphql")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andReturn()

        val response = result.response.contentAsString
        val idMatch = Regex(""""id":(\d+)""").find(response)
        return idMatch!!.groupValues[1].toLong()
    }
}
