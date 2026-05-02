package site.hanabii.fireworks.domain.family

import java.time.Instant

data class PersonSpouse(
    val id: Long? = null,
    val husbandId: Long,
    val wifeId: Long,
    val marriageOrder: Int = 1,
    val isPrimary: Boolean = true,
    val createdAt: Instant = Instant.now()
)
