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
import site.hanabii.fireworks.domain.family.FamilyTree
import site.hanabii.fireworks.domain.family.FamilyTreeRepository
import java.time.Instant

@Repository
class FamilyTreeRepositoryImpl(
    private val database: Database
) : FamilyTreeRepository {

    override fun save(tree: FamilyTree): FamilyTree {
        val now = Instant.now()
        return if (tree.id == null) {
            database.insert(FamilyTreeDO) {
                set(it.name, tree.name)
                set(it.description, tree.description)
                set(it.createdAt, tree.createdAt)
                set(it.updatedAt, now)
            }
            val lastId = database.useConnection { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT last_insert_rowid()").use { rs ->
                        if (rs.next()) rs.getLong(1) else throw IllegalStateException("Failed to get last insert rowid")
                    }
                }
            }
            database.from(FamilyTreeDO)
                .select()
                .where { FamilyTreeDO.id eq lastId }
                .map { it.toFamilyTree() }
                .firstOrNull()
                ?: throw IllegalStateException("FamilyTree insert succeeded but cannot be loaded")
        } else {
            database.update(FamilyTreeDO) {
                set(it.name, tree.name)
                set(it.description, tree.description)
                set(it.updatedAt, now)
                where { it.id eq tree.id }
            }
            tree.copy(updatedAt = now)
        }
    }

    override fun findById(id: Long): FamilyTree? {
        return database.from(FamilyTreeDO)
            .select()
            .where { FamilyTreeDO.id eq id }
            .map { it.toFamilyTree() }
            .firstOrNull()
    }

    override fun findAll(): List<FamilyTree> {
        return database.from(FamilyTreeDO)
            .select()
            .orderBy(FamilyTreeDO.id.asc())
            .map { it.toFamilyTree() }
    }

    override fun deleteById(id: Long): Boolean {
        val affected = database.delete(FamilyTreeDO) { it.id eq id }
        return affected > 0
    }
}
