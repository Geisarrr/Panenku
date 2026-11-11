package com.panenku.backend.modules.shared.service

import com.panenku.backend.common.auth.JwtProvider
import com.panenku.backend.common.dto.UserDTO
import com.panenku.backend.common.exception.AppException
import com.panenku.backend.modules.shared.dto.RegisterRequest
import com.panenku.backend.modules.shared.entity.User
import com.panenku.backend.modules.shared.repository.UserRepository
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.http.HttpStatus

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider
) {

    private val passwordEncoder = BCryptPasswordEncoder()

    fun register(request: RegisterRequest): UserDTO {
        // Validasi email duplikat
        if (userRepository.existsByEmail(request.email)) {
            throw AppException("Email sudah terdaftar", HttpStatus.CONFLICT)
        }

        // Validasi role tidak boleh admin untuk pendaftaran publik
        if (request.role == "admin") {
            throw AppException("Tidak dapat mendaftar sebagai admin melalui formulir publik", HttpStatus.FORBIDDEN)
        }

        // Validasi nama toko wajib untuk petani
        if (request.role == "petani" && (request.storeName.isNullOrBlank())) {
            throw AppException("Nama toko wajib diisi untuk petani", HttpStatus.BAD_REQUEST)
        }

        // Buat user baru
        val user = User()
        user.email = request.email
        user.passwordHash = passwordEncoder.encode(request.password)
        user.role = request.role
        user.status = "active"

        val saved = userRepository.save(user)

        return UserDTO(
            id = saved.id!!,
            email = saved.email,
            role = saved.role,
            status = saved.status
        )
    }

    fun login(request: com.panenku.backend.modules.shared.dto.LoginRequest): String {
        val user = userRepository.findByEmail(request.email)
            ?: throw AppException("Email atau password salah", HttpStatus.UNAUTHORIZED)

        // Verifikasi password
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw AppException("Email atau password salah", HttpStatus.UNAUTHORIZED)
        }

        return jwtProvider.generateToken(user.email)
    }
}