package com.panenku.backend.modules.pembeli.controller

import com.panenku.backend.common.dto.ApiResponse
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.shared.dto.OrderCancelRequest
import com.panenku.backend.modules.shared.service.OrderLifecycleService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/buyer/orders")
class BuyerOrderController(
    private val orders: OrderLifecycleService,
    private val actors: ActorService
) {

    @GetMapping
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ) = ApiResponse(data = orders.listForBuyer(actors.id(jwt), page, size))

    @GetMapping("/{id}")
    fun detail(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID
    ) = ApiResponse(data = orders.detailForBuyer(actors.id(jwt), id))

    @PostMapping("/{id}/cancel")
    fun cancel(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @RequestBody(required = false) request: OrderCancelRequest?
    ) = ApiResponse(data = orders.cancelByBuyer(actors.id(jwt), id, request ?: OrderCancelRequest()))

    @PostMapping("/{id}/receive")
    fun receive(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID
    ) = ApiResponse(data = orders.receiveByBuyer(actors.id(jwt), id))
}
