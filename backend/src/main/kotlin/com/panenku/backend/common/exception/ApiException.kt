package com.panenku.backend.common.exception

import org.springframework.http.HttpStatus
import java.time.Instant

class ApiException(val status: HttpStatus, val code: String, override val message: String) : RuntimeException(message)
fun invalid(message: String): Nothing = throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message)
fun conflict(message: String): Nothing = throw ApiException(HttpStatus.CONFLICT, "CONFLICT", message)
fun missing(message: String): Nothing = throw ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message)
fun forbidden(message: String): Nothing = throw ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message)
data class ErrorResponse(val success: Boolean = false, val code: String, val message: String, val timestamp: Instant = Instant.now())
