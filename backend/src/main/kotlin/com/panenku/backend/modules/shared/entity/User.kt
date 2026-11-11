package com.panenku.backend.modules.shared.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "users")
class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(unique = true, nullable = false)
    var email: String = ""

    @Column(nullable = false)
    var passwordHash: String = ""

    @Column(nullable = false)
    var role: String = "" // pembeli, petani, admin

    @Column(nullable = false)
    var status: String = "active" // active, suspended

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now()

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
}