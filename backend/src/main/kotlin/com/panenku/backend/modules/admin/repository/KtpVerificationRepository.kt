package com.panenku.backend.modules.admin.repository

import com.panenku.backend.modules.shared.entity.FarmerProfile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface KtpVerificationRepository : JpaRepository<FarmerProfile, UUID>
