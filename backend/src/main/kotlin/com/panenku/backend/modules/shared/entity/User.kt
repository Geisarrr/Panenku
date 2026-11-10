package com.panenku.backend.modules.shared.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
    @Column(unique = true, nullable = false)
    var email: String = ""
    @Column(nullable = false)
    var passwordHash: String = ""
    @Column(nullable = false)
    var role: String = ""
    @Column(nullable = false)
    var status: String = "active"
    var createdAt: Instant = Instant.now()
    var updatedAt: Instant = Instant.now()
}

@Entity @Table(name = "buyer_profiles")
class BuyerProfile(
    @Id var id: UUID = UUID.randomUUID(),
    var fullName: String = "", var contactPhone: String? = null, var deliveryAddress: String? = null
)
