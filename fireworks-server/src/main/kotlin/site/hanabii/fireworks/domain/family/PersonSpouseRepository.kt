package site.hanabii.fireworks.domain.family

interface PersonSpouseRepository {
    fun save(spouse: PersonSpouse): PersonSpouse
    fun findByHusbandId(husbandId: Long): List<PersonSpouse>
    fun findPrimaryByHusbandId(husbandId: Long): PersonSpouse?
    fun deleteById(id: Long): Boolean
}
