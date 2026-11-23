package com.panenku.backend.modules.shared.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.panenku.backend.common.exception.ApiException
import com.panenku.backend.common.exception.conflict
import com.panenku.backend.common.exception.forbidden
import com.panenku.backend.common.exception.invalid
import com.panenku.backend.common.exception.missing
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.common.storage.LocalPrivateImageStore
import com.panenku.backend.modules.pembeli.repository.OrderRepository
import com.panenku.backend.modules.shared.dto.LatePaymentCloseRequest
import com.panenku.backend.modules.shared.dto.LatePaymentReportDTO
import com.panenku.backend.modules.shared.dto.LatePaymentReviewRequest
import com.panenku.backend.modules.shared.dto.PageDTO
import com.panenku.backend.modules.shared.repository.LatePaymentReportRepository
import com.panenku.backend.modules.shared.repository.LateReportRow
import com.panenku.backend.modules.shared.repository.NewLateReport
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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

@Service
class LatePaymentReportService(
    private val actors: ActorService, private val orders: OrderRepository,
    private val reports: LatePaymentReportRepository, private val mapper: ObjectMapper, private val clock: Clock,
    private val notifications: NotificationService,
    @Value("\${panenku.storage.local-dir:}") private val directory: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    @Transactional
    fun create(actor: UUID, key: String, orderId: UUID, reason: String, claimedAmount: Long, image: MultipartFile): LatePaymentReportDTO {
        validateKey(key)
        val cleanReason = text(reason, "Keterangan laporan")
        if (claimedAmount <= 0) invalid("Nominal yang dilaporkan harus lebih dari nol.")
        actors.requireLocked(actor, "pembeli")
        val bytes = imageBytes(image)
        val hash = sha256(mapper.writeValueAsBytes(listOf(orderId.toString(), cleanReason, claimedAmount.toString(), sha256(bytes))))
        val order = orders.lockById(orderId) ?: missing("Pesanan tidak ditemukan.")
        if (order.buyerId != actor) forbidden("Pesanan bukan milik pembeli ini.")
        val previous = reports.findByKey(actor, key)
        if (previous != null) {
            if (previous.hash != hash) conflict("Idempotency-Key sudah digunakan untuk laporan berbeda.")
            readImage(previous.evidencePath)
            return previous.view
        }
        if (order.orderStatus != "kedaluwarsa" || order.paymentStatus !in setOf("belum_dibayar", "bukti_ditolak")) {
            conflict("Laporan hanya untuk pesanan kedaluwarsa dengan pembayaran belum diverifikasi.")
        }
        val active = reports.activeCount(orderId)
        if (active > 0) conflict("Pesanan sudah memiliki laporan aktif.")
        val path = saveImage(bytes)
        val id = UUID.randomUUID()
        val now = now()
        reports.insert(NewLateReport(id, orderId, actor, key, hash, cleanReason, claimedAmount, path, now))
        notifications.createForActiveAdmins("Laporan transfer terlambat", "Laporan untuk pesanan #${orderId.toString().take(8)} menunggu pemeriksaan.",
            "late_payment", "/admin/late-payment-reports/$id", now)
        return find(id).view
    }

    @Transactional
    fun review(actor: UUID, id: UUID, request: LatePaymentReviewRequest): LatePaymentReportDTO {
        val decision = request.decision.trim().lowercase(Locale.ROOT)
        if (decision !in setOf("tindak_lanjut", "bukti_tidak_cukup")) invalid("Keputusan harus tindak_lanjut atau bukti_tidak_cukup.")
        val reason = text(request.reason, "Catatan pemeriksaan")
        actors.requireLocked(actor, "admin")
        val row = lock(id)
        if (row.view.reviewDecision != null) {
            if (row.view.reviewDecision == decision && row.view.reviewReason == reason) return row.view
            conflict("Laporan sudah diperiksa.")
        }
        if (row.view.status != "diajukan") conflict("Laporan tidak menunggu pemeriksaan.")
        val now = now()
        val status = if (decision == "tindak_lanjut") "tindak_lanjut" else "ditutup"
        reports.review(id, actor, decision, reason, status, now)
        reports.audit(actor, id, "LATE_PAYMENT_REVIEWED", row.view.status, status, reason, now)
        notifications.create(row.view.buyerId, "Laporan transfer diperiksa",
            if (decision == "tindak_lanjut") "Admin akan menindaklanjuti laporan transfer Anda." else "Admin menilai bukti laporan transfer belum cukup.",
            "late_payment", "/late-payment-reports/$id", now)
        return find(id).view
    }

    @Transactional
    fun close(actor: UUID, id: UUID, request: LatePaymentCloseRequest): LatePaymentReportDTO {
        val reason = text(request.reason, "Catatan penutupan")
        actors.requireLocked(actor, "admin")
        val row = lock(id)
        if (row.view.status == "ditutup") {
            if (row.view.reviewDecision == "tindak_lanjut" && row.view.closeReason == reason) return row.view
            conflict("Laporan sudah ditutup.")
        }
        if (row.view.status != "tindak_lanjut") conflict("Laporan belum berada dalam tindak lanjut.")
        val now = now()
        reports.close(id, actor, reason, now)
        reports.audit(actor, id, "LATE_PAYMENT_CLOSED", row.view.status, "ditutup", reason, now)
        notifications.create(row.view.buyerId, "Laporan transfer ditutup", "Admin telah menutup laporan transfer Anda.",
            "late_payment", "/late-payment-reports/$id", now)
        return find(id).view
    }

    @Transactional(readOnly = true)
    fun list(actor: UUID, role: String, page: Int, size: Int): PageDTO<LatePaymentReportDTO> {
        requireRole(actor, role)
        validatePage(page, size)
        return reports.list(actor, role, page, size)
    }

    @Transactional(readOnly = true)
    fun detail(actor: UUID, role: String, id: UUID): LatePaymentReportDTO = authorized(actor, role, id).view

    @Transactional(readOnly = true)
    fun image(actor: UUID, role: String, id: UUID): PaymentImage = readImage(authorized(actor, role, id).evidencePath)

    private fun authorized(actor: UUID, role: String, id: UUID): LateReportRow {
        requireRole(actor, role)
        val row = find(id)
        if (role == "pembeli" && row.view.buyerId != actor) forbidden("Laporan bukan milik pembeli ini.")
        return row
    }
    private fun requireRole(actor: UUID, role: String) {
        if (role !in setOf("pembeli", "admin")) forbidden("Akses tidak diizinkan.")
        actors.require(actor, role)
    }
    private fun find(id: UUID) = reports.findById(id) ?: missing("Laporan tidak ditemukan.")
    private fun lock(id: UUID): LateReportRow {
        val orderId = reports.orderId(id) ?: missing("Laporan tidak ditemukan.")
        orders.lockById(orderId) ?: missing("Pesanan tidak ditemukan.")
        return reports.findById(id, lock = true) ?: missing("Laporan tidak ditemukan.")
    }
    private fun validateKey(key: String) { if (!Regex("[A-Za-z0-9_-]{1,128}").matches(key)) invalid("Idempotency-Key wajib, maksimal 128 karakter huruf/angka/_/-.") }
    private fun validatePage(page: Int, size: Int) { if (page < 0 || size !in 1..100) invalid("Paginasi tidak valid.") }
    private fun text(value: String, label: String) = value.trim().also { if (it.isBlank() || it.length > 1000) invalid("$label wajib diisi dan maksimal 1000 karakter.") }
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
                    catch (ex: Exception) { logger.error("Gagal membersihkan bukti laporan setelah rollback.") }
                }
            })
        } catch (ex: Exception) {
            try { store.delete(key) } catch (cleanup: Exception) { logger.error("Gagal membersihkan bukti laporan.") }
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
    private fun unavailable(): Nothing = throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED", "Penyimpanan bukti laporan privat belum tersedia.")
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)
    private companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
