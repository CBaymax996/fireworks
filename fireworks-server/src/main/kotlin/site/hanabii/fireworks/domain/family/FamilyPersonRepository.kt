package site.hanabii.fireworks.domain.family

interface FamilyPersonRepository {
    fun save(person: FamilyPerson): FamilyPerson
    fun findById(id: Long): FamilyPerson?
    fun findByTreeId(treeId: Long): List<FamilyPerson>
    fun findByFatherId(fatherId: Long): List<FamilyPerson>
    fun countByTreeId(treeId: Long): Long
    fun deleteById(id: Long): Boolean
    fun deleteByTreeId(treeId: Long)
}
