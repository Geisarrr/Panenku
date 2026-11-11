package com.panenku.backend.modules.admin.controller

import com.panenku.backend.common.dto.ApiResponse
import com.panenku.backend.modules.admin.dto.KtpVerifyRequest
import com.panenku.backend.modules.admin.service.KtpVerificationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/farmers")
class KtpVerificationController(
    private val ktpVerificationService: KtpVerificationService
) {

    @PutMapping("/{id}/verify")
    fun verifyKtp(
        @PathVariable id: UUID,
        @RequestBody request: KtpVerifyRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<ApiResponse<Unit>> {
        val response = ktpVerificationService.verifyKtp(id, UUID.fromString(jwt.subject), request)
        return ResponseEntity(response, HttpStatus.OK)
    }
}
