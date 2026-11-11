package com.panenku.backend.modules.shared.service

import com.panenku.backend.common.exception.conflict
import com.panenku.backend.common.exception.forbidden
import com.panenku.backend.common.exception.invalid
import com.panenku.backend.common.exception.missing
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.pembeli.dto.OrderItemDTO
import com.panenku.backend.modules.pembeli.entity.Order
import com.panenku.backend.modules.pembeli.entity.OrderStatusHistory
import com.panenku.backend.modules.pembeli.repository.OrderItemRepository
import com.panenku.backend.modules.pembeli.repository.OrderRepository
import com.panenku.backend.modules.pembeli.repository.OrderStatusHistoryRepository
import com.panenku.backend.modules.pembeli.repository.StockReservationRepository
import com.panenku.backend.modules.shared.dto.FulfillmentUpdateRequest
import com.panenku.backend.modules.shared.dto.OrderCancelRequest
import com.panenku.backend.modules.shared.dto.OrderRespondRequest
import com.panenku.backend.modules.shared.dto.OrderStatusHistoryDTO
import com.panenku.backend.modules.shared.dto.OrderViewDTO
import com.panenku.backend.modules.shared.dto.PageDTO
import com.panenku.backend.modules.shared.entity.Product
import com.panenku.backend.modules.shared.repository.BuyerProfileRepository
import com.panenku.backend.modules.shared.repository.FarmerProfileRepository
import com.panenku.backend.modules.shared.repository.ProductRepository
import com.panenku.backend.modules.shared.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * F5 - siklus status pesanan setelah checkout.
 *
 * Semua operasi yang mengubah stok mengikuti urutan lock yang sama dengan
 * checkout dan scheduler: users -> farmer_profiles -> products (urut UUID) -> order.
 */
