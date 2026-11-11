package com.panenku.backend.modules.pembeli.repository

import com.panenku.backend.modules.pembeli.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Pageable
import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID

interface CartRepository : JpaRepository<CartItem, UUID> {
    fun findAllByBuyerIdOrderById(buyerId: UUID): List<CartItem>
    fun findByBuyerIdAndProductId(buyerId: UUID, productId: UUID): CartItem?
}
interface ExpiredOrderReference { val id: UUID; val farmerId: UUID }

interface OrderRepository : JpaRepository<Order, UUID> {
    fun findByBuyerIdAndIdempotencyKey(buyerId: UUID, idempotencyKey: String): Order?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    fun lockById(@Param("id") id: UUID): Order?
    @Query("""select o.id as id, o.farmerId as farmerId from Order o
        where o.expiresAt <= :now and o.orderStatus in ('menunggu_petani','menunggu_pembayaran')
        and o.paymentStatus in ('belum_dibayar','bukti_ditolak') order by o.expiresAt, o.id""")
    fun findExpiredOrdersBefore(@Param("now") now: Instant, pageable: Pageable): List<ExpiredOrderReference>
}
interface OrderItemRepository : JpaRepository<OrderItem, UUID> {
    fun findAllByOrderIdOrderByProductId(orderId: UUID): List<OrderItem>
}
interface StockReservationRepository : JpaRepository<StockReservation, UUID> {
    @Query("select r.productId from StockReservation r where r.orderId = :orderId and r.status = 'active' order by r.productId")
    fun activeProductIds(@Param("orderId") orderId: UUID): List<UUID>
    fun findByOrderIdAndStatus(orderId: UUID, status: String): List<StockReservation>
}
interface OrderStatusHistoryRepository : JpaRepository<OrderStatusHistory, UUID>
