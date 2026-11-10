package com.panenku.backend.modules.shared.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:Email(message = "Format email tidak valid")
    @field:NotBlank(message = "Email wajib diisi")
    @field:Size(max = 255)
    val email: String,

    @field:Size(min = 8, message = "Password minimal 8 karakter")
    @field:NotBlank(message = "Password wajib diisi")
    val password: String,

    @field:NotBlank(message = "Role wajib diisi (pembeli atau petani)")
    val role: String,

    @field:Size(max = 255) val fullName: String? = null,
    @field:Size(max = 255) val storeName: String? = null,
    @field:Size(max = 20) val contactPhone: String? = null
)