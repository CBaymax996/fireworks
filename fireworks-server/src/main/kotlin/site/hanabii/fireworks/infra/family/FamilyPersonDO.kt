package site.hanabii.fireworks.infra.family

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import org.ktorm.schema.varchar
import site.hanabii.fireworks.domain.family.FamilyPerson
import java.time.Instant

object FamilyPersonDO : Table<Nothing>("family_persons") {
    val id = long("id").primaryKey()
    val familyTreeId = long("family_tree_id")
    val name = varchar("name")
    val gender = varchar("gender")
    val birthDate = varchar("birth_date")
    val deathDate = varchar("death_date")
    val biography = varchar("biography")
    val fatherId = long("father_id")
    val generationOrder = int("generation_order")
    val sortOrder = int("sort_order")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS family_persons (
            id               INTEGER PRIMARY KEY AUTOINCREMENT,
            family_tree_id   INTEGER NOT NULL,
            name             TEXT    NOT NULL,
            gender           TEXT    NOT NULL DEFAULT 'unknown',
            birth_date       TEXT,
            death_date       TEXT,
            biography        TEXT,
            father_id        INTEGER,
            generation_order INTEGER NOT NULL DEFAULT 0,
            sort_order       INTEGER NOT NULL DEFAULT 0,
            created_at       TIMESTAMP NOT NULL,
            updated_at       TIMESTAMP NOT NULL
        )
    """
}

fun QueryRowSet.toFamilyPerson(): FamilyPerson = FamilyPerson(
    id = this[FamilyPersonDO.id],
    familyTreeId = this[FamilyPersonDO.familyTreeId] ?: 0L,
    name = this[FamilyPersonDO.name] ?: "",
    gender = this[FamilyPersonDO.gender] ?: "unknown",
    birthDate = this[FamilyPersonDO.birthDate],
    deathDate = this[FamilyPersonDO.deathDate],
    biography = this[FamilyPersonDO.biography],
    fatherId = this[FamilyPersonDO.fatherId],
    generationOrder = this[FamilyPersonDO.generationOrder] ?: 0,
    sortOrder = this[FamilyPersonDO.sortOrder] ?: 0,
    createdAt = this[FamilyPersonDO.createdAt] ?: Instant.now(),
    updatedAt = this[FamilyPersonDO.updatedAt] ?: Instant.now()
)
