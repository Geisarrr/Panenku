package com.panenku.backend.modules.shared.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.panenku.backend.common.exception.*
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.pembeli.entity.Order
import com.panenku.backend.modules.pembeli.entity.OrderStatusHistory
import com.panenku.backend.modules.pembeli.repository.*
import com.panenku.backend.modules.shared.dto.*
import com.panenku.backend.modules.shared.repository.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.Locale
import java.util.UUID

@Service
class OrderScheduleService(private val actors: ActorService, private val users: UserRepository,
    private val farmers: FarmerProfileRepository, private val orders: OrderRepository,
    private val items: OrderItemRepository, private val history: OrderStatusHistoryRepository,
    private val schedules: OrderScheduleRepository, private val scheduling: SchedulingService,
    private val mapper: ObjectMapper, private val clock: Clock, private val notifications: NotificationService) {
    @Transactional(readOnly = true)
    fun list(actor: UUID, role: String, id: UUID, page: Int, size: Int): PageDTO<ScheduleChangeDTO> {
        if (role !in setOf("petani","pembeli")) forbidden("Role tidak diizinkan.")
        if (role == "petani") actors.requireFarmerForExistingTransaction(actor) else actors.require(actor,"pembeli")
        if (page < 0 || size !in 1..100) invalid("Paginasi tidak valid.")
        owned(actor,role,orders.findById(id).orElseGet { missing("Pesanan tidak ditemukan.") })
        return schedules.list(id,page,size)
    }
    @Transactional
    fun propose(actor: UUID, orderId: UUID, key: String, request: ScheduleChangeRequest): ScheduleChangeDTO {
        if (!Regex("[A-Za-z0-9_-]{1,128}").matches(key)) invalid("Idempotency-Key tidak valid.")
        val reason = request.reason.trim()
        val slot = request.scheduledTimeSlot.trim()
        if (reason.isBlank() || reason.length > 1000 || request.expectedScheduleVersion < 0) invalid("Alasan wajib maksimal 1000 karakter dan versi tidak boleh negatif.")
        val order = lock(actor,"petani",orderId)
        val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(listOf(request.expectedScheduleVersion,request.scheduledDate,slot,reason))))
        schedules.byKey(orderId,actor,key)?.let {
            if (it.hash != hash) fail("SCHEDULE_KEY_REUSED","Idempotency-Key sudah digunakan untuk usulan berbeda.")
            return it.view
        }
        eligible(order)
        val current = schedules.effective(order)
        if (request.expectedScheduleVersion != current.version) fail("SCHEDULE_CHANGED","Jadwal sudah berubah. Muat ulang pesanan.")
        if (schedules.pending(orderId) != null) fail("SCHEDULE_CHANGE_PENDING","Selesaikan usulan jadwal yang masih menunggu pembeli.")
        if (current.scheduledDate == request.scheduledDate && current.scheduledTimeSlot == slot) invalid("Usulan harus berbeda dari jadwal saat ini.")
        validateProposal(order,request.scheduledDate,slot)
        val now = now()
        val v = ScheduleChangeDTO(UUID.randomUUID(),orderId,actor,order.buyerId,current.version,current.scheduledDate,
            current.scheduledTimeSlot,request.scheduledDate,slot,reason,"pending",now,null,null,null)
        schedules.insert(v,key,hash)
        record(order,actor,"petani","Usulan jadwal ${v.fromDate} ${v.fromSlot} menjadi ${v.proposedDate} ${v.proposedSlot} WIB. Alasan: $reason",now)
        notifications.create(order.buyerId, "Usulan perubahan jadwal", "Petani mengusulkan jadwal pesanan #${order.id.toString().take(8)} menjadi ${v.proposedDate} ${v.proposedSlot} WIB.",
            "schedule", "/orders/${order.id}/schedule", now)
        return v
    }
    @Transactional
    fun decide(actor: UUID, orderId: UUID, id: UUID, request: ScheduleDecisionRequest): ScheduleChangeDTO {
        val target = when (request.decision.trim().lowercase(Locale.ROOT)) { "terima" -> "accepted"; "tolak" -> "rejected"; else -> invalid("Keputusan harus terima atau tolak.") }
        val reason = request.reason?.trim()?.takeIf { it.isNotBlank() }
        if ((reason?.length ?: 0) > 1000 || request.expectedScheduleVersion < 0) invalid("Alasan atau versi tidak valid.")
        val order = lock(actor,"pembeli",orderId)
        val row = schedules.find(orderId,id)?.view ?: missing("Usulan jadwal tidak ditemukan.")
        if (row.baseVersion != request.expectedScheduleVersion) fail("SCHEDULE_CHANGED","Versi usulan tidak sesuai.")
        if (row.status != "pending") {
            if (row.status == target && row.buyerReason == reason) return row
            fail("SCHEDULE_ALREADY_DECIDED","Usulan sudah diputuskan pembeli.")
        }
        val version = if (target == "accepted") {
            eligible(order)
            if (schedules.effective(order).version != row.baseVersion) fail("SCHEDULE_CHANGED","Jadwal sudah berubah.")
            validateProposal(order,row.proposedDate,row.proposedSlot)
            Math.addExact(row.baseVersion,1L)
        } else null
        val now = now()
        schedules.decide(id,actor,target,reason,version,now)
        record(order,actor,"pembeli","Pembeli ${if (target == "accepted") "menyetujui" else "menolak"} usulan jadwal ${row.proposedDate} ${row.proposedSlot} WIB.${reason?.let { " Catatan: $it" } ?: ""}",now)
        notifications.create(order.farmerId, if (target == "accepted") "Perubahan jadwal disetujui" else "Perubahan jadwal ditolak",
            "Pembeli ${if (target == "accepted") "menyetujui" else "menolak"} usulan jadwal pesanan #${order.id.toString().take(8)}.",
            "schedule", "/orders/${order.id}/schedule", now)
        return schedules.find(orderId,id)!!.view
    }
    private fun lock(actor: UUID, role: String, id: UUID): Order {
        actors.requireLocked(actor,role)
        val owner = orders.findOwnerReference(id) ?: missing("Pesanan tidak ditemukan.")
        if (role == "petani" && owner.farmerId != actor) forbidden("Pesanan bukan milik petani ini.")
        users.lockById(owner.farmerId) ?: missing("Petani tidak ditemukan.")
        farmers.lockById(owner.farmerId) ?: missing("Profil petani tidak ditemukan.")
        val order = orders.lockById(id) ?: missing("Pesanan tidak ditemukan.")
        owned(actor,role,order)
        return order
    }
    private fun owned(actor: UUID, role: String, order: Order) {
        if ((role == "petani" && order.farmerId != actor) || (role == "pembeli" && order.buyerId != actor)) forbidden("Pesanan bukan milik akun ini.")
    }
    private fun eligible(order: Order) {
        if (order.orderStatus !in setOf("menunggu_petani","menunggu_pembayaran") || order.paymentStatus != "belum_dibayar" || schedules.hasProof(order.id))
            fail("SCHEDULE_CHANGE_UNAVAILABLE","Perubahan jadwal hanya sebelum bukti pembayaran pertama dan sebelum pemenuhan.")
        if (!order.expiresAt.isAfter(now())) fail("ORDER_EXPIRED","Batas waktu pesanan sudah terlewati.")
    }
    private fun validateProposal(order: Order, date: java.time.LocalDate, slot: String) {
        val farmer = farmers.findById(order.farmerId).orElseGet { missing("Profil petani tidak ditemukan.") }
        scheduling.validate(date,slot,items.findAllByOrderIdOrderByProductId(order.id).map { it.readyDateSnapshot },CatalogService.split(farmer.timeSlots))
    }
    private fun record(order: Order, actor: UUID, role: String, note: String, now: Instant) {
        order.updatedAt = now
        history.save(OrderStatusHistory(orderId=order.id,fromStatus=order.orderStatus,toStatus=order.orderStatus,actorId=actor,actorRole=role,note=note,changedAt=now))
        orders.flush()
    }
    private fun now() = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)
    private fun fail(code: String, text: String): Nothing = throw ApiException(HttpStatus.CONFLICT,code,text)
}
