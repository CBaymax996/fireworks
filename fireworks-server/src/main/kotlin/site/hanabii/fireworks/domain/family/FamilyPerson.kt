package site.hanabii.fireworks.domain.family

import java.time.Instant

data class FamilyPerson(
    val id: Long? = null,
    val familyTreeId: Long,
    val name: String,
    val gender: String = "unknown",
    val birthDate: String? = null,
    val deathDate: String? = null,
    val biography: String? = null,
    val fatherId: Long? = null,
    val generationOrder: Int = 0,
    val sortOrder: Int = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(gender in setOf("male", "female", "unknown")) {
            "gender must be male, female or unknown, got $gender"
        }
    }
}
