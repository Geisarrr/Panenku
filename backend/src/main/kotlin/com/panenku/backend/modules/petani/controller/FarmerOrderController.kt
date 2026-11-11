package com.panenku.backend.modules.petani.controller

import com.panenku.backend.common.dto.ApiResponse
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.shared.dto.FulfillmentUpdateRequest
import com.panenku.backend.modules.shared.dto.OrderRespondRequest
import com.panenku.backend.modules.shared.service.OrderLifecycleService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/petani/orders")
class FarmerOrderController(
    private val orders: OrderLifecycleService,
    private val actors: ActorService
) {

    @GetMapping
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ) = ApiResponse(data = orders.listForFarmer(actors.id(jwt), page, size))

    @GetMapping("/{id}")
    fun detail(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID
    ) = ApiResponse(data = orders.detailForFarmer(actors.id(jwt), id))

    @PutMapping("/{id}/respond")
    fun respond(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @RequestBody request: OrderRespondRequest
    ) = ApiResponse(data = orders.respondByFarmer(actors.id(jwt), id, request))

    @PutMapping("/{id}/fulfillment")
    fun fulfillment(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @RequestBody request: FulfillmentUpdateRequest
    ) = ApiResponse(data = orders.updateFulfillment(actors.id(jwt), id, request))
}
