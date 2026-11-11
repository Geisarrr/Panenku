package com.panenku.backend.modules.shared.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CommodityDTO(val id: UUID, val name: String, val isActive: Boolean)
data class CommodityRequest(val name: String, val isActive: Boolean = true)
data class ProductDTO(
    val id: UUID, val farmerId: UUID, val storeName: String, val commodityId: UUID, val commodity: String,
    val name: String, val description: String?, val photoUrl: String?, val pricePerKg: Long,
    val stockPhysical: BigDecimal, val stockReserved: BigDecimal, val stockAvailable: BigDecimal,
    val readyDate: LocalDate, val publicationStatus: String
)
data class ProductDetailDTO(
    val product: ProductDTO, val pickupLocation: String?, val shippingEnabled: Boolean,
    val deliveryFee: Long, val serviceArea: List<String>, val timeSlots: List<String>
)
data class PageDTO<T>(val items: List<T>, val page: Int, val size: Int, val totalElements: Long)
