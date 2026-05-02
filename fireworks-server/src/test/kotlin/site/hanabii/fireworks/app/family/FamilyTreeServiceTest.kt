package site.hanabii.fireworks.app.family

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import site.hanabii.fireworks.app.AppException
import site.hanabii.fireworks.domain.family.FamilyLineageRepository
import site.hanabii.fireworks.domain.family.FamilyPerson
import site.hanabii.fireworks.domain.family.FamilyPersonRepository
import site.hanabii.fireworks.domain.family.FamilyTree
import site.hanabii.fireworks.domain.family.FamilyTreeRepository
import site.hanabii.fireworks.domain.family.PersonSpouse
import site.hanabii.fireworks.domain.family.PersonSpouseRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FamilyTreeServiceTest {

    private val treeRepo = mock<FamilyTreeRepository>()
    private val personRepo = mock<FamilyPersonRepository>()
    private val spouseRepo = mock<PersonSpouseRepository>()
    private val lineageRepo = mock<FamilyLineageRepository>()

    private val service = FamilyTreeService(treeRepo, personRepo, spouseRepo, lineageRepo)

    private var nextPersonId = 1L

    private val testTree = FamilyTree(id = 1L, name = "张氏族谱")
    private val testPerson = FamilyPerson(
        id = 1L, familyTreeId = 1L, name = "张三", gender = "male",
        generationOrder = 0, sortOrder = 0
    )

    @BeforeEach
    fun setUp() {
        whenever(treeRepo.findAll()).thenReturn(listOf(testTree))
        whenever(treeRepo.findById(1L)).thenReturn(testTree)
        whenever(treeRepo.save(any())).thenAnswer { it.arguments[0] as FamilyTree }
        whenever(personRepo.findById(1L)).thenReturn(testPerson)
        whenever(personRepo.save(any())).thenAnswer {
            val p = it.arguments[0] as FamilyPerson
            p.copy(id = p.id ?: nextPersonId++)
        }
        whenever(personRepo.findByTreeId(1L)).thenReturn(listOf(testPerson))
        whenever(personRepo.findByFatherId(1L)).thenReturn(emptyList())
    }

    @Test
    fun `listTrees 返回所有族谱`() {
        val trees = service.listTrees()
        assertEquals(1, trees.size)
        assertEquals("张氏族谱", trees[0].name)
    }

    @Test
    fun `createTree 创建族谱成功`() {
        val tree = service.createTree("新族谱", null)
        assertNotNull(tree)
        assertEquals("新族谱", tree.name)
    }

    @Test
    fun `getTree 族谱不存在抛异常`() {
        whenever(treeRepo.findById(99L)).thenReturn(null)
        val ex = assertThrows<AppException> { service.getTree(99L) }
        assertEquals("族谱不存在: 99", ex.message)
    }

    @Test
    fun `addPerson 添加成员成功`() {
        val person = service.addPerson(1L, CreatePersonRequest(
            name = "李四", gender = "male"
        ))
        assertNotNull(person)
    }

    @Test
    fun `addPerson 父亲不属于当前族谱抛异常`() {
        val otherPerson = testPerson.copy(id = 2L, familyTreeId = 2L)
        whenever(personRepo.findById(2L)).thenReturn(otherPerson)
        val ex = assertThrows<AppException> {
            service.addPerson(1L, CreatePersonRequest(
                name = "李四", gender = "male", fatherId = 2L
            ))
        }
        assertEquals("父亲不属于当前族谱", ex.message)
    }

    @Test
    fun `getChildren 返回子节点和配偶`() {
        val child = testPerson.copy(id = 2L, name = "张子", fatherId = 1L)
        whenever(personRepo.findByFatherId(1L)).thenReturn(listOf(child))
        whenever(personRepo.findByFatherId(2L)).thenReturn(emptyList())
        whenever(spouseRepo.findPrimaryByHusbandId(1L)).thenReturn(null)

        val result = service.getChildren(1L, 1L)
        assertEquals(1, result.children.size)
        assertEquals("张子", result.children[0].name)
        assertEquals(null, result.person.spouse)
    }

    @Test
    fun `getAncestors 返回祖先链（含自身）`() {
        val grandpa = testPerson.copy(id = 0L, name = "太祖")
        val father = testPerson.copy(id = 5L, name = "父亲", fatherId = 0L)
        val me = testPerson.copy(id = 10L, name = "我", fatherId = 5L)

        whenever(personRepo.findById(10L)).thenReturn(me)
        whenever(personRepo.findById(5L)).thenReturn(father)
        whenever(personRepo.findById(0L)).thenReturn(grandpa)

        val ancestors = service.getAncestors(1L, 10L)
        assertEquals(3, ancestors.size)
        assertEquals("太祖", ancestors[0].name)
        assertEquals("父亲", ancestors[1].name)
        assertEquals("我", ancestors[2].name)
    }

    @Test
    fun `addSpouse 妻子非女性抛异常`() {
        val wife = testPerson.copy(id = 3L, gender = "male")
        whenever(personRepo.findById(3L)).thenReturn(wife)
        val ex = assertThrows<AppException> {
            service.addSpouse(1L, 1L, 3L, true)
        }
        assertEquals("配偶必须是女性", ex.message)
    }
}
