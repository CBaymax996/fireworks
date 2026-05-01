package site.hanabii.fireworks.domain

/**
 * 密码条目仓储接口。
 */
interface PasswordEntryRepository {
    fun save(entry: PasswordEntry): PasswordEntry
    fun findById(id: Long): PasswordEntry?
    fun findAll(): List<PasswordEntry>
    fun deleteById(id: Long): Boolean
    fun count(): Long
}
