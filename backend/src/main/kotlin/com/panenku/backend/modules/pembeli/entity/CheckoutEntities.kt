package com.panenku.backend.modules.pembeli.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity @Table(name = "cart")
class CartItem(
    @Id var id: UUID = UUID.randomUUID(), var buyerId: UUID = UUID.randomUUID(),
    var productId: UUID = UUID.randomUUID(),
    @Column(precision = 10, scale = 2) var quantityKg: BigDecimal = BigDecimal.ONE
)

// Snapshot columns are never changed by Gibran's services after insertion.
@Entity @Table(name = "orders")
class Order(
    @Id var id: UUID = UUID.randomUUID(),
    var buyerId: UUID = UUID.randomUUID(), var farmerId: UUID = UUID.randomUUID(),
    var fulfillmentMethod: String = "pickup", var deliveryFee: Long = 0,
    var subtotalItems: Long = 0, var totalPayment: Long = 0, var transactionLimitApplied: Long = 10000000,
    var orderStatus: String = "menunggu_petani", var paymentStatus: String = "belum_dibayar",
    var scheduledDate: LocalDate = LocalDate.now(), var scheduledTimeSlot: String = "",
    var pickupLocationSnapshot: String? = null, var deliveryAddressSnapshot: String? = null,
    var deliveryDistrictSnapshot: String? = null, var buyerNotes: String? = null,
    var receivedAt: Instant? = null, var reservedAt: Instant = Instant.now(), var expiresAt: Instant = Instant.now(),
    var createdAt: Instant = Instant.now(), var updatedAt: Instant = Instant.now(),
    var idempotencyKey: String = "", var requestHash: String = ""
)

@Entity @Table(name = "order_items")
class OrderItem(
    @Id var id: UUID = UUID.randomUUID(), var orderId: UUID = UUID.randomUUID(), var productId: UUID = UUID.randomUUID(),
    var productNameSnapshot: String = "", var commoditySnapshot: String = "", var readyDateSnapshot: LocalDate = LocalDate.now(),
    @Column(precision = 10, scale = 2) var quantityKg: BigDecimal = BigDecimal.ONE,
    var pricePerKgSnapshot: Long = 0, var itemSubtotal: Long = 0
)

@Entity @Table(name = "stock_reservations")
class StockReservation(
    @Id var id: UUID = UUID.randomUUID(), var productId: UUID = UUID.randomUUID(), var orderId: UUID = UUID.randomUUID(),
    @Column(precision = 10, scale = 2) var quantityKg: BigDecimal = BigDecimal.ONE,
    var status: String = "active", var reservedAt: Instant = Instant.now(), var releasedAt: Instant? = null,
    var fulfilledAt: Instant? = null
)

@Entity @Table(name = "order_status_history")
class OrderStatusHistory(
    @Id var id: UUID = UUID.randomUUID(), var orderId: UUID = UUID.randomUUID(), var fromStatus: String? = null,
    var toStatus: String = "menunggu_petani", var actorId: UUID? = null, var actorRole: String = "pembeli",
    var changedAt: Instant = Instant.now(), var note: String? = null
)
