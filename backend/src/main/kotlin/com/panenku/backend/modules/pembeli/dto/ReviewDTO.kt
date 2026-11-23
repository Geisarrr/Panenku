package com.panenku.backend.modules.pembeli.dto

import java.time.Instant
import java.util.UUID

data class ReviewRequest(val rating: java.math.BigDecimal, val comment: String? = null)
data class ReviewDTO(val id: UUID, val orderId: UUID, val buyerId: UUID, val farmerId: UUID,
    val rating: Int, val comment: String?, val createdAt: Instant)
