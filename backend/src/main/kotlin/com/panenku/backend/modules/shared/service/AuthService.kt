package com.panenku.backend.modules.shared.service

import com.panenku.backend.common.auth.JwtProvider
import com.panenku.backend.common.exception.AppException
import com.panenku.backend.modules.shared.dto.*
import com.panenku.backend.modules.shared.entity.*
import com.panenku.backend.modules.shared.repository.*
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.Locale

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository, private val jwtProvider: JwtProvider,
    private val farmers: FarmerProfileRepository, private val buyers: BuyerProfileRepository, private val clock: Clock
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    fun register(request: RegisterRequest): UserDTO {
        val email = request.email.trim().lowercase(Locale.ROOT)
        if (userRepository.existsByEmail(email)) throw AppException("Email sudah terdaftar", HttpStatus.CONFLICT)
        if (request.role == "admin") throw AppException("Tidak dapat mendaftar sebagai admin melalui formulir publik", HttpStatus.FORBIDDEN)
        if (request.role !in listOf("pembeli", "petani")) throw AppException("Role tidak valid", HttpStatus.BAD_REQUEST)
        if (request.password.length < 8 || request.password.toByteArray(Charsets.UTF_8).size > 72) throw AppException("Password minimal 8 karakter, maksimal 72 byte UTF-8", HttpStatus.BAD_REQUEST)
        if (request.role == "petani" && request.storeName.isNullOrBlank()) throw AppException("Nama toko wajib diisi untuk petani", HttpStatus.BAD_REQUEST)
        if (request.role == "pembeli" && request.fullName.isNullOrBlank()) throw AppException("Nama pembeli wajib diisi", HttpStatus.BAD_REQUEST)
        val saved = userRepository.saveAndFlush(User().apply {
            this.email = email; passwordHash = passwordEncoder.encode(request.password); role = request.role
            createdAt = Instant.now(clock); updatedAt = createdAt
        })
        val id = saved.id!!
        if (request.role == "petani") farmers.save(FarmerProfile(id = id, storeName = request.storeName!!.trim(), contactPhone = request.contactPhone))
        else buyers.save(BuyerProfile(id = id, fullName = request.fullName!!.trim(), contactPhone = request.contactPhone))
        return UserDTO(id, saved.email, saved.role, saved.status)
    }
    @Transactional(readOnly = true)
    fun me(id: java.util.UUID): UserDTO {
        val user = userRepository.findById(id).orElse(null)
            ?: throw AppException("Akun tidak ditemukan", HttpStatus.UNAUTHORIZED)
        return UserDTO(user.id!!, user.email, user.role, user.status)
    }

    fun login(request: LoginRequest): String {
        val user = userRepository.findByEmail(request.email.trim().lowercase(Locale.ROOT))
            ?: throw AppException("Email atau password salah", HttpStatus.UNAUTHORIZED)
        if (request.password.toByteArray(Charsets.UTF_8).size > 72 || !passwordEncoder.matches(request.password, user.passwordHash))
            throw AppException("Email atau password salah", HttpStatus.UNAUTHORIZED)
        // A suspended farmer may still log in to resolve existing orders/refunds.
        return jwtProvider.generateToken(user.id!!.toString())
    }
}
