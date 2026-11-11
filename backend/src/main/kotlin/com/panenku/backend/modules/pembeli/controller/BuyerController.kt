package com.panenku.backend.modules.pembeli.controller

import com.panenku.backend.common.dto.ApiResponse
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.pembeli.dto.*
import com.panenku.backend.modules.pembeli.service.*
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/buyer/cart")
class CartController(private val service: CartService, private val actors: ActorService) {
    @GetMapping fun get(@AuthenticationPrincipal jwt: Jwt) = ApiResponse(data = service.get(actors.id(jwt)))
    @PostMapping("/items") fun add(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: CartItemRequest) = ApiResponse(data = service.add(actors.id(jwt), request))
    @PutMapping("/items/{id}") fun update(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID, @RequestBody request: CartQuantityRequest) = ApiResponse(data = service.update(actors.id(jwt), id, request.quantityKg))
    @DeleteMapping("/items/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID) = service.delete(actors.id(jwt), id)
}

@RestController
@RequestMapping("/api/v1/buyer/checkout")
class CheckoutController(private val service: CheckoutService, private val actors: ActorService) {
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    fun checkout(@AuthenticationPrincipal jwt: Jwt, @RequestHeader("Idempotency-Key") key: String, @RequestBody request: CheckoutRequest) =
        ApiResponse(data = service.checkout(actors.id(jwt), key, request))
}
