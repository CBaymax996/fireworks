package site.hanabii.fireworks.app

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import site.hanabii.fireworks.domain.auth.Account
import site.hanabii.fireworks.domain.auth.AccountRepository

/**
 * Web 账号认证服务。
 */
@Service
class AuthService(
    private val accountRepository: AccountRepository
) {

    private val passwordEncoder = BCryptPasswordEncoder()

    fun register(username: String, password: String): Account {
        require(username.isNotBlank()) { "Username must not be blank" }
        require(password.isNotBlank()) { "Password must not be blank" }
        if (accountRepository.findByUsername(username) != null) {
            throw AppException(
                code = ErrorCode.ACCOUNT_EXISTS,
                status = org.springframework.http.HttpStatus.CONFLICT,
                message = "Username already exists: $username"
            )
        }
        val hash = passwordEncoder.encode(password) ?: throw IllegalStateException("Failed to hash password")
        return accountRepository.save(Account(id = null, username = username, passwordHash = hash))
    }

    fun authenticate(username: String, password: String): Account? {
        val account = accountRepository.findByUsername(username) ?: return null
        return if (passwordEncoder.matches(password, account.passwordHash)) account else null
    }
}
