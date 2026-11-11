package com.panenku.backend

import com.panenku.backend.common.exception.ApiException
import com.panenku.backend.modules.shared.service.OrderStatusRules
import com.panenku.backend.modules.shared.service.OrderStatusRules.FulfillmentAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class OrderStatusRulesTest {

    @Test
    fun `pembeli hanya bisa batal saat menunggu petani`() {
        OrderStatusRules.validateCancel(OrderStatusRules.MENUNGGU_PETANI)

        val error = assertThrows(ApiException::class.java) {
            OrderStatusRules.validateCancel(OrderStatusRules.MENUNGGU_PEMBAYARAN)
        }
        assertEquals(HttpStatus.CONFLICT, error.status)
    }

    @Test
    fun `respons petani menerima dan menolak menghasilkan status yang benar`() {
        assertEquals(
            OrderStatusRules.MENUNGGU_PEMBAYARAN,
            OrderStatusRules.respondTarget("terima")
        )
        assertEquals(
            OrderStatusRules.DITOLAK,
            OrderStatusRules.respondTarget(" TOLAK ")
        )

        val error = assertThrows(ApiException::class.java) {
            OrderStatusRules.respondTarget("mungkin")
        }
        assertEquals(HttpStatus.BAD_REQUEST, error.status)
    }

    @Test
    fun `fulfillment pickup berjalan dari diproses ke siap diambil`() {
        val transition = OrderStatusRules.fulfillmentTarget(
            OrderStatusRules.DIPROSES, "pickup", "siap_diambil"
        )
        assertEquals(OrderStatusRules.SIAP_DIAMBIL, transition.targetStatus)
        assertEquals(FulfillmentAction.NONE, transition.action)
    }

    @Test
    fun `fulfillment delivery berjalan dari diproses ke diantar`() {
        val transition = OrderStatusRules.fulfillmentTarget(
            OrderStatusRules.DIPROSES, "delivery", "diantar"
        )
        assertEquals(OrderStatusRules.DIANTAR, transition.targetStatus)
        assertEquals(FulfillmentAction.NONE, transition.action)
    }

    @Test
    fun `gagal dipenuhi melepas reservasi stok`() {
        val transition = OrderStatusRules.fulfillmentTarget(
            OrderStatusRules.DIPROSES, "pickup", "gagal_dipenuhi"
        )
        assertEquals(OrderStatusRules.GAGAL_DIPENUHI, transition.targetStatus)
        assertEquals(FulfillmentAction.RELEASE_STOCK, transition.action)
    }

    @Test
    fun `menunggu penerimaan memenuhi stok fisik`() {
        val transition = OrderStatusRules.fulfillmentTarget(
            OrderStatusRules.SIAP_DIAMBIL, "pickup", "menunggu_penerimaan"
        )
        assertEquals(OrderStatusRules.MENUNGGU_PENERIMAAN, transition.targetStatus)
        assertEquals(FulfillmentAction.FULFILL_STOCK, transition.action)
    }

    @Test
    fun `metode pemenuhan yang salah ditolak`() {
        val error = assertThrows(ApiException::class.java) {
            OrderStatusRules.fulfillmentTarget(
                OrderStatusRules.DIPROSES, "delivery", "siap_diambil"
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, error.status)
    }

    @Test
    fun `pembeli hanya bisa konfirmasi saat menunggu penerimaan`() {
        OrderStatusRules.validateReceive(OrderStatusRules.MENUNGGU_PENERIMAAN)

        val error = assertThrows(ApiException::class.java) {
            OrderStatusRules.validateReceive(OrderStatusRules.DIPROSES)
        }
        assertEquals(HttpStatus.CONFLICT, error.status)
    }
}
