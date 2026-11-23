package com.panenku.backend.modules.shared.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.panenku.backend.common.exception.ApiException
import com.panenku.backend.common.exception.conflict
import com.panenku.backend.common.exception.forbidden
import com.panenku.backend.common.exception.invalid
import com.panenku.backend.common.exception.missing
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.common.storage.LocalPrivateImageStore
import com.panenku.backend.modules.pembeli.entity.Order
import com.panenku.backend.modules.pembeli.entity.OrderStatusHistory
import com.panenku.backend.modules.pembeli.repository.OrderRepository
import com.panenku.backend.modules.pembeli.repository.OrderStatusHistoryRepository
import com.panenku.backend.modules.pembeli.repository.StockReservationRepository
import com.panenku.backend.modules.shared.repository.UserRepository
import com.panenku.backend.modules.shared.repository.FarmerProfileRepository
import com.panenku.backend.modules.shared.repository.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

data class PaymentSubmissionDTO(
    val id: UUID, val orderId: UUID, val status: String, val expectedAmount: Long,
    val submittedAt: Instant, val decision: String?, val reason: String?, val decidedAt: Instant?,
    val originalFileName: String? = null, val fileSizeBytes: Long? = null
)
data class PaymentDecisionRequest(val submissionId: UUID, val decision: String, val reason: String? = null)
private data class StoredSubmission(val view: PaymentSubmissionDTO, val buyerId: UUID, val hash: String, val path: String)

