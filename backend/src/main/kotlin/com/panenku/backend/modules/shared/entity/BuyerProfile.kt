package com.panenku.backend.modules.shared.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "buyer_profiles")
class BuyerProfile(
    @Id var id: UUID = UUID.randomUUID(),
    var fullName: String = "",
    var contactPhone: String? = null,
    var deliveryAddress: String? = null
)
