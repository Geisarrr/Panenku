package com.panenku.backend.modules.shared.service

import com.panenku.backend.common.exception.conflict
import com.panenku.backend.common.exception.invalid

/**
 * Aturan transisi status pesanan (F5).
 *
 * Fungsi di sini murni (tanpa database) supaya mudah diuji dan supaya
 * service tidak menyebar aturan state machine ke banyak tempat.
 */
object OrderStatusRules {
    const val MENUNGGU_PETANI = "menunggu_petani"
    const val MENUNGGU_PEMBAYARAN = "menunggu_pembayaran"
    const val DIPROSES = "diproses"
    const val SIAP_DIAMBIL = "siap_diambil"
    const val DIANTAR = "diantar"
    const val MENUNGGU_PENERIMAAN = "menunggu_penerimaan"
    const val SELESAI = "selesai"
    const val DITOLAK = "ditolak"
    const val DIBATALKAN = "dibatalkan"
    const val GAGAL_DIPENUHI = "gagal_dipenuhi"

    enum class FulfillmentAction { NONE, FULFILL_STOCK, RELEASE_STOCK }
    data class FulfillmentTransition(val targetStatus: String, val action: FulfillmentAction)

    /** Pembeli hanya boleh membatalkan sebelum petani merespons. */
    fun validateCancel(currentStatus: String) {
        if (currentStatus != MENUNGGU_PETANI) {
            conflict("Pesanan hanya dapat dibatalkan saat masih menunggu konfirmasi petani.")
        }
    }

    /** Petani hanya boleh merespons saat pesanan masih menunggu konfirmasi. */
    fun validateRespond(currentStatus: String) {
        if (currentStatus != MENUNGGU_PETANI) {
            conflict("Pesanan tidak sedang menunggu konfirmasi petani.")
        }
    }

    fun respondTarget(decision: String): String = when (decision.trim().lowercase()) {
        "terima" -> MENUNGGU_PEMBAYARAN
        "tolak" -> DITOLAK
        else -> invalid("Keputusan harus 'terima' atau 'tolak'.")
    }

    fun validateReceive(currentStatus: String) {
        if (currentStatus != MENUNGGU_PENERIMAAN) {
            conflict("Pesanan belum berada pada tahap menunggu penerimaan.")
        }
    }

    /**
     * Menentukan status tujuan dan aksi stok untuk endpoint fulfillment.
     *
     * Alur:
     * - diproses + pickup  -> siap_diambil
     * - diproses + delivery -> diantar
     * - diproses -> gagal_dipenuhi (lepas reservasi)
     * - siap_diambil/diantar -> menunggu_penerimaan (stok fisik dipenuhi)
     */
    fun fulfillmentTarget(
        currentStatus: String,
        fulfillmentMethod: String,
        requestedStatus: String
    ): FulfillmentTransition {
        val requested = requestedStatus.trim().lowercase()
        return when {
            currentStatus == DIPROSES && requested == SIAP_DIAMBIL && fulfillmentMethod == "pickup" ->
                FulfillmentTransition(SIAP_DIAMBIL, FulfillmentAction.NONE)

            currentStatus == DIPROSES && requested == DIANTAR && fulfillmentMethod == "delivery" ->
                FulfillmentTransition(DIANTAR, FulfillmentAction.NONE)

            currentStatus == DIPROSES && requested == GAGAL_DIPENUHI ->
                FulfillmentTransition(GAGAL_DIPENUHI, FulfillmentAction.RELEASE_STOCK)

            currentStatus in setOf(SIAP_DIAMBIL, DIANTAR) && requested == MENUNGGU_PENERIMAAN ->
                FulfillmentTransition(MENUNGGU_PENERIMAAN, FulfillmentAction.FULFILL_STOCK)

            else -> invalid(
                "Status pemenuhan tidak valid untuk pesanan $currentStatus " +
                    "dengan metode $fulfillmentMethod."
            )
        }
    }
}
