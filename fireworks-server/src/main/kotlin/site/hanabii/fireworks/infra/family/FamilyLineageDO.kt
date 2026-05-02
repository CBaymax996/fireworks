package site.hanabii.fireworks.infra.family

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.long
import site.hanabii.fireworks.domain.family.FamilyLineage

object FamilyLineageDO : Table<Nothing>("family_lineages") {
    val rootId = long("root_id")
    val personId = long("person_id")
    val depth = int("depth")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS family_lineages (
            root_id   INTEGER NOT NULL,
            person_id INTEGER NOT NULL,
            depth     INTEGER NOT NULL DEFAULT 0,
            UNIQUE(root_id, person_id)
        )
    """
}

fun QueryRowSet.toFamilyLineage(): FamilyLineage = FamilyLineage(
    rootId = this[FamilyLineageDO.rootId] ?: 0L,
    personId = this[FamilyLineageDO.personId] ?: 0L,
    depth = this[FamilyLineageDO.depth] ?: 0
)
