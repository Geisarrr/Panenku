package com.panenku.backend.modules.shared.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:Email @field:NotBlank @field:Size(max = 255) val email: String,
    @field:NotBlank val password: String
)