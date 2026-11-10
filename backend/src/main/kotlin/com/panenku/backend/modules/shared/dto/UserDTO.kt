package com.panenku.backend.modules.shared.dto

import java.util.UUID

data class UserDTO(
    val id: UUID,
    val email: String,
    val role: String,
    val status: String
)