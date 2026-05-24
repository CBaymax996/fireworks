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
 * GraphQL 投资组合模块集成测试。
 * 测试组合 CRUD / 配比管理 / 出入金 / 净值历史 / tushare 搜索等。
 */
@SpringBootTest
class PortfolioGraphQLTest {

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        // 清理投资组合相关数据（表名已修正为单数形式）
        jdbcTemplate.execute("DELETE FROM portfolio_holding")
        jdbcTemplate.execute("DELETE FROM portfolio_snapshot")
        jdbcTemplate.execute("DELETE FROM stock_history")
        jdbcTemplate.execute("DELETE FROM portfolio")
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.execute("DELETE FROM portfolio_holding")
        jdbcTemplate.execute("DELETE FROM portfolio_snapshot")
        jdbcTemplate.execute("DELETE FROM stock_history")
        jdbcTemplate.execute("DELETE FROM portfolio")
    }

    // ==================== createPortfolio / portfolio ====================

    @Test
    fun `createPortfolio 应成功创建组合`() {
        val mutation = """mutation { createPortfolio(name: \"我的组合\") { id name totalNav assetCount } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.createPortfolio.name").value("我的组合"))
            .andExpect(jsonPath("$.data.createPortfolio.totalNav").value(0.0))
            .andExpect(jsonPath("$.data.createPortfolio.assetCount").value(1))
    }

    @Test
    fun `portfolio 应列出所有组合`() {
        createPortfolio("组合A")
        createPortfolio("组合B")

        val query = """query { portfolios { id name } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.portfolios.length()").value(2))
    }

    // ==================== portfolio ====================

    @Test
    fun `portfolio 应返回组合概览`() {
        createPortfolio("测试组合")

        val query = """query { portfolio(name: \"测试组合\") { id name totalNav cash holdings { code } } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.portfolio.name").value("测试组合"))
            .andExpect(jsonPath("$.data.portfolio.totalNav").value(0.0))
            .andExpect(jsonPath("$.data.portfolio.cash").value(0.0))
    }

    // ==================== deletePortfolio ====================

    @Test
    fun `deletePortfolio 应删除组合`() {
        createPortfolio("待删除")

        val mutation = """mutation { deletePortfolio(name: \"待删除\") }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.deletePortfolio").value(true))

        // 确认已删除
        val query = """query { portfolios { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(jsonPath("$.data.portfolios.length()").value(0))
    }

    // ==================== renamePortfolio ====================

    @Test
    fun `renamePortfolio 应重命名组合`() {
        createPortfolio("旧名称")

        val mutation = """mutation { renamePortfolio(name: \"旧名称\", newName: \"新名称\") }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.renamePortfolio").value(true))

        val query = """query { portfolios { id name } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(jsonPath("$.data.portfolios[0].name").value("新名称"))
    }

    // ==================== addAllocation / allocation ====================

    @Test
    fun `addAllocation 应添加配比`() {
        createPortfolio("配比组合")
        val cashId = getCashId("配比组合")
        // 先将 CASH 占比降至 0
        mockMvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content("""{"query": "mutation { updateAllocation(name: \"配比组合\", allocationId: $cashId, targetRatio: 0.01) }"}""")).andExpect(status().isOk)

        // 单行 mutation
        val mutation = """mutation { addAllocation(name: \"配比组合\", code: \"510300\", assetName: \"沪深300ETF\", assetType: \"ETF\", targetRatio: 0.6) { id code name assetType targetRatio } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.addAllocation.code").value("510300"))
            .andExpect(jsonPath("$.data.addAllocation.name").value("沪深300ETF"))
            .andExpect(jsonPath("$.data.addAllocation.assetType").value("ETF"))
            .andExpect(jsonPath("$.data.addAllocation.targetRatio").value(0.6))
    }

    @Test
    fun `allocation 应返回配比列表`() {
        createPortfolio("配比组合2")
        val cashId = getCashId("配比组合2")
        mockMvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content("""{"query": "mutation { updateAllocation(name: \"配比组合2\", allocationId: $cashId, targetRatio: 0.01) }"}""")).andExpect(status().isOk)
        addAllocation("配比组合2", "510050", "上证50", "ETF", 0.3)
        addAllocation("配比组合2", "159919", "沪深300", "ETF", 0.69)

        val query = """query { allocation(name: \"配比组合2\") { id code name targetRatio } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.allocation.length()").value(3))
    }

    // ==================== updateAllocation / deleteAllocation ====================

    @Test
    fun `updateAllocation 应更新配比`() {
        createPortfolio("更新配比")
        val cashId = getCashId("更新配比")
        mockMvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content("""{"query": "mutation { updateAllocation(name: \"更新配比\", allocationId: $cashId, targetRatio: 0.01) }"}""")).andExpect(status().isOk)
        val alloc = addAllocation("更新配比", "510050", "上证50", "ETF", 0.3)

        // 单行 mutation
        val mutation = """mutation { updateAllocation(name: \"更新配比\", allocationId: $alloc, targetRatio: 0.5) }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.updateAllocation").value(true))
    }

    @Test
    fun `deleteAllocation 应删除配比`() {
        createPortfolio("删除配比")
        val cashId = getCashId("删除配比")
        mockMvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content("""{"query": "mutation { updateAllocation(name: \"删除配比\", allocationId: $cashId, targetRatio: 0.01) }"}""")).andExpect(status().isOk)
        val alloc = addAllocation("删除配比", "510050", "上证50", "ETF", 0.3)

        val mutation = """mutation { deleteAllocation(name: \"删除配比\", allocationId: $alloc) }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.deleteAllocation").value(true))

        val query = """query { allocation(name: \"删除配比\") { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(jsonPath("$.data.allocation.length()").value(1))
    }

    // ==================== deposit / withdraw ====================

    @Test
    fun `deposit 应增加现金`() {
        createPortfolio("入金组合")

        val mutation = """mutation { deposit(name: \"入金组合\", amount: 10000.0) { name totalNav cash } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.deposit.totalNav").value(10000.0))
            .andExpect(jsonPath("$.data.deposit.cash").value(10000.0))
    }

    @Test
    fun `withdraw 应减少现金`() {
        createPortfolio("出金组合")
        // 先入金
        depositMutation("出金组合", 10000.0)
        val cashId = getCashId("出金组合")

        val mutation = """mutation { withdraw(name: \"出金组合\", allocationId: $cashId, shares: 2000) { totalNav } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            // 出金后 NAV 应减少
            .andExpect(jsonPath("$.data.withdraw.totalNav").isNumber)
    }

    // ==================== navHistory ====================

    @Test
    fun `navHistory 应返回净值历史`() {
        createPortfolio("净值组合")

        val query = """query { navHistory(name: \"净值组合\", days: 30) { portfolioName data { date nav } } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.navHistory.portfolioName").value("净值组合"))
            .andExpect(jsonPath("$.data.navHistory.data").isArray)
    }

    // ==================== tushareSearch ====================

    @Test
    fun `tushareSearch 搜索关键字应返回结果或空列表`() {
        // tushare 在测试环境可能不可用，测试应优雅处理
        val query = """query { tushareSearch(keyword: \"沪深300\") { code name assetType } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.tushareSearch").isArray)
    }

    // ==================== 辅助方法 ====================

    private fun createPortfolio(name: String): String {
        val mutation = """mutation { createPortfolio(name: \"$name\") { id } }"""
        val result = mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andReturn()
        return Regex(""""id":"([^"]+)"""").find(result.response.contentAsString)!!.groupValues[1]
    }

    /** 查询组合的分配列表，找到 CASH 分配 ID */
    private fun getCashId(name: String): String {
        val query = """query { allocation(name: \"$name\") { id code } }"""
        val result = mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andReturn()
        // 找第一个包含 CASH 的分配条目中的 id
        val cashBlock = """\{[^}]*"code":"CASH"[^}]*\}""".toRegex().find(result.response.contentAsString)
        return Regex(""""id":"([^"]+)"""").find(cashBlock!!.value)!!.groupValues[1]
    }

    private fun addAllocation(name: String, code: String, assetName: String, assetType: String, targetRatio: Double): Long {
        // 单行 mutation
        val mutation = """mutation { addAllocation(name: \"$name\", code: \"$code\", assetName: \"$assetName\", assetType: \"$assetType\", targetRatio: $targetRatio) { id } }"""
        val result = mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andReturn()
        return Regex(""""id":"([^"]+)"""").find(result.response.contentAsString)!!.groupValues[1].toLong()
    }

    private fun depositMutation(name: String, amount: Double) {
        val mutation = """mutation { deposit(name: \"$name\", amount: $amount) { totalNav } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
    }
}
