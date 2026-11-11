package com.panenku.backend.modules.petani.controller

import com.panenku.backend.common.dto.ApiResponse
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.petani.dto.ProductRequest
import com.panenku.backend.modules.petani.service.ProductService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/petani/products")
class ProductController(private val service: ProductService, private val actors: ActorService) {
    @GetMapping fun list(@AuthenticationPrincipal jwt: Jwt, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int) = ApiResponse(data = service.list(actors.id(jwt), page, size))
    @GetMapping("/{id}") fun detail(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID) = ApiResponse(data = service.detail(actors.id(jwt), id))
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: ProductRequest) = ApiResponse(data = service.save(actors.id(jwt), null, request))
    @PutMapping("/{id}") fun update(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID, @RequestBody request: ProductRequest) = ApiResponse(data = service.save(actors.id(jwt), id, request))
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID) = service.deactivate(actors.id(jwt), id)
}
