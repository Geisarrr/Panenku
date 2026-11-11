package com.panenku.backend.modules.admin.dto

import java.time.LocalDateTime
import java.util.UUID

data class AuditLogDTO(
    val id: UUID,
    val actorId: UUID,
    val actorRole: String,
    val action: String,
    val targetType: String?,
    val targetId: UUID?,
    val oldValues: String?,
    val newValues: String?,
    val reason: String?,
    val createdAt: LocalDateTime
)