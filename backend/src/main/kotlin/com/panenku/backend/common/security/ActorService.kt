package com.panenku.backend.common.security

import com.panenku.backend.common.exception.*
import com.panenku.backend.modules.shared.entity.User
import com.panenku.backend.modules.shared.repository.UserRepository
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ActorService(private val accounts: UserRepository) {
    fun id(jwt: Jwt): UUID = try { UUID.fromString(jwt.subject) } catch (_: Exception) { forbidden("Subjek token tidak valid.") }

    // Check the database too: client-supplied role or a stale JWT cannot bypass suspension.
    fun require(id: UUID, role: String, lock: Boolean = true): User {
        val user = if (lock) accounts.lockById(id) else accounts.findById(id).orElse(null)
        if (user == null || user.role != role || user.status != "active") forbidden("Akses $role tidak diizinkan.")
        return user
    }
}
