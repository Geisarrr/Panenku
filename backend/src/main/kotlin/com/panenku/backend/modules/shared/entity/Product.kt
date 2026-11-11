package com.panenku.backend.modules.shared.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity @Table(name = "commodities")
class Commodity(
    @Id var id: UUID = UUID.randomUUID(),
    var name: String = "",
    var isActive: Boolean = true
)

@Entity @Table(name = "products")
class Product(
    @Id var id: UUID = UUID.randomUUID(),
    var farmerId: UUID = UUID.randomUUID(),
    var commodityId: UUID = UUID.randomUUID(),
    var name: String = "",
    var description: String? = null,
    var photoUrl: String? = null,
    var pricePerKg: Long = 1,
    @Column(precision = 10, scale = 2) var stockPhysical: BigDecimal = BigDecimal.ZERO,
    @Column(precision = 10, scale = 2) var stockReserved: BigDecimal = BigDecimal.ZERO,
    var readyDate: LocalDate = LocalDate.now(),
    var publicationStatus: String = "draft",
    var updatedAt: Instant = Instant.now()
) {
    val available: BigDecimal get() = stockPhysical.subtract(stockReserved)
}
