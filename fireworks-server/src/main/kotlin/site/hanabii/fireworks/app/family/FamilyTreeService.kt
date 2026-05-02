package site.hanabii.fireworks.app.family

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import site.hanabii.fireworks.app.AppException
import site.hanabii.fireworks.app.ErrorCode
import site.hanabii.fireworks.domain.family.FamilyLineageRepository
import site.hanabii.fireworks.domain.family.FamilyPerson
import site.hanabii.fireworks.domain.family.FamilyPersonRepository
import site.hanabii.fireworks.domain.family.FamilyTree
import site.hanabii.fireworks.domain.family.FamilyTreeRepository
import site.hanabii.fireworks.domain.family.PersonSpouse
import site.hanabii.fireworks.domain.family.PersonSpouseRepository

@Service
class FamilyTreeService(
    private val treeRepo: FamilyTreeRepository,
    private val personRepo: FamilyPersonRepository,
    private val spouseRepo: PersonSpouseRepository,
    private val lineageRepo: FamilyLineageRepository
) {

    // ========== 族谱 CRUD ==========

    fun listTrees(): List<FamilyTree> = treeRepo.findAll()

    fun createTree(name: String, description: String?): FamilyTree {
        return treeRepo.save(FamilyTree(name = name, description = description))
    }

    fun getTree(id: Long): FamilyTree {
        return treeRepo.findById(id)
            ?: throw AppException(ErrorCode.FAMILY_TREE_NOT_FOUND, HttpStatus.NOT_FOUND, "族谱不存在: $id")
    }

    fun updateTree(id: Long, name: String?, description: String?): FamilyTree {
        val existing = getTree(id)
        return treeRepo.save(existing.copy(
            name = name ?: existing.name,
            description = description ?: existing.description
        ))
    }

    fun deleteTree(id: Long) {
        getTree(id)
        lineageRepo.deleteByTreeId(id)
        personRepo.deleteByTreeId(id)
        treeRepo.deleteById(id)
    }

    // ========== 成员 CRUD ==========

    fun listPersons(treeId: Long): List<FamilyPerson> {
        getTree(treeId)
        return personRepo.findByTreeId(treeId)
    }

    fun listRoots(treeId: Long): List<FamilyPerson> {
        getTree(treeId)
        return personRepo.findByTreeId(treeId)
            .filter { it.fatherId == null && it.gender == "male" }
    }

    fun addPerson(treeId: Long, req: CreatePersonRequest): FamilyPerson {
        getTree(treeId)
        val fatherId = req.fatherId
        var genOrder = 0
        if (fatherId != null) {
            val father = personRepo.findById(fatherId)
                ?: throw AppException(ErrorCode.FAMILY_PERSON_NOT_FOUND, HttpStatus.NOT_FOUND, "父亲不存在: $fatherId")
            if (father.familyTreeId != treeId)
                throw AppException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "父亲不属于当前族谱")
            genOrder = father.generationOrder + 1
        }
        val person = personRepo.save(FamilyPerson(
            familyTreeId = treeId,
            name = req.name,
            gender = req.gender,
            birthDate = req.birthDate,
            deathDate = req.deathDate,
            biography = req.biography,
            fatherId = fatherId,
            generationOrder = genOrder,
            sortOrder = req.sortOrder
        ))
        lineageRepo.rebuildForPerson(person.id!!, fatherId)
        return person
    }

    fun getPerson(treeId: Long, personId: Long): FamilyPerson {
        getTree(treeId)
        return personRepo.findById(personId)
            ?: throw AppException(ErrorCode.FAMILY_PERSON_NOT_FOUND, HttpStatus.NOT_FOUND, "成员不存在: $personId")
    }

    fun updatePerson(treeId: Long, personId: Long, req: UpdatePersonRequest): FamilyPerson {
        val existing = getPerson(treeId, personId)
        val oldFatherId = existing.fatherId
        val newFatherId = req.fatherId ?: oldFatherId

        var genOrder = existing.generationOrder
        if (newFatherId != oldFatherId) {
            if (newFatherId != null) {
                val newFather = personRepo.findById(newFatherId)
                    ?: throw AppException(ErrorCode.FAMILY_PERSON_NOT_FOUND, HttpStatus.NOT_FOUND, "父亲不存在: $newFatherId")
                genOrder = newFather.generationOrder + 1
            } else {
                genOrder = 0
            }
        }

        val updated = personRepo.save(existing.copy(
            name = req.name ?: existing.name,
            gender = req.gender ?: existing.gender,
            birthDate = req.birthDate ?: existing.birthDate,
            deathDate = req.deathDate ?: existing.deathDate,
            biography = req.biography ?: existing.biography,
            fatherId = newFatherId,
            generationOrder = genOrder,
            sortOrder = req.sortOrder ?: existing.sortOrder
        ))

        if (newFatherId != oldFatherId) {
            lineageRepo.rebuildForPerson(personId, newFatherId)
        }
        return updated
    }

    fun deletePerson(treeId: Long, personId: Long) {
        getPerson(treeId, personId)
        // 将子孙的 fatherId 置空（他们将成为新的根节点）
        val children = personRepo.findByFatherId(personId)
        for (child in children) {
            personRepo.save(child.copy(fatherId = null, generationOrder = 0))
            lineageRepo.rebuildForPerson(child.id!!, null)
        }
        lineageRepo.deleteByPersonId(personId)
        personRepo.deleteById(personId)
    }

    fun getChildren(treeId: Long, personId: Long): ChildrenResponse {
        val person = getPerson(treeId, personId)
        val children = personRepo.findByFatherId(personId)
        val spouse = spouseRepo.findPrimaryByHusbandId(personId)?.let { s ->
            val wife = personRepo.findById(s.wifeId)
            wife?.let { SpouseInfo(id = it.id!!, name = it.name) }
        }
        return ChildrenResponse(
            person = PersonWithSpouse(
                id = person.id!!,
                name = person.name,
                gender = person.gender,
                birthDate = person.birthDate,
                deathDate = person.deathDate,
                biography = person.biography,
                fatherId = person.fatherId,
                generationOrder = person.generationOrder,
                sortOrder = person.sortOrder,
                spouse = spouse
            ),
            children = children.map {
                val id = it.id ?: error("person id must not be null")
                ChildInfo(
                    id = id,
                    name = it.name,
                    gender = it.gender,
                    sortOrder = it.sortOrder,
                    hasChildren = personRepo.findByFatherId(id).isNotEmpty()
                )
            }
        )
    }

    fun getAncestors(treeId: Long, personId: Long): List<FamilyPerson> {
        getPerson(treeId, personId)
        val result = mutableListOf<FamilyPerson>()
        var current = personRepo.findById(personId)
        while (current != null) {
            result.add(current)
            current = current.fatherId?.let { personRepo.findById(it) }
        }
        return result.reversed()
    }

    // ========== 配偶管理 ==========

    fun getSpouses(treeId: Long, husbandId: Long): List<PersonSpouse> {
        getPerson(treeId, husbandId)
        return spouseRepo.findByHusbandId(husbandId)
    }

    fun addSpouse(treeId: Long, husbandId: Long, wifeId: Long, isPrimary: Boolean): PersonSpouse {
        getPerson(treeId, husbandId)
        val wife = personRepo.findById(wifeId)
            ?: throw AppException(ErrorCode.FAMILY_PERSON_NOT_FOUND, HttpStatus.NOT_FOUND, "妻子不存在: $wifeId")
        if (wife.familyTreeId != treeId)
            throw AppException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "妻子不属于当前族谱")
        if (wife.gender != "female")
            throw AppException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "配偶必须是女性")
        val existing = spouseRepo.findByHusbandId(husbandId)
        val marriageOrder = existing.size + 1
        return spouseRepo.save(PersonSpouse(
            husbandId = husbandId,
            wifeId = wifeId,
            marriageOrder = marriageOrder,
            isPrimary = isPrimary
        ))
    }

    fun removeSpouse(treeId: Long, husbandId: Long, spouseId: Long) {
        getPerson(treeId, husbandId)
        val deleted = spouseRepo.deleteById(spouseId)
        if (!deleted)
            throw AppException(ErrorCode.FAMILY_PERSON_NOT_FOUND, HttpStatus.NOT_FOUND, "配偶关系不存在: $spouseId")
    }

    // ========== 搜索 ==========

    fun searchPersons(treeId: Long, search: String): List<FamilyPerson> {
        getTree(treeId)
        return personRepo.findByTreeId(treeId)
            .filter { it.name.contains(search, ignoreCase = true) }
    }
}

// ---------- 请求 / 响应 DTO ----------

data class CreatePersonRequest(
    val name: String,
    val gender: String = "unknown",
    val birthDate: String? = null,
    val deathDate: String? = null,
    val biography: String? = null,
    val fatherId: Long? = null,
    val sortOrder: Int = 0
)

data class UpdatePersonRequest(
    val name: String? = null,
    val gender: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val biography: String? = null,
    val fatherId: Long? = null,
    val sortOrder: Int? = null
)

data class SpouseInfo(
    val id: Long,
    val name: String
)

data class PersonWithSpouse(
    val id: Long,
    val name: String,
    val gender: String,
    val birthDate: String?,
    val deathDate: String?,
    val biography: String?,
    val fatherId: Long?,
    val generationOrder: Int,
    val sortOrder: Int,
    val spouse: SpouseInfo?
)

data class ChildInfo(
    val id: Long,
    val name: String,
    val gender: String,
    val sortOrder: Int,
    val hasChildren: Boolean
)

data class ChildrenResponse(
    val person: PersonWithSpouse,
    val children: List<ChildInfo>
)