@Service
class PaymentSubmissionService(
    private val actors: ActorService, private val orders: OrderRepository,
    private val history: OrderStatusHistoryRepository, private val db: JdbcTemplate,
    private val users: UserRepository, private val farmers: FarmerProfileRepository,
    private val products: ProductRepository, private val reservations: StockReservationRepository,
    private val destinations: PaymentDestinationService,
    private val mapper: ObjectMapper, private val clock: Clock, private val scheduleGuard: OrderScheduleGuard,
    private val notifications: NotificationService,
    @Value("\${panenku.storage.local-dir:}") private val directory: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val rowMapper = RowMapper { rs, _ ->
        StoredSubmission(PaymentSubmissionDTO(
            rs.getObject("id", UUID::class.java), rs.getObject("order_id", UUID::class.java),
            rs.getString("status"), rs.getLong("expected_amount"), rs.getTimestamp("submitted_at").toInstant(),
            rs.getString("farmer_decision"), rs.getString("farmer_decision_reason"),
            rs.getTimestamp("farmer_decided_at")?.toInstant(), rs.getString("original_file_name"),
            rs.getObject("file_size_bytes", java.lang.Long::class.java)?.toLong()
        ), rs.getObject("buyer_id", UUID::class.java), rs.getString("request_hash"), rs.getString("proof_image_path"))
    }

    @Transactional
    fun submit(actor: UUID, orderId: UUID, key: String, file: MultipartFile): PaymentSubmissionDTO {
        if (!Regex("[A-Za-z0-9_-]{1,128}").matches(key)) invalid("Idempotency-Key wajib, maksimal 128 karakter huruf/angka/_/-.")
        actors.requireLocked(actor, "pembeli")
        if (file.isEmpty || file.size > MAX_BYTES) invalid("Ukuran gambar harus antara 1 byte dan 5 MB.")
        val bytes = file.inputStream.use { it.readNBytes(MAX_BYTES + 1) }
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) invalid("Ukuran gambar harus antara 1 byte dan 5 MB.")
        val hash = sha256(mapper.writeValueAsBytes(listOf(orderId.toString(), sha256(bytes))))
        // History inserts reference users as well as orders. Acquire all locks in the
        // established order before touching the order, including for payment-only changes.
        val order = lockOrder(orderId)
        if (order.buyerId != actor) forbidden("Pesanan bukan milik pembeli ini.")
        val previous = db.query("SELECT * FROM payment_submissions WHERE buyer_id=? AND idempotency_key=?", rowMapper, actor, key).singleOrNull()
        if (previous != null) {
            if (previous.hash != hash) conflict("Idempotency-Key sudah digunakan untuk unggahan berbeda.")
            readImage(previous.path)
            return previous.view
        }
        val now = now()
        scheduleGuard.canPay(order)
        val transition = PaymentStatusRules.submit(order.orderStatus, order.paymentStatus, order.expiresAt, now)
        destinations.finalizeSelection(orderId)
        val storage = storage()
        val path = try { storage.upload(bytes) } catch (ex: ApiException) { throw ex } catch (ex: Exception) { unavailable() }
        registerRollbackCleanup(storage, path)
        val id = UUID.randomUUID()
        val originalName = file.originalFilename?.replace('\\', '/')?.substringAfterLast('/')
            ?.filterNot { it.isISOControl() }?.trim()?.take(255)?.takeIf { it.isNotBlank() }
        db.update("""INSERT INTO payment_submissions(id,order_id,buyer_id,idempotency_key,request_hash,expected_amount,
            proof_image_path,status,submitted_at,original_file_name,file_size_bytes) VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
            id, orderId, actor, key, hash, order.totalPayment, path, transition.paymentStatus, Timestamp.from(now), originalName, bytes.size.toLong())
        changeOrder(order, transition, actor, "pembeli", "Pembeli mengirim bukti pembayaran.", now)
        notifications.create(order.farmerId, "Bukti pembayaran baru", "Pembeli mengunggah bukti pembayaran untuk pesanan #${order.id.toString().take(8)}.",
            "payment", "/orders/${order.id}", now)
        return PaymentSubmissionDTO(id, orderId, transition.paymentStatus, order.totalPayment, now, null, null, null, originalName, bytes.size.toLong())
    }

    @Transactional
    fun decide(actor: UUID, orderId: UUID, request: PaymentDecisionRequest): PaymentSubmissionDTO {
        actors.requireLocked(actor, "petani")
        val owner = orders.findOwnerReference(orderId) ?: missing("Pesanan tidak ditemukan.")
        if (owner.farmerId != actor) forbidden("Pesanan bukan milik petani ini.")
        val order = lockOrder(orderId, actor)
        if (order.farmerId != actor) forbidden("Pesanan bukan milik petani ini.")
        val submission = find(orderId, request.submissionId)
        val decision = request.decision.trim().lowercase(Locale.ROOT)
        val reason = request.reason?.trim()?.takeIf { it.isNotEmpty() }
        if ((reason?.length ?: 0) > 1000) invalid("Alasan maksimal 1000 karakter.")
        if (submission.view.status != PaymentStatusRules.MENUNGGU_VERIFIKASI) {
            if (submission.view.decision == decision && submission.view.reason == reason) return submission.view
            conflict("Bukti ini sudah diputuskan.")
        }
        val now = now()
        val transition = PaymentStatusRules.decide(order.orderStatus, order.paymentStatus, decision, reason, now)
        db.update("""UPDATE payment_submissions SET status=?, farmer_decision=?, farmer_decision_reason=?, farmer_decided_at=?
            WHERE id=?""", transition.paymentStatus, decision, reason, Timestamp.from(now), submission.view.id)
        changeOrder(order, transition, actor, "petani", if (decision == "terima") "Petani menerima bukti pembayaran."
            else "Petani menolak bukti pembayaran. Alasan: $reason", now)
        notifications.create(order.buyerId, if (decision == "terima") "Pembayaran terverifikasi" else "Bukti pembayaran ditolak",
            if (decision == "terima") "Pesanan #${order.id.toString().take(8)} sedang diproses petani." else "Silakan unggah bukti pembayaran baru untuk pesanan #${order.id.toString().take(8)}.",
            "payment", "/orders/${order.id}", now)
        return submission.view.copy(status = transition.paymentStatus, decision = decision, reason = reason, decidedAt = now)
    }

    @Transactional(readOnly = true)
    fun list(actor: UUID, role: String, orderId: UUID): List<PaymentSubmissionDTO> {
        authorizedOrder(actor, role, orderId)
        return db.query("SELECT * FROM payment_submissions WHERE order_id=? ORDER BY submitted_at, id", rowMapper, orderId).map { it.view }
    }

    @Transactional(readOnly = true)
    fun image(actor: UUID, role: String, orderId: UUID, submissionId: UUID): PaymentImage {
        authorizedOrder(actor, role, orderId)
        return readImage(find(orderId, submissionId).path)
    }

    private fun authorizedOrder(actor: UUID, role: String, orderId: UUID): Order {
        if (role !in setOf("pembeli", "petani", "admin")) forbidden("Akses tidak diizinkan.")
        actors.require(actor, role)
        val order = orders.findById(orderId).orElse(null) ?: missing("Pesanan tidak ditemukan.")
        if ((role == "pembeli" && order.buyerId != actor) || (role == "petani" && order.farmerId != actor)) {
            forbidden("Pesanan bukan milik pengguna ini.")
        }
        return order
    }

    private fun lockOrder(orderId: UUID, alreadyLockedFarmer: UUID? = null): Order {
        val owner = orders.findOwnerReference(orderId) ?: missing("Pesanan tidak ditemukan.")
        if (alreadyLockedFarmer != null && owner.farmerId != alreadyLockedFarmer) forbidden("Pesanan bukan milik petani ini.")
        if (alreadyLockedFarmer == null) users.lockById(owner.farmerId) ?: conflict("Akun petani pesanan tidak ditemukan.")
        farmers.lockById(owner.farmerId) ?: conflict("Profil petani pesanan tidak ditemukan.")
        reservations.activeProductIds(orderId).distinct().sorted().forEach {
            products.lockById(it) ?: conflict("Produk pesanan tidak ditemukan.")
        }
        val order = orders.lockById(orderId) ?: missing("Pesanan tidak ditemukan.")
        if (order.farmerId != owner.farmerId) conflict("Pemilik pesanan berubah.")
        return order
    }

    private fun find(orderId: UUID, submissionId: UUID): StoredSubmission =
        db.query("SELECT * FROM payment_submissions WHERE order_id=? AND id=?", rowMapper, orderId, submissionId).singleOrNull()
            ?: missing("Bukti pembayaran tidak ditemukan.")

    private fun changeOrder(order: Order, transition: PaymentStatusRules.Transition, actor: UUID, role: String, note: String, now: Instant) {
        val previous = order.orderStatus
        order.orderStatus = transition.orderStatus
        order.paymentStatus = transition.paymentStatus
        transition.replacementExpiresAt?.let { order.expiresAt = it }
        order.updatedAt = now
        history.save(OrderStatusHistory(orderId = order.id, fromStatus = previous, toStatus = transition.orderStatus,
            actorId = actor, actorRole = role, changedAt = now, note = note))
        orders.flush()
    }

    private fun registerRollbackCleanup(store: LocalPrivateImageStore, key: String) {
        try {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        try { store.delete(key) } catch (ex: Exception) { logger.error("Gagal membersihkan bukti setelah transaksi dibatalkan.") }
                    }
                }
            })
        } catch (ex: Exception) {
            try { store.delete(key) } catch (cleanup: Exception) { logger.error("Gagal membersihkan bukti setelah pendaftaran transaksi gagal.") }
            throw ex
        }
    }

    private fun storage(): LocalPrivateImageStore {
        if (directory.isBlank()) unavailable()
        return try { LocalPrivateImageStore(Path.of(directory)) } catch (ex: Exception) { unavailable() }
    }
    private fun readImage(key: String): PaymentImage {
        val bytes = try { storage().read(key) } catch (ex: Exception) { unavailable() }
        return PaymentImage(if (key.endsWith(".png")) "image/png" else "image/jpeg", bytes)
    }
    private fun unavailable(): Nothing = throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED", "Penyimpanan bukti pembayaran privat belum tersedia.")
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun now() = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)
    private companion object { const val MAX_BYTES = 5 * 1024 * 1024 }
}
