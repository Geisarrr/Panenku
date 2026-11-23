package com.panenku.backend.modules.admin.service

import com.panenku.backend.common.dto.ApiResponse
import com.panenku.backend.common.dto.successResponse
import com.panenku.backend.common.exception.AppException
import com.panenku.backend.modules.admin.dto.KtpVerifyRequest
import com.panenku.backend.modules.admin.repository.AuditLogRepository
import com.panenku.backend.modules.admin.repository.KtpVerificationRepository
import com.panenku.backend.modules.shared.entity.AuditLog
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.common.exception.conflict
import com.panenku.backend.modules.shared.repository.FarmerProfileRepository
import com.panenku.backend.modules.shared.repository.UserRepository
import com.panenku.backend.modules.shared.service.NotificationService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
@Transactional
class KtpVerificationService(
    private val ktpRepository: KtpVerificationRepository,
    private val farmerProfiles: FarmerProfileRepository,
    private val userRepository: UserRepository,
    private val actors: ActorService,
    private val auditLogRepository: AuditLogRepository,
    private val clock: Clock,
    private val notifications: NotificationService
) {

    fun verifyKtp(farmerId: UUID, adminId: UUID, request: KtpVerifyRequest): ApiResponse<Unit> {
        actors.requireLocked(adminId, "admin")
        if (userRepository.roleById(farmerId) != "petani") throw AppException("Petani tidak ditemukan", HttpStatus.NOT_FOUND)
        val farmer = userRepository.lockById(farmerId)
        if (farmer == null || farmer.role != "petani") throw AppException("Petani tidak ditemukan", HttpStatus.NOT_FOUND)
        val farmerProfile = farmerProfiles.lockById(farmerId)
            ?: throw AppException("Petani tidak ditemukan", HttpStatus.NOT_FOUND)

        if (farmerProfile.ktpStatus != "menunggu") conflict("Pengajuan KTP tidak sedang menunggu verifikasi.")

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

        val reviewedAt = clock.instant()
        farmerProfile.ktpReviewedAt = reviewedAt
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
        notifications.create(farmerId, if (request.approved) "KTP disetujui" else "KTP ditolak",
            if (request.approved) "Verifikasi KTP dan toko Anda telah disetujui." else "Pengajuan KTP ditolak. ${request.rejectionReason?.trim() ?: "Perbaiki data lalu ajukan kembali."}",
            "verification", "/account/verification", reviewedAt)

        return successResponse(Unit, "Verifikasi KTP berhasil")
    }
}
