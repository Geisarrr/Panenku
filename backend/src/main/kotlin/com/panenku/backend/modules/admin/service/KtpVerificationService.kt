package com.panenku.backend.modules.admin.service

import com.panenku.backend.common.dto.ApiResponse
import com.panenku.backend.common.dto.successResponse
import com.panenku.backend.common.exception.AppException
import com.panenku.backend.modules.admin.dto.KtpVerifyRequest
import com.panenku.backend.modules.admin.repository.AuditLogRepository
import com.panenku.backend.modules.admin.repository.KtpVerificationRepository
import com.panenku.backend.modules.shared.entity.AuditLog
import com.panenku.backend.modules.shared.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class KtpVerificationService(
    private val ktpRepository: KtpVerificationRepository,
    private val userRepository: UserRepository,
    private val auditLogRepository: AuditLogRepository
) {

    fun verifyKtp(farmerId: UUID, adminId: UUID, request: KtpVerifyRequest): ApiResponse<Unit> {
        val farmerProfile = ktpRepository.findById(farmerId).orElse(null)
            ?: throw AppException("Petani tidak ditemukan", HttpStatus.NOT_FOUND)

        val admin = userRepository.findById(adminId).orElse(null)
            ?: throw AppException("Admin tidak ditemukan", HttpStatus.NOT_FOUND)

        if (admin.role != "admin") {
            throw AppException("Hanya admin yang dapat memverifikasi KTP", HttpStatus.FORBIDDEN)
        }

        val previousStatus = farmerProfile.ktpStatus

        if (request.approved) {
            farmerProfile.ktpStatus = "disetujui"
            farmerProfile.ktpRejectionReason = null
        } else {
            if (request.rejectionReason.isNullOrBlank()) {
                throw AppException("Alasan penolakan wajib diisi", HttpStatus.BAD_REQUEST)
            }
            farmerProfile.ktpStatus = "ditolak"
            farmerProfile.ktpRejectionReason = request.rejectionReason
        }

        farmerProfile.ktpReviewedAt = Instant.now()
        farmerProfile.ktpReviewedBy = adminId

        ktpRepository.save(farmerProfile)

        val auditLog = AuditLog().apply {
            actorId = adminId
            actorRole = "admin"
            action = if (request.approved) "VERIFY_KTP_APPROVED" else "VERIFY_KTP_REJECTED"
            targetType = "farmer_profile"
            targetId = farmerId
            oldValues = previousStatus
            newValues = farmerProfile.ktpStatus
            reason = request.rejectionReason
        }
        auditLogRepository.save(auditLog)

        return successResponse(Unit, "Verifikasi KTP berhasil")
    }
}
