package com.panenku.backend.modules.pembeli.controller

import com.panenku.backend.common.dto.ApiResponse
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.pembeli.dto.ReviewRequest
import com.panenku.backend.modules.pembeli.service.ReviewService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/buyer/orders/{id}/review")
class BuyerReviewController(private val service: ReviewService, private val actors: ActorService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID, @RequestBody request: ReviewRequest) =
        ApiResponse(data = service.submit(actors.id(jwt), id, request))

    @GetMapping
    fun get(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID) =
        ApiResponse(data = service.get(actors.id(jwt), id))
}
