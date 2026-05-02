package site.hanabii.fireworks.app.family

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import site.hanabii.fireworks.domain.family.FamilyPerson
import site.hanabii.fireworks.domain.family.FamilyTree
import site.hanabii.fireworks.domain.family.PersonSpouse

@RestController
@RequestMapping("/api/family-trees")
class FamilyTreeController(
    private val service: FamilyTreeService
) {

    // ========== 族谱 CRUD ==========

    @GetMapping
    fun listTrees(): List<FamilyTree> = service.listTrees()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createTree(@Valid @RequestBody req: CreateTreeRequest): FamilyTree {
        return service.createTree(req.name, req.description)
    }

    @GetMapping("/{treeId}")
    fun getTree(@PathVariable treeId: Long): FamilyTree = service.getTree(treeId)

    @PutMapping("/{treeId}")
    fun updateTree(@PathVariable treeId: Long, @Valid @RequestBody req: UpdateTreeRequest): FamilyTree {
        return service.updateTree(treeId, req.name, req.description)
    }

    @DeleteMapping("/{treeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTree(@PathVariable treeId: Long) {
        service.deleteTree(treeId)
    }

    // ========== 成员 CRUD ==========

    @GetMapping("/{treeId}/persons")
    fun listPersons(
        @PathVariable treeId: Long,
        @RequestParam(required = false) search: String?
    ): List<FamilyPerson> {
        return if (search != null) service.searchPersons(treeId, search)
        else service.listPersons(treeId)
    }

    @PostMapping("/{treeId}/persons")
    @ResponseStatus(HttpStatus.CREATED)
    fun addPerson(@PathVariable treeId: Long, @Valid @RequestBody req: CreatePersonRequest): FamilyPerson {
        return service.addPerson(treeId, req)
    }

    @GetMapping("/{treeId}/persons/{personId}")
    fun getPerson(@PathVariable treeId: Long, @PathVariable personId: Long): FamilyPerson {
        return service.getPerson(treeId, personId)
    }

    @PutMapping("/{treeId}/persons/{personId}")
    fun updatePerson(
        @PathVariable treeId: Long,
        @PathVariable personId: Long,
        @Valid @RequestBody req: UpdatePersonRequest
    ): FamilyPerson {
        return service.updatePerson(treeId, personId, req)
    }

    @DeleteMapping("/{treeId}/persons/{personId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePerson(@PathVariable treeId: Long, @PathVariable personId: Long) {
        service.deletePerson(treeId, personId)
    }

    // ========== 树结构查询 ==========

    @GetMapping("/{treeId}/roots")
    fun listRoots(@PathVariable treeId: Long): List<FamilyPerson> = service.listRoots(treeId)

    @GetMapping("/{treeId}/persons/{personId}/children")
    fun getChildren(@PathVariable treeId: Long, @PathVariable personId: Long): ChildrenResponse {
        return service.getChildren(treeId, personId)
    }

    @GetMapping("/{treeId}/persons/{personId}/ancestors")
    fun getAncestors(@PathVariable treeId: Long, @PathVariable personId: Long): List<FamilyPerson> {
        return service.getAncestors(treeId, personId)
    }

    // ========== 配偶管理 ==========

    @GetMapping("/{treeId}/persons/{husbandId}/spouses")
    fun getSpouses(@PathVariable treeId: Long, @PathVariable husbandId: Long): List<PersonSpouse> {
        return service.getSpouses(treeId, husbandId)
    }

    @PostMapping("/{treeId}/persons/{husbandId}/spouses")
    @ResponseStatus(HttpStatus.CREATED)
    fun addSpouse(
        @PathVariable treeId: Long,
        @PathVariable husbandId: Long,
        @Valid @RequestBody req: AddSpouseRequest
    ): PersonSpouse {
        return service.addSpouse(treeId, husbandId, req.wifeId, req.isPrimary)
    }

    @DeleteMapping("/{treeId}/persons/{husbandId}/spouses/{spouseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeSpouse(
        @PathVariable treeId: Long,
        @PathVariable husbandId: Long,
        @PathVariable spouseId: Long
    ) {
        service.removeSpouse(treeId, husbandId, spouseId)
    }
}

// ---------- 请求 DTO ----------

data class CreateTreeRequest(
    @field:NotBlank val name: String,
    val description: String? = null
)

data class UpdateTreeRequest(
    val name: String? = null,
    val description: String? = null
)

data class AddSpouseRequest(
    val wifeId: Long,
    val isPrimary: Boolean = true
)
