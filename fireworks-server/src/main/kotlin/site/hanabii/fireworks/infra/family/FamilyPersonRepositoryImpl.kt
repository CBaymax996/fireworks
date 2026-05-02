package site.hanabii.fireworks.infra.family

import org.ktorm.database.Database
import org.ktorm.dsl.asc
import org.ktorm.dsl.delete
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.insert
import org.ktorm.dsl.map
import org.ktorm.dsl.orderBy
import org.ktorm.dsl.select
import org.ktorm.dsl.update
import org.ktorm.dsl.where
import org.springframework.stereotype.Repository
import site.hanabii.fireworks.domain.family.FamilyPerson
import site.hanabii.fireworks.domain.family.FamilyPersonRepository
import java.time.Instant

@Repository
class FamilyPersonRepositoryImpl(
    private val database: Database
) : FamilyPersonRepository {

    override fun save(person: FamilyPerson): FamilyPerson {
        val now = Instant.now()
        return if (person.id == null) {
            database.insert(FamilyPersonDO) {
                set(it.familyTreeId, person.familyTreeId)
                set(it.name, person.name)
                set(it.gender, person.gender)
                set(it.birthDate, person.birthDate)
                set(it.deathDate, person.deathDate)
                set(it.biography, person.biography)
                set(it.fatherId, person.fatherId)
                set(it.generationOrder, person.generationOrder)
                set(it.sortOrder, person.sortOrder)
                set(it.createdAt, person.createdAt)
                set(it.updatedAt, now)
            }
            val lastId = database.useConnection { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT last_insert_rowid()").use { rs ->
                        if (rs.next()) rs.getLong(1) else throw IllegalStateException("Failed to get last insert rowid")
                    }
                }
            }
            database.from(FamilyPersonDO)
                .select()
                .where { FamilyPersonDO.id eq lastId }
                .map { it.toFamilyPerson() }
                .firstOrNull()
                ?: throw IllegalStateException("FamilyPerson insert succeeded but cannot be loaded")
        } else {
            database.update(FamilyPersonDO) {
                set(it.familyTreeId, person.familyTreeId)
                set(it.name, person.name)
                set(it.gender, person.gender)
                set(it.birthDate, person.birthDate)
                set(it.deathDate, person.deathDate)
                set(it.biography, person.biography)
                set(it.fatherId, person.fatherId)
                set(it.generationOrder, person.generationOrder)
                set(it.sortOrder, person.sortOrder)
                set(it.updatedAt, now)
                where { it.id eq person.id }
            }
            person.copy(updatedAt = now)
        }
    }

    override fun findById(id: Long): FamilyPerson? {
        return database.from(FamilyPersonDO)
            .select()
            .where { FamilyPersonDO.id eq id }
            .map { it.toFamilyPerson() }
            .firstOrNull()
    }

    override fun findByTreeId(treeId: Long): List<FamilyPerson> {
        return database.from(FamilyPersonDO)
            .select()
            .where { FamilyPersonDO.familyTreeId eq treeId }
            .map { it.toFamilyPerson() }
    }

    override fun findByFatherId(fatherId: Long): List<FamilyPerson> {
        return database.from(FamilyPersonDO)
            .select()
            .where { FamilyPersonDO.fatherId eq fatherId }
            .orderBy(FamilyPersonDO.sortOrder.asc())
            .map { it.toFamilyPerson() }
    }

    override fun countByTreeId(treeId: Long): Long {
        return database.from(FamilyPersonDO)
            .select()
            .where { FamilyPersonDO.familyTreeId eq treeId }
            .totalRecordsInAllPages.toLong()
    }

    override fun deleteById(id: Long): Boolean {
        val affected = database.delete(FamilyPersonDO) { it.id eq id }
        return affected > 0
    }

    override fun deleteByTreeId(treeId: Long) {
        database.delete(FamilyPersonDO) { it.familyTreeId eq treeId }
    }
}
