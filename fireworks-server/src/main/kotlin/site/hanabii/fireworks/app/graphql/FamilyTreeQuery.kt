package site.hanabii.fireworks.app.graphql

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import org.springframework.stereotype.Component
import site.hanabii.fireworks.app.family.ChildrenResponse
import site.hanabii.fireworks.app.family.FamilyTreeService
import site.hanabii.fireworks.domain.family.FamilyPerson
import site.hanabii.fireworks.domain.family.FamilyTree
import site.hanabii.fireworks.domain.family.PersonSpouse

/**
 * 族谱查询 — GraphQL Query resolver。
 */
@Component
class FamilyTreeQuery(
    private val familyTreeService: FamilyTreeService
) {
    @GraphQLDescription("列出所有族谱")
    fun familyTrees(): List<FamilyTree> = familyTreeService.listTrees()

    @GraphQLDescription("获取单个族谱")
    fun familyTree(id: Long): FamilyTree = familyTreeService.getTree(id)

    @GraphQLDescription("列出族谱中的所有成员（可选搜索）")
    fun familyPersons(treeId: Long, search: String? = null): List<FamilyPerson> {
        return if (search.isNullOrBlank()) {
            familyTreeService.listPersons(treeId)
        } else {
            familyTreeService.searchPersons(treeId, search)
        }
    }

    @GraphQLDescription("获取族谱中的单个成员")
    fun familyPerson(treeId: Long, personId: Long): FamilyPerson {
        return familyTreeService.getPerson(treeId, personId)
    }

    @GraphQLDescription("列出族谱中的根节点（男性且无父节点）")
    fun familyRoots(treeId: Long): List<FamilyPerson> {
        return familyTreeService.listRoots(treeId)
    }

    @GraphQLDescription("获取成员的子女列表")
    fun familyChildren(treeId: Long, personId: Long): ChildrenResponse {
        return familyTreeService.getChildren(treeId, personId)
    }

    @GraphQLDescription("获取成员的祖先链（从远到近）")
    fun familyAncestors(treeId: Long, personId: Long): List<FamilyPerson> {
        return familyTreeService.getAncestors(treeId, personId)
    }

    @GraphQLDescription("获取成员的配偶列表")
    fun familySpouses(treeId: Long, husbandId: Long): List<PersonSpouse> {
        return familyTreeService.getSpouses(treeId, husbandId)
    }
}
