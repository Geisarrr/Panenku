package com.panenku.backend.modules.shared.controller

import com.panenku.backend.common.dto.ApiResponse
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.shared.service.NotificationService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(private val notifications: NotificationService, private val actors: ActorService) {
    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt, @RequestParam(defaultValue = "0") page: Int,
             @RequestParam(defaultValue = "20") size: Int, @RequestParam(defaultValue = "false") unreadOnly: Boolean) =
        ApiResponse(data = notifications.list(actors.id(jwt), page, size, unreadOnly))

    @PutMapping("/{id}/read")
    fun read(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID) =
        ApiResponse(data = notifications.markRead(actors.id(jwt), id))

    @PutMapping("/read-all")
    fun readAll(@AuthenticationPrincipal jwt: Jwt) = ApiResponse(data = notifications.markAllRead(actors.id(jwt)))
}
