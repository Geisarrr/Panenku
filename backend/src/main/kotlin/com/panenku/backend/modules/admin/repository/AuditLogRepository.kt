package com.panenku.backend.modules.admin.repository

import com.panenku.backend.modules.shared.entity.AuditLog
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditLogRepository : JpaRepository<AuditLog, UUID>