package site.hanabii.fireworks.infra.family

import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.asc
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.map
import org.ktorm.dsl.orderBy
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import org.springframework.stereotype.Repository
import site.hanabii.fireworks.domain.family.PersonSpouse
import site.hanabii.fireworks.domain.family.PersonSpouseRepository
import java.time.Instant

@Repository
class PersonSpouseRepositoryImpl(
    private val database: Database
) : PersonSpouseRepository {

    override fun save(spouse: PersonSpouse): PersonSpouse {
        return if (spouse.id == null) {
            database.insert(PersonSpouseDO) {
                set(it.husbandId, spouse.husbandId)
                set(it.wifeId, spouse.wifeId)
                set(it.marriageOrder, spouse.marriageOrder)
                set(it.isPrimary, if (spouse.isPrimary) 1 else 0)
                set(it.createdAt, spouse.createdAt)
            }
            val lastId = database.useConnection { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT last_insert_rowid()").use { rs ->
                        if (rs.next()) rs.getLong(1) else throw IllegalStateException("Failed to get last insert rowid")
                    }
                }
            }
            spouse.copy(id = lastId)
        } else {
            spouse // 配偶关系不支持更新，只增删
        }
    }

    override fun findByHusbandId(husbandId: Long): List<PersonSpouse> {
        return database.from(PersonSpouseDO)
            .select()
            .where { PersonSpouseDO.husbandId eq husbandId }
            .orderBy(PersonSpouseDO.marriageOrder.asc())
            .map { it.toPersonSpouse() }
    }

    override fun findPrimaryByHusbandId(husbandId: Long): PersonSpouse? {
        return database.from(PersonSpouseDO)
            .select()
            .where { (PersonSpouseDO.husbandId eq husbandId) and (PersonSpouseDO.isPrimary eq 1) }
            .map { it.toPersonSpouse() }
            .firstOrNull()
    }

    override fun deleteById(id: Long): Boolean {
        val affected = database.delete(PersonSpouseDO) { it.id eq id }
        return affected > 0
    }
}
