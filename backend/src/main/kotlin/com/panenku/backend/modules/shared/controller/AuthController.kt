package com.panenku.backend.modules.shared.controller

import com.panenku.backend.common.dto.ApiResponse
import com.panenku.backend.common.dto.successResponse
import com.panenku.backend.modules.shared.dto.LoginRequest
import com.panenku.backend.modules.shared.dto.RegisterRequest
import com.panenku.backend.modules.shared.dto.UserDTO
import com.panenku.backend.modules.shared.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<ApiResponse<UserDTO>> {
        val userDTO = authService.register(request)
        return ResponseEntity(
            successResponse(userDTO, "Registrasi berhasil"),
            HttpStatus.CREATED
        )
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<ApiResponse<String>> {
        val token = authService.login(request)
        return ResponseEntity(
            successResponse(token, "Login berhasil"),
            HttpStatus.OK
        )
    }
}