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
import com.panenku.backend.modules.pembeli.repository.OrderRepository
import com.panenku.backend.modules.shared.dto.*
import com.panenku.backend.modules.shared.repository.RefundRepository
import com.panenku.backend.modules.shared.repository.RefundRow
import com.panenku.backend.modules.shared.repository.NewRefund
import com.panenku.backend.modules.shared.repository.NewDispute
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

@Service
class RefundService(
    private val actors: ActorService, private val orders: OrderRepository,
    private val refunds: RefundRepository, private val mapper: ObjectMapper, private val clock: Clock,
    private val notifications: NotificationService,
    @Value("\${panenku.storage.local-dir:}") private val directory: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    @Transactional
    fun create(actor: UUID, key: String, orderId: UUID, reason: String, destinationType: String,
               provider: String, account: String, name: String, image: MultipartFile): RefundDTO {
        validateKey(key)
        val cleanReason = text(reason, 1000, "Alasan refund")
        val type = destinationType.trim().lowercase(Locale.ROOT)
        if (type !in setOf("bank", "e-wallet")) invalid("Tujuan refund harus bank atau e-wallet.")
        val cleanProvider = text(provider, 100, "Nama bank atau penyedia")
        val cleanAccount = account.trim()
        if (!Regex("[0-9]{1,100}").matches(cleanAccount)) invalid("Nomor tujuan harus berupa 1–100 digit.")
        val cleanName = text(name, 255, "Nama penerima refund")
        actors.requireLocked(actor, "pembeli")
        val bytes = imageBytes(image)
        val hash = hash(listOf(orderId.toString(), cleanReason, type, cleanProvider, cleanAccount, cleanName, sha256(bytes)))
        val order = orders.lockById(orderId) ?: missing("Pesanan tidak ditemukan.")
        if (order.buyerId != actor) forbidden("Pesanan bukan milik pembeli ini.")
        val existing = refunds.findByKey(actor, key)
        if (existing != null) {
            if (existing.hash != hash) conflict("Idempotency-Key sudah digunakan untuk pengajuan berbeda.")
            readImage(existing.evidencePath)
            return detail(existing.view)
        }
        val now = now()
        validateEligible(order, now)
        val completed = refunds.completedCount(orderId)
        if (completed > 0) conflict("Refund penuh untuk pesanan ini sudah selesai.")
        val active = refunds.activeCount(orderId)
        if (active > 0) conflict("Pesanan sudah memiliki pengajuan refund aktif.")
        val path = saveImage(bytes)
        val id = UUID.randomUUID()
        refunds.insert(NewRefund(id, orderId, actor, key, hash, cleanReason, path, order.totalPayment, type, cleanProvider, cleanAccount, cleanName, now))
        refunds.record(id, null, "diajukan", actor, "pembeli", "Pembeli mengajukan refund.", now)
        notifications.create(order.farmerId, "Pengajuan refund baru", "Pembeli mengajukan refund untuk pesanan #${orderId.toString().take(8)}.", "refund", "/refunds/$id", now)
        return detail(findRefund(id).view)
    }

    @Transactional
    fun respond(actor: UUID, id: UUID, request: RefundDecisionRequest): RefundDTO {
        actors.requireFarmerForExistingTransaction(actor, locked = true)
        val row = lockRefund(id)
        if (row.view.farmerId != actor) forbidden("Refund bukan milik petani ini.")
        val decision = request.decision.trim().lowercase(Locale.ROOT)
        if (decision !in setOf("terima", "tolak")) invalid("Keputusan harus terima atau tolak.")
        val reason = optionalText(request.reason, 1000)
        if (decision == "tolak" && reason == null) invalid("Alasan penolakan wajib diisi.")
        if (row.view.farmerDecision != null) {
            if (row.view.farmerDecision == decision && row.view.farmerReason == reason) return detail(row.view)
            conflict("Pengajuan ini sudah direspons petani.")
        }
        if (row.view.status != "diajukan") conflict("Refund tidak sedang menunggu respons petani.")
        val target = if (decision == "terima") "disetujui" else "ditolak"
        val now = now()
        refunds.respond(id, target, decision, reason, now)
        refunds.record(id, row.view.status, target, actor, "petani", reason, now)
        notifications.create(row.view.buyerId, if (decision == "terima") "Refund disetujui petani" else "Refund ditolak petani",
            "Refund untuk pesanan #${row.view.orderId.toString().take(8)} telah ${if (decision == "terima") "disetujui." else "ditolak."}", "refund", "/refunds/$id", now)
        return detail(findRefund(id).view)
    }

    @Transactional
    fun submitTransfer(actor: UUID, id: UUID, key: String, image: MultipartFile): RefundDTO {
        validateKey(key)
        actors.requireFarmerForExistingTransaction(actor, locked = true)
        val bytes = imageBytes(image)
        val hash = hash(listOf(id.toString(), sha256(bytes)))
        val row = lockRefund(id)
        if (row.view.farmerId != actor) forbidden("Refund bukan milik petani ini.")
        val previous = refunds.transferRequest(actor, key)
        if (previous != null) {
            if (previous.first != hash || previous.second != id) conflict("Idempotency-Key sudah digunakan untuk unggahan berbeda.")
            readImage(row.transferPath ?: conflict("Bukti transfer tidak ditemukan."))
            return detail(row.view)
        }
        if (row.view.status != "disetujui" || row.transferPath != null) conflict("Refund tidak menunggu bukti transfer petani.")
        val path = saveImage(bytes)
        val now = now()
        refunds.saveTransfer(id, actor, key, hash, path, now)
        refunds.record(id, row.view.status, "menunggu_konfirmasi_dana", actor, "petani", "Petani mengirim bukti transfer balik.", now)
        notifications.create(row.view.buyerId, "Bukti transfer refund tersedia", "Petani mengirim bukti transfer refund untuk pesanan #${row.view.orderId.toString().take(8)}.", "refund", "/refunds/$id", now)
        return detail(findRefund(id).view)
    }

    @Transactional
    fun confirm(actor: UUID, id: UUID): RefundDTO {
        actors.requireLocked(actor, "pembeli")
        val row = lockRefund(id)
        if (row.view.buyerId != actor) forbidden("Refund bukan milik pembeli ini.")
        if (row.view.status == "selesai") return detail(row.view)
        if (row.view.status != "menunggu_konfirmasi_dana" || row.transferPath == null) conflict("Refund belum menunggu konfirmasi dana.")
        val now = now()
        refunds.confirm(id, now)
        refunds.record(id, row.view.status, "selesai", actor, "pembeli", "Pembeli mengonfirmasi dana diterima.", now)
        notifications.create(row.view.farmerId, "Refund selesai", "Pembeli mengonfirmasi dana refund untuk pesanan #${row.view.orderId.toString().take(8)} telah diterima.", "refund", "/refunds/$id", now)
        return detail(findRefund(id).view)
    }

    @Transactional
    fun escalate(actor: UUID, id: UUID, key: String, request: EscalationRequest): RefundDisputeDTO {
        validateKey(key)
        val reason = text(request.reason, 1000, "Alasan eskalasi")
        actors.requireLocked(actor, "pembeli")
        val row = lockRefund(id)
        if (row.view.buyerId != actor) forbidden("Refund bukan milik pembeli ini.")
        val hash = hash(listOf(id.toString(), reason))
        val previous = refunds.findDisputeByKey(actor, key)
        if (previous != null) {
            if (previous.hash != hash) conflict("Idempotency-Key sudah digunakan untuk eskalasi berbeda.")
            return previous.view
        }
        if (row.view.status !in setOf("diajukan", "ditolak", "disetujui", "menunggu_konfirmasi_dana")) conflict("Refund tidak dapat dieskalasi pada status ini.")
        val active = refunds.activeCount(row.view.orderId, id)
        if (active > 0) conflict("Pesanan memiliki refund aktif lain.")
        val completed = refunds.completedCount(row.view.orderId, id)
        if (completed > 0) conflict("Refund penuh untuk pesanan ini sudah selesai.")
        val disputeId = UUID.randomUUID()
        val now = now()
        refunds.insertDispute(NewDispute(disputeId, id, actor, key, hash, reason, row.view.status, now))
        refunds.updateStatus(id, "dieskalasi", now)
        refunds.record(id, row.view.status, "dieskalasi", actor, "pembeli", reason, now)
        notifications.create(row.view.farmerId, "Refund dieskalasi", "Pembeli meminta peninjauan admin untuk refund pesanan #${row.view.orderId.toString().take(8)}.", "refund", "/refunds/$id", now)
        notifications.createForActiveAdmins("Sengketa refund baru", "Refund pesanan #${row.view.orderId.toString().take(8)} menunggu keputusan admin.", "refund", "/admin/disputes/$disputeId", now)
        return findDispute(disputeId).view
    }

    @Transactional
    fun decideDispute(actor: UUID, disputeId: UUID, request: AdminDisputeDecisionRequest): RefundDisputeDTO {
        val decision = request.decision.trim().lowercase(Locale.ROOT)
        if (decision !in setOf("setujui_refund", "tutup_sengketa")) invalid("Keputusan harus setujui_refund atau tutup_sengketa.")
        val reason = text(request.reason, 1000, "Alasan keputusan")
        actors.requireLocked(actor, "admin")
        val reference = findDispute(disputeId)
        val refund = lockRefund(reference.view.refundId)
        val dispute = refunds.findDispute(disputeId, lock = true) ?: missing("Sengketa tidak ditemukan.")
        if (dispute.view.status == "selesai") {
            if (dispute.view.decision == decision && dispute.view.decisionReason == reason) return dispute.view
            conflict("Sengketa sudah diputuskan.")
        }
        if (refund.view.status != "dieskalasi") conflict("Refund tidak sedang dieskalasi.")
        val target = if (decision == "tutup_sengketa") dispute.previousStatus
            else if (dispute.previousStatus == "menunggu_konfirmasi_dana") "menunggu_konfirmasi_dana" else "disetujui"
        val now = now()
        refunds.decideDispute(disputeId, actor, decision, reason, now)
        refunds.updateStatus(refund.view.id, target, now)
        refunds.record(refund.view.id, refund.view.status, target, actor, "admin", reason, now)
        refunds.auditDispute(actor, disputeId, if (decision == "setujui_refund") "REFUND_DISPUTE_APPROVED" else "REFUND_DISPUTE_CLOSED", reason, now)
        val decisionTitle = if (decision == "setujui_refund") "Admin menyetujui refund" else "Admin menutup sengketa refund"
        notifications.create(refund.view.buyerId, decisionTitle, "Admin telah memutuskan sengketa refund pesanan #${refund.view.orderId.toString().take(8)}.", "refund", "/refunds/${refund.view.id}", now)
        notifications.create(refund.view.farmerId, decisionTitle, "Admin telah memutuskan sengketa refund pesanan #${refund.view.orderId.toString().take(8)}.", "refund", "/refunds/${refund.view.id}", now)
        return findDispute(disputeId).view
    }

    @Transactional(readOnly = true)
    fun list(actor: UUID, role: String, page: Int, size: Int): PageDTO<RefundDTO> {
        if (role !in setOf("pembeli", "petani", "admin")) forbidden("Akses tidak diizinkan.")
        requireRole(actor, role)
        validatePage(page, size)
        return refunds.list(actor, role, page, size)
    }

    @Transactional(readOnly = true)
    fun detail(actor: UUID, role: String, id: UUID): RefundDTO = detail(authorized(actor, role, id).view)

    @Transactional(readOnly = true)
    fun image(actor: UUID, role: String, id: UUID, kind: String): PaymentImage {
        val row = authorized(actor, role, id)
        val path = when (kind) { "evidence" -> row.evidencePath; "transfer-proof" -> row.transferPath ?: missing("Bukti transfer tidak ditemukan."); else -> missing("Jenis bukti tidak ditemukan.") }
        return readImage(path)
    }

    @Transactional(readOnly = true)
    fun disputes(actor: UUID, page: Int, size: Int): PageDTO<RefundDisputeDTO> {
        actors.require(actor, "admin")
        validatePage(page, size)
        return refunds.openDisputes(page, size)
    }

    @Transactional(readOnly = true)
    fun disputeDetail(actor: UUID, role: String, id: UUID): RefundDisputeDTO {
        if (role !in setOf("pembeli", "admin")) forbidden("Akses tidak diizinkan.")
        requireRole(actor, role)
        val dispute = findDispute(id).view
        if (role == "pembeli" && dispute.buyerId != actor) forbidden("Sengketa bukan milik pembeli ini.")
        return dispute
    }

    private fun authorized(actor: UUID, role: String, id: UUID): RefundRow {
        if (role !in setOf("pembeli", "petani", "admin")) forbidden("Akses tidak diizinkan.")
        requireRole(actor, role)
        val row = findRefund(id)
        if ((role == "pembeli" && row.view.buyerId != actor) || (role == "petani" && row.view.farmerId != actor)) forbidden("Refund bukan milik pengguna ini.")
        return row
    }
    private fun findRefund(id: UUID) = refunds.findRefund(id) ?: missing("Refund tidak ditemukan.")
    private fun requireRole(actor: UUID, role: String) {
        if (role == "petani") actors.requireFarmerForExistingTransaction(actor) else actors.require(actor, role)
    }
    private fun findDispute(id: UUID) = refunds.findDispute(id) ?: missing("Sengketa tidak ditemukan.")
    private fun lockRefund(id: UUID): RefundRow {
        // Lock the actor first, then the order, then refund. No farmer/product lock is
        // acquired after the order, and refund never changes stock or order snapshots.
        val ref = refunds.orderId(id) ?: missing("Refund tidak ditemukan.")
        orders.lockById(ref) ?: missing("Pesanan tidak ditemukan.")
        return refunds.findRefund(id, lock = true) ?: missing("Refund tidak ditemukan.")
    }
    private fun detail(view: RefundDTO): RefundDTO = view.copy(history = refunds.history(view.id))
    private fun validateEligible(order: Order, now: Instant) {
        if (order.paymentStatus != "terverifikasi" || order.totalPayment <= 0) conflict("Refund hanya untuk pesanan dengan pembayaran terverifikasi.")
        if (order.orderStatus == "gagal_dipenuhi") return
        val received = order.receivedAt ?: conflict("Pesanan belum dikonfirmasi diterima.")
        if (order.orderStatus != "selesai" || now.isBefore(received) || now.isAfter(received.plus(Duration.ofHours(24)))) {
            conflict("Pengajuan refund di luar jendela 24 jam setelah penerimaan.")
        }
    }
    private fun validateKey(key: String) { if (!Regex("[A-Za-z0-9_-]{1,128}").matches(key)) invalid("Idempotency-Key wajib, maksimal 128 karakter huruf/angka/_/-.") }
    private fun validatePage(page: Int, size: Int) { if (page < 0 || size !in 1..100) invalid("Paginasi tidak valid.") }
    private fun text(value: String, max: Int, label: String): String = value.trim().also { if (it.isBlank() || it.length > max) invalid("$label wajib diisi dan maksimal $max karakter.") }
    private fun optionalText(value: String?, max: Int): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.also { if (it.length > max) invalid("Catatan maksimal $max karakter.") }
    private fun imageBytes(file: MultipartFile): ByteArray {
        if (file.isEmpty || file.size > MAX_BYTES) invalid("Ukuran gambar harus antara 1 byte dan 5 MB.")
        return file.inputStream.use { it.readNBytes(MAX_BYTES + 1) }.also { if (it.isEmpty() || it.size > MAX_BYTES) invalid("Ukuran gambar harus antara 1 byte dan 5 MB.") }
    }
    private fun saveImage(bytes: ByteArray): String {
        val store = storage()
        val key = try { store.upload(bytes) } catch (ex: ApiException) { throw ex } catch (ex: Exception) { unavailable() }
        try {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) try { store.delete(key) }
                    catch (ex: Exception) { logger.error("Gagal membersihkan file refund setelah rollback.") }
                }
            })
        } catch (ex: Exception) {
            try { store.delete(key) } catch (cleanup: Exception) { logger.error("Gagal membersihkan file refund.") }
            throw ex
        }
        return key
    }
    private fun storage(): LocalPrivateImageStore {
        if (directory.isBlank()) unavailable()
        return try { LocalPrivateImageStore(Path.of(directory)) } catch (ex: Exception) { unavailable() }
    }
    private fun readImage(key: String): PaymentImage {
        val bytes = try { storage().read(key) } catch (ex: Exception) { unavailable() }
        return PaymentImage(if (key.endsWith(".png")) "image/png" else "image/jpeg", bytes)
    }
    private fun unavailable(): Nothing = throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED", "Penyimpanan bukti refund privat belum tersedia.")
    private fun hash(values: List<String>) = sha256(mapper.writeValueAsBytes(values))
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)
    private companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
