package site.hanabii.fireworks.domain.family

interface FamilyLineageRepository {
    fun insert(rootId: Long, personId: Long, depth: Int)
    fun deleteByPersonId(personId: Long)
    fun deleteByTreeId(treeId: Long)
    fun findByRootId(rootId: Long, maxDepth: Int?, offset: Int, size: Int): List<FamilyLineage>
    fun countByRootId(rootId: Long, maxDepth: Int?): Long
    fun rebuildForPerson(personId: Long, fatherId: Long?)
}
