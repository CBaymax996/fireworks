package site.hanabii.fireworks.infra.family

import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.asc
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.lessEq
import org.ktorm.dsl.limit
import org.ktorm.dsl.map
import org.ktorm.dsl.offset
import org.ktorm.dsl.orderBy
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import org.springframework.stereotype.Repository
import site.hanabii.fireworks.domain.family.FamilyLineage
import site.hanabii.fireworks.domain.family.FamilyLineageRepository

@Repository
class FamilyLineageRepositoryImpl(
    private val database: Database
) : FamilyLineageRepository {

    override fun insert(rootId: Long, personId: Long, depth: Int) {
        database.insert(FamilyLineageDO) {
            set(it.rootId, rootId)
            set(it.personId, personId)
            set(it.depth, depth)
        }
    }

    override fun deleteByPersonId(personId: Long) {
        database.delete(FamilyLineageDO) { it.personId eq personId }
    }

    override fun deleteByTreeId(treeId: Long) {
        database.useConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM family_lineages WHERE root_id IN (SELECT id FROM family_persons WHERE family_tree_id = $treeId)")
            }
        }
    }

    override fun findByRootId(rootId: Long, maxDepth: Int?, offset: Int, size: Int): List<FamilyLineage> {
        val query = database.from(FamilyLineageDO).select()
        val filtered = if (maxDepth != null) {
            query.where { (FamilyLineageDO.rootId eq rootId) and (FamilyLineageDO.depth lessEq maxDepth) }
        } else {
            query.where { FamilyLineageDO.rootId eq rootId }
        }
        return filtered
            .orderBy(FamilyLineageDO.depth.asc())
            .limit(size)
            .offset(offset)
            .map { it.toFamilyLineage() }
    }

    override fun countByRootId(rootId: Long, maxDepth: Int?): Long {
        val query = database.from(FamilyLineageDO).select()
        val filtered = if (maxDepth != null) {
            query.where { (FamilyLineageDO.rootId eq rootId) and (FamilyLineageDO.depth lessEq maxDepth) }
        } else {
            query.where { FamilyLineageDO.rootId eq rootId }
        }
        return filtered.totalRecordsInAllPages.toLong()
    }

    override fun rebuildForPerson(personId: Long, fatherId: Long?) {
        deleteByPersonId(personId)
        if (fatherId != null) {
            val parentLineages = database.from(FamilyLineageDO)
                .select()
                .where { FamilyLineageDO.personId eq fatherId }
                .map { it.toFamilyLineage() }
            for (lineage in parentLineages) {
                insert(lineage.rootId, personId, lineage.depth + 1)
            }
        }
        // 始终插入自身作为根记录（depth=0，rootId=自身）
        insert(personId, personId, 0)
    }
}
