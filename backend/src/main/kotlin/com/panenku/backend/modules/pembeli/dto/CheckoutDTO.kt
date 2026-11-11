package com.panenku.backend.modules.pembeli.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CartItemRequest(val productId: UUID, val quantityKg: BigDecimal)
data class CartQuantityRequest(val quantityKg: BigDecimal)
data class CartItemDTO(val id: UUID, val productId: UUID, val name: String, val quantityKg: BigDecimal,
    val pricePerKg: Long, val itemSubtotal: Long, val stockAvailable: BigDecimal, val available: Boolean)
data class CartDTO(val items: List<CartItemDTO>, val subtotalItems: BigDecimal)
data class CheckoutItemRequest(val productId: UUID, val quantityKg: BigDecimal, val expectedPricePerKg: Long)
data class CheckoutRequest(
    val items: List<CheckoutItemRequest>, val fulfillmentMethod: String, val scheduledDate: LocalDate,
    val scheduledTimeSlot: String, val expectedDeliveryFee: Long,
    val deliveryAddress: String? = null, val deliveryDistrict: String? = null, val buyerNotes: String? = null
)
data class OrderItemDTO(val productId: UUID, val productNameSnapshot: String, val commoditySnapshot: String,
    val readyDateSnapshot: LocalDate, val quantityKg: BigDecimal, val pricePerKgSnapshot: Long, val itemSubtotal: Long)
data class OrderDTO(
    val orderId: UUID, val orderStatus: String, val paymentStatus: String,
    val subtotalItems: Long, val deliveryFee: Long, val totalPayment: Long, val transactionLimitApplied: Long,
    val fulfillmentMethod: String, val scheduledDate: LocalDate, val scheduledTimeSlot: String,
    val pickupLocationSnapshot: String?, val deliveryAddressSnapshot: String?, val deliveryDistrictSnapshot: String?,
    val buyerNotes: String?, val expiresAt: Instant, val items: List<OrderItemDTO>
)
