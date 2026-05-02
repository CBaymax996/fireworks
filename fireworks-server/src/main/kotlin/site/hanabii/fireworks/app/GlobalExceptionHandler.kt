package site.hanabii.fireworks.app

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.Instant

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

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.joinToString("; ") {
            "${it.field}: ${it.defaultMessage}"
        }
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                code = ErrorCode.INVALID_REQUEST.name,
                message = message,
                path = request.requestURI,
                timestamp = Instant.now()
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
