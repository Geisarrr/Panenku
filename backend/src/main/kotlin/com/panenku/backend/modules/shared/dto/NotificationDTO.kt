package com.panenku.backend.modules.shared.dto

import java.time.Instant
import java.util.UUID

data class NotificationDTO(
    val id: UUID,
    val title: String,
    val body: String?,
    val type: String,
    val isRead: Boolean,
    val deepLink: String?,
    val createdAt: Instant
)

data class NotificationPageDTO(
    val items: List<NotificationDTO>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val unreadCount: Long
)

data class NotificationsMarkedReadDTO(val updatedCount: Int)
