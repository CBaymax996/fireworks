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
 * GraphQL 族谱模块集成测试。
 * 测试族谱 CRUD / 成员管理 / 配偶关系 / 子女查询 / 祖先链等。
 */
@SpringBootTest
class FamilyTreeGraphQLTest {

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        // 清理族谱相关数据
        jdbcTemplate.execute("DELETE FROM person_spouses")
        jdbcTemplate.execute("DELETE FROM family_lineages")
        jdbcTemplate.execute("DELETE FROM family_persons")
        jdbcTemplate.execute("DELETE FROM family_trees")
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.execute("DELETE FROM person_spouses")
        jdbcTemplate.execute("DELETE FROM family_lineages")
        jdbcTemplate.execute("DELETE FROM family_persons")
        jdbcTemplate.execute("DELETE FROM family_trees")
    }

    // ==================== createFamilyTree ====================

    @Test
    fun `createFamilyTree 应成功创建族谱`() {
        // 单行 mutation，避免多行 JSON 导致 400
        val mutation = """mutation { createFamilyTree(name: \"张氏族谱\", description: \"清河张氏\") { id name description } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.createFamilyTree.id").isNumber)
            .andExpect(jsonPath("$.data.createFamilyTree.name").value("张氏族谱"))
            .andExpect(jsonPath("$.data.createFamilyTree.description").value("清河张氏"))
    }

    // ==================== familyTrees ====================

    @Test
    fun `familyTrees 应列出所有族谱`() {
        createTree("族谱A", "描述A")
        createTree("族谱B", "描述B")

        val query = """query { familyTrees { id name description } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.familyTrees.length()").value(2))
    }

    // ==================== familyTree ====================

    @Test
    fun `familyTree 应返回单个族谱`() {
        val id = createTree("李氏族谱", null)

        val query = """query { familyTree(id: $id) { id name } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.familyTree.name").value("李氏族谱"))
    }

    @Test
    fun `familyTree 不存在的 ID 应返回错误`() {
        val query = """query { familyTree(id: 99999) { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("FAMILY_TREE_NOT_FOUND"))
    }

    // ==================== createFamilyPerson ====================

    @Test
    fun `createFamilyPerson 应成功创建成员`() {
        val treeId = createTree("王氏族谱", null)

        // 单行 mutation
        val mutation = """mutation { createFamilyPerson(treeId: $treeId, input: { name: \"王大明\", gender: \"male\", birthDate: \"1950-01-01\", biography: \"家族族长\" }) { id name gender birthDate biography familyTreeId } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.createFamilyPerson.id").isNumber)
            .andExpect(jsonPath("$.data.createFamilyPerson.name").value("王大明"))
            .andExpect(jsonPath("$.data.createFamilyPerson.gender").value("male"))
            .andExpect(jsonPath("$.data.createFamilyPerson.birthDate").value("1950-01-01"))
            .andExpect(jsonPath("$.data.createFamilyPerson.familyTreeId").value(treeId))
    }

    @Test
    fun `createFamilyPerson 可指定父亲创建子节点`() {
        val treeId = createTree("test", null)
        val fatherId = createPerson(treeId, "父亲", "male")

        // 单行 mutation
        val mutation = """mutation { createFamilyPerson(treeId: $treeId, input: { name: \"儿子\", gender: \"male\", fatherId: $fatherId, sortOrder: 1 }) { id name fatherId generationOrder } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.createFamilyPerson.name").value("儿子"))
            .andExpect(jsonPath("$.data.createFamilyPerson.fatherId").value(fatherId))
            .andExpect(jsonPath("$.data.createFamilyPerson.generationOrder").value(1))
    }

    // ==================== familyPersons / familyPerson ====================

    @Test
    fun `familyPersons 应列出族谱所有成员`() {
        val treeId = createTree("Test", null)
        createPerson(treeId, "A", "male")
        createPerson(treeId, "B", "female")

        val query = """query { familyPersons(treeId: $treeId) { id name gender } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.familyPersons.length()").value(2))
    }

    @Test
    fun `familyPersons 支持搜索`() {
        val treeId = createTree("Test", null)
        createPerson(treeId, "张三丰", "male")
        createPerson(treeId, "李四", "male")

        val query = """query { familyPersons(treeId: $treeId, search: \"张三\") { id name } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.familyPersons.length()").value(1))
            .andExpect(jsonPath("$.data.familyPersons[0].name").value("张三丰"))
    }

    @Test
    fun `familyPerson 应返回单个成员`() {
        val treeId = createTree("Test", null)
        val personId = createPerson(treeId, "目标人物", "male")

        val query = """query { familyPerson(treeId: $treeId, personId: $personId) { id name } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.familyPerson.name").value("目标人物"))
    }

    @Test
    fun `familyPerson 不存在的 ID 应返回错误`() {
        val treeId = createTree("Test", null)

        val query = """query { familyPerson(treeId: $treeId, personId: 99999) { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.errors[0].extensions.code").value("FAMILY_PERSON_NOT_FOUND"))
    }

    // ==================== familyRoots ====================

    @Test
    fun `familyRoots 应返回男性根节点`() {
        val treeId = createTree("Test", null)
        val rootId = createPerson(treeId, "根节点", "male")
        // 创建子节点（有父亲，不应出现在根节点中）
        createPersonWithFather(treeId, "子节点", "male", rootId)

        val query = """query { familyRoots(treeId: $treeId) { id name } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.familyRoots.length()").value(1))
            .andExpect(jsonPath("$.data.familyRoots[0].name").value("根节点"))
    }

    // ==================== familyChildren ====================

    @Test
    fun `familyChildren 应返回子女列表`() {
        val treeId = createTree("Test", null)
        val fatherId = createPerson(treeId, "父亲", "male")
        val sonId = createPersonWithFather(treeId, "长子", "male", fatherId)

        val query = """query { familyChildren(treeId: $treeId, personId: $fatherId) { person { name } children { id name gender } } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.familyChildren.person.name").value("父亲"))
            .andExpect(jsonPath("$.data.familyChildren.children.length()").value(1))
            .andExpect(jsonPath("$.data.familyChildren.children[0].name").value("长子"))
    }

    // ==================== addSpouse / removeSpouse ====================

    @Test
    fun `addSpouse 应创建配偶关系`() {
        val treeId = createTree("Test", null)
        val husbandId = createPerson(treeId, "丈夫", "male")
        val wifeId = createPerson(treeId, "妻子", "female")

        // 单行 mutation
        val mutation = """mutation { addSpouse(treeId: $treeId, husbandId: $husbandId, wifeId: $wifeId, isPrimary: true) { id husbandId wifeId isPrimary } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.addSpouse.id").isNumber)
            .andExpect(jsonPath("$.data.addSpouse.husbandId").value(husbandId))
            .andExpect(jsonPath("$.data.addSpouse.wifeId").value(wifeId))
            .andExpect(jsonPath("$.data.addSpouse.isPrimary").value(true))
    }

    @Test
    fun `removeSpouse 应移除配偶关系`() {
        val treeId = createTree("Test", null)
        val husbandId = createPerson(treeId, "丈夫", "male")
        val wifeId = createPerson(treeId, "妻子", "female")
        val spouseId = addSpouse(treeId, husbandId, wifeId)

        val mutation = """mutation { removeSpouse(treeId: $treeId, husbandId: $husbandId, spouseId: $spouseId) }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.removeSpouse").value(true))

        // 确认已移除
        val query = """query { familySpouses(treeId: $treeId, husbandId: $husbandId) { id } }"""
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$query"}""")
        )
            .andExpect(jsonPath("$.data.familySpouses.length()").value(0))
    }

    // ==================== 辅助方法 ====================

    private fun createTree(name: String, description: String?): Long {
        val descStr = description?.let { "description: \\\"$it\\\"" } ?: ""
        val mutation = """mutation { createFamilyTree(name: \"$name\" $descStr) { id } }"""
        val result = mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andReturn()
        return Regex(""""id":(\d+)""").find(result.response.contentAsString)!!.groupValues[1].toLong()
    }

    private fun createPerson(treeId: Long, name: String, gender: String): Long {
        // 单行 mutation
        val mutation = """mutation { createFamilyPerson(treeId: $treeId, input: { name: \"$name\", gender: \"$gender\" }) { id } }"""
        val result = mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andReturn()
        return Regex(""""id":(\d+)""").find(result.response.contentAsString)!!.groupValues[1].toLong()
    }

    private fun createPersonWithFather(treeId: Long, name: String, gender: String, fatherId: Long): Long {
        // 单行 mutation
        val mutation = """mutation { createFamilyPerson(treeId: $treeId, input: { name: \"$name\", gender: \"$gender\", fatherId: $fatherId }) { id } }"""
        val result = mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andReturn()
        return Regex(""""id":(\d+)""").find(result.response.contentAsString)!!.groupValues[1].toLong()
    }

    private fun addSpouse(treeId: Long, husbandId: Long, wifeId: Long): Long {
        // 单行 mutation
        val mutation = """mutation { addSpouse(treeId: $treeId, husbandId: $husbandId, wifeId: $wifeId, isPrimary: true) { id } }"""
        val result = mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query": "$mutation"}""")
        )
            .andExpect(status().isOk)
            .andReturn()
        return Regex(""""id":(\d+)""").find(result.response.contentAsString)!!.groupValues[1].toLong()
    }
}
