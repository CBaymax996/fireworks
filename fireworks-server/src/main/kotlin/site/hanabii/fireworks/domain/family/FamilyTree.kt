package site.hanabii.fireworks.domain.family

import java.time.Instant

data class FamilyTree(
    val id: Long? = null,
    val name: String,
    val description: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
    }
}
