package site.hanabii.fireworks.infra.family

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import org.ktorm.schema.varchar
import site.hanabii.fireworks.domain.family.FamilyTree
import java.time.Instant

object FamilyTreeDO : Table<Nothing>("family_trees") {
    val id = long("id").primaryKey()
    val name = varchar("name")
    val description = varchar("description")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS family_trees (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            name        TEXT NOT NULL,
            description TEXT,
            created_at  TIMESTAMP NOT NULL,
            updated_at  TIMESTAMP NOT NULL
        )
    """
}

fun QueryRowSet.toFamilyTree(): FamilyTree = FamilyTree(
    id = this[FamilyTreeDO.id],
    name = this[FamilyTreeDO.name] ?: "",
    description = this[FamilyTreeDO.description],
    createdAt = this[FamilyTreeDO.createdAt] ?: Instant.now(),
    updatedAt = this[FamilyTreeDO.updatedAt] ?: Instant.now()
)
