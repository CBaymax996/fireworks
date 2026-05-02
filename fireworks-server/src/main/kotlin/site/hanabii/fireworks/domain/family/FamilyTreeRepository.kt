package site.hanabii.fireworks.domain.family

interface FamilyTreeRepository {
    fun save(tree: FamilyTree): FamilyTree
    fun findById(id: Long): FamilyTree?
    fun findAll(): List<FamilyTree>
    fun deleteById(id: Long): Boolean
}
