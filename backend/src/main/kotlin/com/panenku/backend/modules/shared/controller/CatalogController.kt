package com.panenku.backend.modules.shared.controller

import com.panenku.backend.common.dto.ApiResponse
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.shared.dto.CommodityRequest
import com.panenku.backend.modules.shared.service.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
import java.util.UUID

@RestController
@RequestMapping("/api/v1/buyer")
class CatalogController(private val catalog: CatalogService) {
    @GetMapping("/products")
    fun products(@RequestParam(defaultValue = "") search: String, @RequestParam(required = false) commodityId: UUID?,
                 @RequestParam(defaultValue = "price_asc") sort: String, @RequestParam(defaultValue = "0") page: Int,
                 @RequestParam(defaultValue = "20") size: Int) = ApiResponse(data = catalog.list(search, commodityId, sort, page, size))
    @GetMapping("/products/{id}") fun detail(@PathVariable id: UUID) = ApiResponse(data = catalog.detail(id))
    @GetMapping("/commodities") fun commodities() = ApiResponse(data = catalog.commodities())
}

@RestController
@RequestMapping("/api/v1/admin/commodities")
class CommodityController(private val service: CommodityService, private val actors: ActorService) {
    @GetMapping fun list(@AuthenticationPrincipal jwt: Jwt) = ApiResponse(data = service.list(actors.id(jwt)))
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: CommodityRequest) = ApiResponse(data = service.save(actors.id(jwt), null, request))
    @PutMapping("/{id}") fun update(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID, @RequestBody request: CommodityRequest) = ApiResponse(data = service.save(actors.id(jwt), id, request))
}
