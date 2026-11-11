package com.panenku.backend.modules.petani.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ProductRequest(
    val commodityId: UUID, val name: String, val pricePerKg: Long,
    val stockPhysical: BigDecimal, val readyDate: LocalDate,
    val publicationStatus: String = "draft", val description: String? = null, val photoUrl: String? = null
)
