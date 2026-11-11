package com.panenku.backend.modules.shared.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "audit_logs")
class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(nullable = false)
    var actorId: UUID? = null

    @Column(nullable = false)
    var actorRole: String = ""

    @Column(nullable = false)
    var action: String = ""

    var targetType: String? = null
    var targetId: UUID? = null
    var oldValues: String? = null
    var newValues: String? = null
    var reason: String? = null

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now()
}
