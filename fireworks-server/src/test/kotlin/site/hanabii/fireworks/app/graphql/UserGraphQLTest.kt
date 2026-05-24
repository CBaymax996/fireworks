package site.hanabii.fireworks.app.graphql

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * GraphQL 用户模块集成测试。
 * 测试 createUser / users / user / updateUser / deleteUser。
 */
@SpringBootTest
class UserGraphQLTest {

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        jdbcTemplate.execute("DELETE FROM users")
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.execute("DELETE FROM users")
    }

    // ==================== createUser ====================

    @Test
    fun `createUser 应成功创建用户`() {
        // 单行 mutation，避免多行 JSON 导致 400
        val mutation = """mutation { createUser(username: \"张三\", email: \"zhangsan@test.com\") { id username email } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.createUser.id").isNumber)
            .andExpect(jsonPath("$.data.createUser.username").value("张三"))
            .andExpect(jsonPath("$.data.createUser.email").value("zhangsan@test.com"))
    }

    @Test
    fun `createUser 空用户名应返回错误`() {
        // 单行 mutation
        val mutation = """mutation { createUser(username: \"\", email: \"test@test.com\") { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("INVALID_REQUEST"))
    }

    @Test
    fun `createUser 空邮箱应返回错误`() {
        // 单行 mutation
        val mutation = """mutation { createUser(username: \"user\", email: \"\") { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("INVALID_REQUEST"))
    }

    // ==================== users ====================

    @Test
    fun `users 应列出所有用户`() {
        createUser("用户A", "a@test.com")
        createUser("用户B", "b@test.com")

        val query = """query { users { id username email } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.users.length()").value(2))
    }

    @Test
    fun `users 无数据时应返回空列表`() {
        val query = """query { users { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.users.length()").value(0))
    }

    // ==================== user ====================

    @Test
    fun `user 应返回单个用户`() {
        val id = createUser("李四", "lisi@test.com")

        val query = """query { user(id: $id) { id username email } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.user.username").value("李四"))
            .andExpect(jsonPath("$.data.user.email").value("lisi@test.com"))
    }

    @Test
    fun `user 不存在的 ID 应返回 null`() {
        val query = """query { user(id: 99999) { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.user").doesNotExist())
    }

    // ==================== updateUser ====================

    @Test
    fun `updateUser 应更新用户名和邮箱`() {
        val id = createUser("王五", "wangwu@test.com")

        // 单行 mutation
        val mutation = """mutation { updateUser(id: $id, username: \"王五改\", email: \"wangwu_new@test.com\") { id username email } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.updateUser.username").value("王五改"))
            .andExpect(jsonPath("$.data.updateUser.email").value("wangwu_new@test.com"))
    }

    @Test
    fun `updateUser 不存在的 ID 应返回错误`() {
        // 单行 mutation
        val mutation = """mutation { updateUser(id: 99999, username: \"不存在\", email: \"no@test.com\") { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("USER_NOT_FOUND"))
    }

    @Test
    fun `updateUser 空用户名应返回错误`() {
        val id = createUser("用户", "u@test.com")

        // 单行 mutation
        val mutation = """mutation { updateUser(id: $id, username: \"\", email: \"ok@test.com\") { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("INVALID_REQUEST"))
    }

    // ==================== deleteUser ====================

    @Test
    fun `deleteUser 应删除用户`() {
        val id = createUser("待删除", "del@test.com")

        val mutation = """mutation { deleteUser(id: $id) }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.deleteUser").value(true))

        // 确认已删除
        val query = """query { user(id: $id) { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(jsonPath("$.data.user").doesNotExist())
    }

    @Test
    fun `deleteUser 不存在的 ID 应返回错误`() {
        val mutation = """mutation { deleteUser(id: 99999) }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("USER_NOT_FOUND"))
    }

    // ==================== 辅助方法 ====================

    private fun createUser(username: String, email: String): Long {
        // 单行 mutation
        val mutation = """mutation { createUser(username: \"${username}\", email: \"${email}\") { id } }"""
        val result = mockMvc.perform(
            post("/graphql")
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
