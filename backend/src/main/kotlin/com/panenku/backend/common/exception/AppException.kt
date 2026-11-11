package com.panenku.backend.common.exception

import org.springframework.http.HttpStatus

/**
 * Exception untuk kesalahan bisnis aplikasi.
 * Ditangkap oleh [ExceptionHandler] dan dikonversi menjadi JSON response.
 */
class AppException(
    override val message: String,
    val status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR
) : RuntimeException(message)