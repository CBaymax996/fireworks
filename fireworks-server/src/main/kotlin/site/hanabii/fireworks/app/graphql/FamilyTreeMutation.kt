package site.hanabii.fireworks.app.graphql

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import org.springframework.stereotype.Component
import site.hanabii.fireworks.app.family.CreatePersonRequest
import site.hanabii.fireworks.app.family.FamilyTreeService
import site.hanabii.fireworks.app.family.UpdatePersonRequest
import site.hanabii.fireworks.domain.family.FamilyPerson
import site.hanabii.fireworks.domain.family.FamilyTree
import site.hanabii.fireworks.domain.family.PersonSpouse

/**
 * 族谱变更 — GraphQL Mutation resolver。
 */
@Component
class FamilyTreeMutation(
    private val familyTreeService: FamilyTreeService
) {
    @GraphQLDescription("创建新族谱")
    fun createFamilyTree(name: String, description: String? = null): FamilyTree {
        return familyTreeService.createTree(name, description)
    }

    @GraphQLDescription("更新族谱信息")
    fun updateFamilyTree(id: Long, name: String? = null, description: String? = null): FamilyTree {
        return familyTreeService.updateTree(id, name, description)
    }

    @GraphQLDescription("删除族谱及其所有关联数据")
    fun deleteFamilyTree(id: Long): Boolean {
        familyTreeService.deleteTree(id)
        return true
    }

    @GraphQLDescription("添加族谱成员")
    fun createFamilyPerson(treeId: Long, input: FamilyPersonInput): FamilyPerson {
        val req = CreatePersonRequest(
            name = input.name,
            gender = input.gender ?: "unknown",
            birthDate = input.birthDate,
            deathDate = input.deathDate,
            biography = input.biography,
            fatherId = input.fatherId?.toLong(),
            sortOrder = input.sortOrder ?: 0
        )
        return familyTreeService.addPerson(treeId, req)
    }

    @GraphQLDescription("更新族谱成员")
    fun updateFamilyPerson(treeId: Long, personId: Long, input: FamilyPersonUpdateInput): FamilyPerson {
        val req = UpdatePersonRequest(
            name = input.name,
            gender = input.gender,
            birthDate = input.birthDate,
            deathDate = input.deathDate,
            biography = input.biography,
            fatherId = input.fatherId?.toLong(),
            sortOrder = input.sortOrder
        )
        return familyTreeService.updatePerson(treeId, personId, req)
    }

    @GraphQLDescription("删除族谱成员（子孙将变为根节点）")
    fun deleteFamilyPerson(treeId: Long, personId: Long): Boolean {
        familyTreeService.deletePerson(treeId, personId)
        return true
    }

    @GraphQLDescription("添加配偶关系")
    fun addSpouse(
        treeId: Long,
        husbandId: Long,
        wifeId: Long,
        isPrimary: Boolean = true
    ): PersonSpouse {
        return familyTreeService.addSpouse(treeId, husbandId, wifeId, isPrimary)
    }

    @GraphQLDescription("移除配偶关系")
    fun removeSpouse(treeId: Long, husbandId: Long, spouseId: Long): Boolean {
        familyTreeService.removeSpouse(treeId, husbandId, spouseId)
        return true
    }
}

/** GraphQL 族谱成员创建输入 */
data class FamilyPersonInput(
    val name: String,
    val gender: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val biography: String? = null,
    val fatherId: Int? = null,
    val sortOrder: Int? = null
)

/** GraphQL 族谱成员更新输入 */
data class FamilyPersonUpdateInput(
    val name: String? = null,
    val gender: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val biography: String? = null,
    val fatherId: Int? = null,
    val sortOrder: Int? = null
)
