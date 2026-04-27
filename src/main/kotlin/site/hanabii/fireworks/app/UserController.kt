package site.hanabii.fireworks.app

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import site.hanabii.fireworks.domain.User
import site.hanabii.fireworks.domain.UserRepository

import java.time.Instant

@RestController
@RequestMapping("/users")
class UserController(
    private val userRepository: UserRepository
) {

    @GetMapping
    fun listUsers(): List<User> = userRepository.findAll()

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): User {
        return userRepository.findById(id)
            ?: throw AppException(
                code = ErrorCode.USER_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "User not found: $id"
            )
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@RequestBody req: CreateUserRequest): User {
        req.validate()
        return userRepository.save(User(username = req.username, email = req.email))
    }

    @PutMapping("/{id}")
    fun updateUser(@PathVariable id: Long, @RequestBody req: UpdateUserRequest): User {
        req.validate()
        val existing = userRepository.findById(id)
            ?: throw AppException(
                code = ErrorCode.USER_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "User not found: $id"
            )
        return userRepository.save(existing.copy(username = req.username, email = req.email))
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(@PathVariable id: Long) {
        val deleted = userRepository.deleteById(id)
        if (!deleted) {
            throw AppException(
                code = ErrorCode.USER_NOT_FOUND,
                status = HttpStatus.NOT_FOUND,
                message = "User not found: $id"
            )
        }
    }
}

data class CreateUserRequest(
    val username: String,
    val email: String
)

data class UpdateUserRequest(
    val username: String,
    val email: String
)

private fun CreateUserRequest.validate() {
    if (username.isBlank() || email.isBlank()) {
        throw AppException(
            code = ErrorCode.INVALID_REQUEST,
            status = HttpStatus.BAD_REQUEST,
            message = "username and email must not be blank"
        )
    }
}

private fun UpdateUserRequest.validate() {
    if (username.isBlank() || email.isBlank()) {
        throw AppException(
            code = ErrorCode.INVALID_REQUEST,
            status = HttpStatus.BAD_REQUEST,
            message = "username and email must not be blank"
        )
    }
}

enum class ErrorCode {
    USER_NOT_FOUND,
    INVALID_REQUEST,
    INTERNAL_ERROR
}

class AppException(
    val code: ErrorCode,
    val status: HttpStatus,
    override val message: String
) : RuntimeException(message)

data class ErrorResponse(
    val code: String,
    val message: String,
    val path: String,
    val timestamp: Instant = Instant.now()
)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(AppException::class)
    fun handleAppException(ex: AppException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(ex.status).body(
            ErrorResponse(
                code = ex.code.name,
                message = ex.message,
                path = request.requestURI
            )
        )
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        IllegalArgumentException::class
    )
    fun handleBadRequest(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                code = ErrorCode.INVALID_REQUEST.name,
                message = ex.message ?: "Invalid request",
                path = request.requestURI
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                code = ErrorCode.INTERNAL_ERROR.name,
                message = "Internal server error",
                path = request.requestURI
            )
        )
    }
}