@Service
@Transactional
class OrderLifecycleService(
    private val actors: ActorService,
    private val users: UserRepository,
    private val farmers: FarmerProfileRepository,
    private val buyers: BuyerProfileRepository,
    private val products: ProductRepository,
    private val orders: OrderRepository,
    private val orderItems: OrderItemRepository,
    private val reservations: StockReservationRepository,
    private val history: OrderStatusHistoryRepository,
    private val clock: Clock
) {

    @Transactional(readOnly = true)
    fun listForBuyer(actor: UUID, page: Int, size: Int): PageDTO<OrderViewDTO> {
        actors.require(actor, "pembeli")
        validatePage(page, size)
        val result = orders.findAllByBuyerId(actor, pageable(page, size))
        return PageDTO(result.content.map { view(it) }, page, size, result.totalElements)
    }

    @Transactional(readOnly = true)
    fun detailForBuyer(actor: UUID, orderId: UUID): OrderViewDTO {
        actors.require(actor, "pembeli")
        val order = orders.findById(orderId).orElse(null) ?: missing("Pesanan tidak ditemukan.")
        if (order.buyerId != actor) forbidden("Pesanan bukan milik pembeli ini.")
        return view(order, withHistory = true)
    }

    fun cancelByBuyer(actor: UUID, orderId: UUID, request: OrderCancelRequest): OrderViewDTO {
        actors.require(actor, "pembeli")
        val locked = lockAndLoad(orderId)
        val order = locked.order
        if (order.buyerId != actor) forbidden("Pesanan bukan milik pembeli ini.")
        OrderStatusRules.validateCancel(order.orderStatus)

        val now = Instant.now(clock)
        val previous = order.orderStatus
        releaseReservations(locked, orderId, now)
        order.orderStatus = OrderStatusRules.DIBATALKAN
        order.updatedAt = now
        record(order, previous, order.orderStatus, actor, "pembeli",
            noteOrDefault("Pembeli membatalkan pesanan.", request.reason), now)
        orders.flush()
        return view(order, withHistory = true)
    }

    fun receiveByBuyer(actor: UUID, orderId: UUID): OrderViewDTO {
        actors.require(actor, "pembeli")
        val locked = lockAndLoad(orderId)
        val order = locked.order
        if (order.buyerId != actor) forbidden("Pesanan bukan milik pembeli ini.")
        OrderStatusRules.validateReceive(order.orderStatus)

        val now = Instant.now(clock)
        val previous = order.orderStatus
        order.orderStatus = OrderStatusRules.SELESAI
        order.receivedAt = now
        order.updatedAt = now
        record(order, previous, order.orderStatus, actor, "pembeli",
            "Pembeli mengonfirmasi pesanan diterima.", now)
        orders.flush()
        return view(order, withHistory = true)
    }

    @Transactional(readOnly = true)
    fun listForFarmer(actor: UUID, page: Int, size: Int): PageDTO<OrderViewDTO> {
        actors.require(actor, "petani")
        validatePage(page, size)
        val result = orders.findAllByFarmerId(actor, pageable(page, size))
        return PageDTO(result.content.map { view(it) }, page, size, result.totalElements)
    }

    @Transactional(readOnly = true)
    fun detailForFarmer(actor: UUID, orderId: UUID): OrderViewDTO {
        actors.require(actor, "petani")
        val order = orders.findById(orderId).orElse(null) ?: missing("Pesanan tidak ditemukan.")
        if (order.farmerId != actor) forbidden("Pesanan bukan milik petani ini.")
        return view(order, withHistory = true)
    }

    fun respondByFarmer(actor: UUID, orderId: UUID, request: OrderRespondRequest): OrderViewDTO {
        actors.require(actor, "petani")
        val locked = lockAndLoad(orderId)
        val order = locked.order
        if (order.farmerId != actor) forbidden("Pesanan bukan milik petani ini.")
        OrderStatusRules.validateRespond(order.orderStatus)

        val now = Instant.now(clock)
        val previous = order.orderStatus
        val target = OrderStatusRules.respondTarget(request.decision)

        if (target == OrderStatusRules.DITOLAK) {
            val reason = request.reason?.trim()
            if (reason.isNullOrBlank()) invalid("Alasan penolakan wajib diisi.")
            releaseReservations(locked, orderId, now)
            order.orderStatus = target
            order.updatedAt = now
            record(order, previous, target, actor, "petani", "Petani menolak pesanan. Alasan: $reason", now)
        } else {
            if (order.expiresAt <= now) conflict("Pesanan sudah melewati batas waktu respons petani.")
            order.orderStatus = target
            // Beri jendela 24 jam baru untuk pembeli mengunggah bukti pembayaran.
            order.expiresAt = now.plus(ORDER_WINDOW)
            order.updatedAt = now
            record(order, previous, target, actor, "petani",
                noteOrDefault("Petani menerima pesanan.", request.reason), now)
        }

        orders.flush()
        return view(order, withHistory = true)
    }

    fun updateFulfillment(actor: UUID, orderId: UUID, request: FulfillmentUpdateRequest): OrderViewDTO {
        actors.require(actor, "petani")
        val locked = lockAndLoad(orderId)
        val order = locked.order
        if (order.farmerId != actor) forbidden("Pesanan bukan milik petani ini.")
        if (order.paymentStatus != "terverifikasi") {
            conflict("Pembayaran belum diverifikasi, status pemenuhan belum dapat diubah.")
        }

        val transition = OrderStatusRules.fulfillmentTarget(
            order.orderStatus, order.fulfillmentMethod, request.status
        )
        if (transition.targetStatus == OrderStatusRules.GAGAL_DIPENUHI && request.note.isNullOrBlank()) {
            invalid("Alasan gagal dipenuhi wajib diisi.")
        }

        val now = Instant.now(clock)
        val previous = order.orderStatus
        when (transition.action) {
            OrderStatusRules.FulfillmentAction.FULFILL_STOCK -> fulfillReservations(locked, orderId, now)
            OrderStatusRules.FulfillmentAction.RELEASE_STOCK -> releaseReservations(locked, orderId, now)
            OrderStatusRules.FulfillmentAction.NONE -> Unit
        }

        order.orderStatus = transition.targetStatus
        order.updatedAt = now
        record(order, previous, transition.targetStatus, actor, "petani",
            noteOrDefault(defaultFulfillmentNote(transition.targetStatus), request.note), now)
        orders.flush()
        return view(order, withHistory = true)
    }

    private fun lockAndLoad(orderId: UUID): LockedOrder {
        // Baca farmerId sebagai scalar dulu agar entity order tidak masuk persistence context
        // sebelum row lock didapat.
        val owner = orders.findOwnerReference(orderId) ?: missing("Pesanan tidak ditemukan.")
        users.lockById(owner.farmerId) ?: conflict("Akun petani pesanan tidak ditemukan.")
        farmers.lockById(owner.farmerId) ?: conflict("Profil petani pesanan tidak ditemukan.")
        val lockedProducts = reservations.activeProductIds(orderId).distinct().sorted().associateWith {
            products.lockById(it) ?: conflict("Produk pesanan tidak ditemukan.")
        }
        val order = orders.lockById(orderId) ?: missing("Pesanan tidak ditemukan.")
        return LockedOrder(order, lockedProducts)
    }

    private fun releaseReservations(locked: LockedOrder, orderId: UUID, now: Instant) {
        reservations.findByOrderIdAndStatus(orderId, "active").forEach { reservation ->
            val product = locked.products[reservation.productId]
                ?: conflict("Cadangan produk berubah. Muat ulang pesanan.")
            if (product.stockReserved < reservation.quantityKg) {
                conflict("Cadangan stok tidak konsisten.")
            }
            product.stockReserved = product.stockReserved.subtract(reservation.quantityKg)
            product.updatedAt = now
            reservation.status = "released"
            reservation.releasedAt = now
        }
    }

    private fun fulfillReservations(locked: LockedOrder, orderId: UUID, now: Instant) {
        reservations.findByOrderIdAndStatus(orderId, "active").forEach { reservation ->
            val product = locked.products[reservation.productId]
                ?: conflict("Cadangan produk berubah. Muat ulang pesanan.")
            if (product.stockReserved < reservation.quantityKg ||
                product.stockPhysical < reservation.quantityKg
            ) {
                conflict("Stok fisik atau cadangan tidak konsisten.")
            }
            product.stockReserved = product.stockReserved.subtract(reservation.quantityKg)
            product.stockPhysical = product.stockPhysical.subtract(reservation.quantityKg)
            product.updatedAt = now
            reservation.status = "fulfilled"
            reservation.fulfilledAt = now
        }
    }

    private fun record(
        order: Order,
        from: String,
        to: String,
        actorId: UUID,
        actorRole: String,
        note: String?,
        now: Instant
    ) {
        history.save(
            OrderStatusHistory(
                orderId = order.id,
                fromStatus = from,
                toStatus = to,
                actorId = actorId,
                actorRole = actorRole,
                changedAt = now,
                note = note
            )
        )
    }

    private fun view(order: Order, withHistory: Boolean = false): OrderViewDTO {
        val items = orderItems.findAllByOrderIdOrderByProductId(order.id).map {
            OrderItemDTO(
                it.productId, it.productNameSnapshot, it.commoditySnapshot, it.readyDateSnapshot,
                it.quantityKg, it.pricePerKgSnapshot, it.itemSubtotal
            )
        }
        val rows = if (withHistory) {
            history.findAllByOrderIdOrderByChangedAtAsc(order.id).map {
                OrderStatusHistoryDTO(it.fromStatus, it.toStatus, it.actorRole, it.note, it.changedAt)
            }
        } else {
            emptyList()
        }
        return OrderViewDTO(
            orderId = order.id,
            buyerId = order.buyerId,
            farmerId = order.farmerId,
            buyerName = buyers.findById(order.buyerId).orElse(null)?.fullName,
            storeName = farmers.findById(order.farmerId).orElse(null)?.storeName,
            orderStatus = order.orderStatus,
            paymentStatus = order.paymentStatus,
            subtotalItems = order.subtotalItems,
            deliveryFee = order.deliveryFee,
            totalPayment = order.totalPayment,
            transactionLimitApplied = order.transactionLimitApplied,
            fulfillmentMethod = order.fulfillmentMethod,
            scheduledDate = order.scheduledDate,
            scheduledTimeSlot = order.scheduledTimeSlot,
            pickupLocationSnapshot = order.pickupLocationSnapshot,
            deliveryAddressSnapshot = order.deliveryAddressSnapshot,
            deliveryDistrictSnapshot = order.deliveryDistrictSnapshot,
            buyerNotes = order.buyerNotes,
            reservedAt = order.reservedAt,
            expiresAt = order.expiresAt,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
            receivedAt = order.receivedAt,
            items = items,
            history = rows
        )
    }

    private fun validatePage(page: Int, size: Int) {
        if (page < 0 || size !in 1..100) invalid("Paginasi tidak valid.")
    }

    private fun pageable(page: Int, size: Int): PageRequest =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")))

    private fun noteOrDefault(default: String, reason: String?): String {
        val value = reason?.trim()?.takeIf { it.isNotEmpty() } ?: return default
        return "$default Catatan: $value"
    }

    private fun defaultFulfillmentNote(status: String): String = when (status) {
        OrderStatusRules.SIAP_DIAMBIL -> "Petani menandai pesanan siap diambil."
        OrderStatusRules.DIANTAR -> "Petani mulai mengantar pesanan."
        OrderStatusRules.MENUNGGU_PENERIMAAN -> "Petani menandai pesanan tiba/diterima."
        OrderStatusRules.GAGAL_DIPENUHI -> "Petani gagal memenuhi pesanan."
        else -> "Status pemenuhan diperbarui."
    }

    private data class LockedOrder(val order: Order, val products: Map<UUID, Product>)

    private companion object {
        val ORDER_WINDOW: Duration = Duration.ofHours(24)
    }
}
