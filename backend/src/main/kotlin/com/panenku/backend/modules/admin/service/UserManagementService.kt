package com.panenku.backend.modules.admin.service

import com.panenku.backend.common.dto.ApiResponse
import com.panenku.backend.common.dto.successResponse
import com.panenku.backend.common.exception.AppException
import com.panenku.backend.modules.admin.dto.SuspendUserRequest
import com.panenku.backend.modules.admin.repository.AuditLogRepository
import com.panenku.backend.modules.shared.entity.AuditLog
import com.panenku.backend.modules.shared.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class UserManagementService(
    private val userRepository: UserRepository,
    private val auditLogRepository: AuditLogRepository
) {

    fun suspendUser(userId: UUID, adminId: UUID, request: SuspendUserRequest): ApiResponse<Unit> {
        val user = userRepository.findById(userId).orElse(null)
            ?: throw AppException("Pengguna tidak ditemukan", HttpStatus.NOT_FOUND)

        val admin = userRepository.findById(adminId).orElse(null)
            ?: throw AppException("Admin tidak ditemukan", HttpStatus.NOT_FOUND)

        if (admin.role != "admin") {
            throw AppException("Hanya admin yang dapat melakukan suspend", HttpStatus.FORBIDDEN)
        }

        // Admin tidak boleh suspend dirinya sendiri
        if (userId == adminId) {
            throw AppException("Tidak dapat suspend akun sendiri", HttpStatus.BAD_REQUEST)
        }

        // Tidak boleh suspend admin lain
        if (user.role == "admin") {
            throw AppException("Tidak dapat suspend akun admin", HttpStatus.FORBIDDEN)
        }

        val previousStatus = user.status
        user.status = if (request.suspend) "suspended" else "active"
        userRepository.save(user)

        val auditLog = AuditLog().apply {
            actorId = adminId
            actorRole = "admin"
            action = if (request.suspend) "USER_SUSPENDED" else "USER_RESTORED"
            targetType = "user"
            targetId = userId
            oldValues = previousStatus
            newValues = user.status
            reason = request.reason
        }
        auditLogRepository.save(auditLog)

        return successResponse(Unit, if (request.suspend) "Akun berhasil disuspend" else "Akun berhasil dipulihkan")
    }
}
