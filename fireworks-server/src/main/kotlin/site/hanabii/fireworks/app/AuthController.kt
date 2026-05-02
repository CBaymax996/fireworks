package site.hanabii.fireworks.app

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import site.hanabii.fireworks.domain.auth.Account

@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证", description = "Web 登录注册接口")
class AuthController(
    private val authService: AuthService,
    private val authSessionService: AuthSessionService
) {

    @Operation(summary = "用户注册", description = "注册新账号，用户名唯一，密码使用 BCrypt 哈希存储")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "注册成功",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = AccountResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "请求参数错误（用户名或密码为空）",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "409",
                description = "用户名已存在",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @Parameter(description = "注册请求", required = true)
        @RequestBody req: AuthRequest,
        session: HttpSession
    ): AccountResponse {
        req.validate()
        val account = authService.register(req.username, req.password)
        authSessionService.storeAccountId(session, account.id!!)
        return AccountResponse(id = account.id, username = account.username, createdAt = account.createdAt.toString())
    }

    @Operation(summary = "用户登录", description = "验证账号密码，成功后创建服务端 Session")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "登录成功",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = LoginResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "用户名或密码错误",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
            )
        ]
    )
    @PostMapping("/login")
    fun login(
        @Parameter(description = "登录请求", required = true)
        @RequestBody req: AuthRequest,
        session: HttpSession
    ): ResponseEntity<LoginResponse> {
        req.validate()
        val account = authService.authenticate(req.username, req.password)
            ?: throw AppException(
                code = ErrorCode.INVALID_CREDENTIALS,
                status = HttpStatus.UNAUTHORIZED,
                message = "Invalid username or password"
            )
        authSessionService.storeAccountId(session, account.id!!)
        return ResponseEntity.ok(
            LoginResponse(
                success = true,
                account = AccountResponse(id = account.id, username = account.username, createdAt = account.createdAt.toString())
            )
        )
    }

    @Operation(summary = "用户登出", description = "清除当前会话")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "登出成功")
        ]
    )
    @PostMapping("/logout")
    fun logout(session: HttpSession): ResponseEntity<Map<String, Boolean>> {
        authSessionService.clear(session)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @Operation(summary = "获取当前登录状态", description = "查询当前 Session 是否已登录")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = AuthStatusResponse::class))]
            )
        ]
    )
    @GetMapping("/status")
    fun status(session: HttpSession): AuthStatusResponse {
        val accountId = authSessionService.getAccountId(session)
        return AuthStatusResponse(
            authenticated = accountId != null,
            accountId = accountId
        )
    }
}

data class AuthRequest(
    val username: String,
    val password: String
)

private fun AuthRequest.validate() {
    if (username.isBlank() || password.isBlank()) {
        throw AppException(
            code = ErrorCode.INVALID_REQUEST,
            status = HttpStatus.BAD_REQUEST,
            message = "username and password must not be blank"
        )
    }
}

data class AccountResponse(
    val id: Long,
    val username: String,
    val createdAt: String
)

data class LoginResponse(
    val success: Boolean,
    val account: AccountResponse
)

data class AuthStatusResponse(
    val authenticated: Boolean,
    val accountId: Long?
)
