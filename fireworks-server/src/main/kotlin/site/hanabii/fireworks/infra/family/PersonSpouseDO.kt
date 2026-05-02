package site.hanabii.fireworks.infra.family

import org.ktorm.dsl.QueryRowSet
import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.long
import org.ktorm.schema.timestamp
import site.hanabii.fireworks.domain.family.PersonSpouse
import java.time.Instant

object PersonSpouseDO : Table<Nothing>("person_spouses") {
    val id = long("id").primaryKey()
    val husbandId = long("husband_id")
    val wifeId = long("wife_id")
    val marriageOrder = int("marriage_order")
    val isPrimary = int("is_primary")
    val createdAt = timestamp("created_at")

    const val DDL: String = """
        CREATE TABLE IF NOT EXISTS person_spouses (
            id             INTEGER PRIMARY KEY AUTOINCREMENT,
            husband_id     INTEGER NOT NULL,
            wife_id        INTEGER NOT NULL,
            marriage_order INTEGER NOT NULL DEFAULT 1,
            is_primary     INTEGER NOT NULL DEFAULT 1,
            created_at     TIMESTAMP NOT NULL,
            UNIQUE(husband_id, wife_id)
        )
    """
}

fun QueryRowSet.toPersonSpouse(): PersonSpouse = PersonSpouse(
    id = this[PersonSpouseDO.id],
    husbandId = this[PersonSpouseDO.husbandId] ?: 0L,
    wifeId = this[PersonSpouseDO.wifeId] ?: 0L,
    marriageOrder = this[PersonSpouseDO.marriageOrder] ?: 1,
    isPrimary = (this[PersonSpouseDO.isPrimary] ?: 1) != 0,
    createdAt = this[PersonSpouseDO.createdAt] ?: Instant.now()
)
