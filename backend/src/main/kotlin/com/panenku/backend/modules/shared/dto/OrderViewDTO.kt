package com.panenku.backend.modules.shared.dto

import com.panenku.backend.modules.pembeli.dto.OrderItemDTO
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class OrderStatusHistoryDTO(
    val fromStatus: String?,
    val toStatus: String,
    val actorRole: String,
    val note: String?,
    val changedAt: Instant
)

data class OrderViewDTO(
    val orderId: UUID,
    val buyerId: UUID,
    val farmerId: UUID,
    val buyerName: String?,
    val storeName: String?,
    val orderStatus: String,
    val paymentStatus: String,
    val subtotalItems: Long,
    val deliveryFee: Long,
    val totalPayment: Long,
    val transactionLimitApplied: Long,
    val fulfillmentMethod: String,
    val scheduledDate: LocalDate,
    val scheduledTimeSlot: String,
    val pickupLocationSnapshot: String?,
    val deliveryAddressSnapshot: String?,
    val deliveryDistrictSnapshot: String?,
    val buyerNotes: String?,
    val reservedAt: Instant,
    val expiresAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val receivedAt: Instant?,
    val items: List<OrderItemDTO>,
    val history: List<OrderStatusHistoryDTO> = emptyList()
)

/** Body untuk `PUT /petani/orders/{id}/respond`. */
data class OrderRespondRequest(val decision: String, val reason: String? = null)

/** Body untuk `PUT /petani/orders/{id}/fulfillment`. */
data class FulfillmentUpdateRequest(val status: String, val note: String? = null)

/** Body opsional untuk `POST /buyer/orders/{id}/cancel`. */
data class OrderCancelRequest(val reason: String? = null)
