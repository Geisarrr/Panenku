package com.panenku.backend.scheduler

import com.panenku.backend.common.exception.conflict
import com.panenku.backend.modules.pembeli.entity.OrderStatusHistory
import com.panenku.backend.modules.pembeli.repository.*
import com.panenku.backend.modules.shared.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class OrderExpirationService(
    private val users: UserRepository, private val farmers: FarmerProfileRepository, private val products: ProductRepository,
    private val orders: OrderRepository, private val reservations: StockReservationRepository,
    private val history: OrderStatusHistoryRepository
) {
    @Transactional
    fun expire(orderId: UUID, farmerId: UUID, now: Instant): Boolean {
        // Match checkout's lock order: farmer -> profile -> sorted products -> order.
        // Suspended farmers' overdue unpaid orders must still release their reservations.
        users.lockById(farmerId) ?: conflict("Akun petani pesanan tidak ditemukan.")
        farmers.lockById(farmerId) ?: conflict("Profil petani pesanan tidak ditemukan.")
        val lockedProducts = reservations.activeProductIds(orderId).distinct().sorted().associateWith {
            products.lockById(it) ?: conflict("Produk pesanan tidak ditemukan.")
        }
        val order = orders.lockById(orderId) ?: return false
        if (order.farmerId != farmerId || order.expiresAt > now ||
            order.orderStatus !in listOf("menunggu_petani", "menunggu_pembayaran") ||
            order.paymentStatus !in listOf("belum_dibayar", "bukti_ditolak")) return false

        val previous = order.orderStatus
        reservations.findByOrderIdAndStatus(orderId, "active").forEach { reservation ->
            val product = lockedProducts[reservation.productId] ?: conflict("Cadangan produk berubah.")
            if (product.stockReserved < reservation.quantityKg) conflict("Cadangan stok tidak konsisten.")
            product.stockReserved = product.stockReserved.subtract(reservation.quantityKg)
            product.updatedAt = now
            reservation.status = "released"; reservation.releasedAt = now
        }
        order.orderStatus = "kedaluwarsa"; order.updatedAt = now
        history.save(OrderStatusHistory(orderId = order.id, fromStatus = previous, toStatus = "kedaluwarsa",
            actorId = null, actorRole = "system", changedAt = now, note = "Batas waktu respons/pembayaran terlewati."))
        orders.flush()
        return true
    }
}
