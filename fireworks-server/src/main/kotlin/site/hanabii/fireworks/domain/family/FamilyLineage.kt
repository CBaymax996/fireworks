package site.hanabii.fireworks.domain.family

data class FamilyLineage(
    val rootId: Long,
    val personId: Long,
    val depth: Int
)
