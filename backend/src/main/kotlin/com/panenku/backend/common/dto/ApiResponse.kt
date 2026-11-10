package com.panenku.backend.common.dto

import java.time.Instant

/**
 * Wrapper respons JSON standar untuk semua endpoint API.
 *
 * Setiap controller harus mengembalikan objek ini agar respons konsisten di seluruh aplikasi.
 *
 * Contoh respons sukses:
 * {
 *   "success": true,
 *   "data": { ... },
 *   "message": "Login berhasil",
 *   "timestamp": "2026-11-10T10:00:00"
 * }
 *
 * Contoh respons error:
 * {
 *   "success": false,
 *   "data": null,
 *   "message": "Email sudah terdaftar",
 *   "timestamp": "2026-11-10T10:05:00"
 * }
 */
data class ApiResponse<T>(
    val success: Boolean = true,
    val data: T? = null,
    val message: String = "Berhasil",
    val timestamp: Instant = Instant.now()
)

/**
 * Factory function untuk membuat respons sukses dengan data.
 */
fun <T> successResponse(data: T, message: String = "Berhasil"): ApiResponse<T> {
    return ApiResponse(
        success = true,
        data = data,
        message = message
    )
}

/**
 * Factory function untuk membuat respons sukses tanpa data.
 */
fun <T> successResponse(message: String = "Berhasil"): ApiResponse<T> {
    return ApiResponse(
        success = true,
        data = null,
        message = message
    )
}

/**
 * Factory function untuk membuat respons error.
 */
fun <T> errorResponse(message: String): ApiResponse<T> {
    return ApiResponse(
        success = false,
        data = null,
        message = message
    )
}
