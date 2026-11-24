package id.panenku.app.ui

import id.panenku.app.features.f1_catalog.*
import id.panenku.app.features.f5_order_status.*
import id.panenku.app.features.f7_refund.*
import id.panenku.app.ui.account.*
import id.panenku.app.ui.components.*

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import id.panenku.app.data.buyer.*
import id.panenku.app.navigation.PanenkuApp
import id.panenku.app.ui.buyer.*
import id.panenku.app.ui.theme.PanenkuTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BuyerComposeFlowTest {
    @get:Rule val compose = createComposeRule()
    private fun scroll(text: String) = compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    private fun refundRepo(): DemoBuyerRepository = DemoBuyerRepository().also { repo -> runBlocking {
        repo.submitRefund(RefundRequest(BuyerSamples.completed.id, "Produk rusak", "Sayur rusak saat diterima pembeli.", "content://test/photo", "BCA", "000000000000", "Rina Catering", true))
        repo.simulateRefundResponse(BuyerSamples.completed.id, true)
    } }
    @Test fun buyerCanLoginAndSubmitOneReview() {
        compose.setContent { PanenkuTheme { PanenkuApp() } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Buat akun baru").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sudah memiliki akun? Masuk").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("rina@example.com")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("demopanenku")
        compose.onNodeWithText("Masuk").performScrollTo().performClick()
        compose.onNodeWithText("Pantau pesanan hasil panenmu").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/screenshots/07-pembeli-pesanan.png")
        compose.onNodeWithText("Selesai", substring = false).performClick()
        compose.onAllNodesWithText("Beri Ulasan")[0].performScrollTo().performClick()
        compose.onNodeWithContentDescription("Beri nilai 5 bintang").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Sayurnya segar dan pengiriman tepat waktu.")
        scroll("Kirim");compose.onNodeWithText("Kirim").performClick()
        scroll("Ulasan sudah dikirim");compose.onNodeWithText("Ulasan sudah dikirim").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/screenshots/08-pembeli-ulasan.png")
    }
    @Test fun profileCanBeUpdatedAndShowsSavedIdentity() {
        var profile by mutableStateOf(BuyerProfile())
        compose.setContent { PanenkuTheme { BuyerAccountScreen(profile, { profile = it }, {}, {}, {}, {}) } }
        compose.onNode(hasSetTextAction() and hasText("Rina Catering")).performTextReplacement("Dapur Rina")
        scroll("Simpan profil");compose.onNodeWithText("Simpan profil").performClick()
        assertEquals("Dapur Rina", profile.name)
        compose.onRoot().captureRoboImage("build/outputs/screenshots/09-pembeli-akun.png")
    }
    @Test fun missingFundsCreatesTicketAndReceivingFundsNeedsConfirmation() {
        val repo = refundRepo()
        var ticketId: String? = null
        compose.setContent {
            val data by repo.data.collectAsState()
            val refund = data.refunds.last()
            PanenkuTheme { BuyerRefundStatusScreen(refund, data.ticketFor(refund.id), repo.now, {}, {},
                { runBlocking { repo.confirmRefundReceived(refund.orderId) } },
                { runBlocking { repo.reportMissingRefund(refund.orderId) } },
                { ticketId = runBlocking { repo.createTicket(refund.orderId) } }) }
        }
        compose.onRoot().captureRoboImage("build/outputs/screenshots/10-pembeli-refund.png")
        scroll("Dana belum diterima");compose.onNodeWithText("Dana belum diterima").performClick()
        scroll("Buat tiket eskalasi");compose.onNodeWithText("Buat tiket eskalasi").performClick()
        assertNotNull(ticketId)
        scroll("Dana sudah diterima");compose.onNodeWithText("Dana sudah diterima").performClick()
        assertEquals(BuyerRefundStatus.AWAITING_FUNDS, repo.data.value.refunds.last().status)
        compose.onNodeWithText("Ya, dana sudah masuk").performClick()
        assertEquals(BuyerRefundStatus.COMPLETED, repo.data.value.refunds.last().status)
    }
    @Test fun ticketExistsBeforeCsAndAcceptsAdditionalNotes() {
        val repo = refundRepo()
        runBlocking { repo.reportMissingRefund(BuyerSamples.completed.id); repo.createTicket(BuyerSamples.completed.id) }
        compose.setContent {
            val data by repo.data.collectAsState()
            val ticket = data.tickets.single()
            PanenkuTheme { BuyerHelpScreen(ticket, data.refunds.last(), null, {}, { note, uri, done ->
                runBlocking { repo.addTicketEvidence(ticket.id, note, uri) };done()
            }, {}) }
        }
        compose.onRoot().captureRoboImage("build/outputs/screenshots/11-pembeli-bantuan.png")
        scroll("Tambah keterangan / bukti")
        compose.onNodeWithText("Tambah keterangan / bukti").performClick()
        compose.onNode(hasSetTextAction()).performScrollTo()
        compose.onRoot().captureRoboImage("build/outputs/screenshots/15-pembeli-form-bukti.png")
        compose.onNode(hasSetTextAction()).performTextInput("Sudah memeriksa mutasi rekening kembali.")
        scroll("Simpan");compose.onNodeWithText("Simpan").performClick()
        assertEquals(1, repo.data.value.tickets.single().notes.size)
        scroll("Hubungi WhatsApp CS");compose.onNodeWithText("Hubungi WhatsApp CS").performClick()
        compose.onNodeWithText("Salin pesan tiket").assertIsDisplayed()
        assertEquals(1, repo.data.value.tickets.size)
    }
    @Test @Config(qualifiers = "w800dp-h1000dp-mdpi")
    fun tabletStoreAddsProductToCartAndShowsReviews() {
        val repo = DemoBuyerRepository()
        compose.setContent {
            val data by repo.data.collectAsState()
            PanenkuTheme { BuyerStoreScreen(data, {}, { id -> runBlocking { repo.addToCart(id, 1) } }, {}) }
        }
        compose.onRoot().captureRoboImage("build/outputs/screenshots/12-pembeli-toko-tablet.png")
        scroll("Tambah ke keranjang · 1 kg")
        compose.onAllNodesWithText("Tambah ke keranjang · 1 kg")[0].performScrollTo().performClick()
        assertEquals(1, repo.data.value.cart["sawi"])
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("Ulasan", substring = false).performClick()
        compose.onNodeWithText("Contoh ulasan pembeli").performScrollTo().assertIsDisplayed()
    }
    @Test @Config(qualifiers = "w320dp-h1000dp-mdpi")
    fun reviewSupportsLargeTextOnSmallPhone() {
        var sent = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                PanenkuTheme { BuyerReviewScreen(BuyerSamples.completed, null, {}, { _, _, _ -> sent = true }) }
            }
        }
        scroll("Kirim");compose.onNodeWithText("Kirim").assertIsNotEnabled()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Beri nilai 5 bintang"))
        compose.onNodeWithContentDescription("Beri nilai 5 bintang").performScrollTo().performClick()
        scroll("Kirim");compose.onNodeWithText("Kirim").assertIsEnabled()
        compose.onRoot().captureRoboImage("build/outputs/screenshots/13-pembeli-font-besar.png")
        compose.onNodeWithText("Kirim").performClick();assertTrue(sent)
    }
    @Test fun completedOrderShowsItsOwnPaymentBreakdown() {
        compose.setContent { PanenkuTheme { BuyerOrderDetailScreen(BuyerSamples.historical, null, null,
            BuyerSamples.now, {}, {}, {}, {}, {}) } }
        compose.onRoot().captureRoboImage("build/outputs/screenshots/16-pembeli-detail-selesai.png")
        scroll("Rp212.500");compose.onNodeWithText("Rp212.500").assertIsDisplayed()
        compose.onNodeWithText("Rp1.000").assertExists()
        compose.onNodeWithText("Rp1.500").assertExists()
        scroll("Ajukan refund");compose.onNodeWithText("Ajukan refund").assertIsNotEnabled()
    }
    @Test fun refundFormRequiresProofAndConsent() {
        compose.setContent { PanenkuTheme { BuyerRefundFormScreen(BuyerSamples.completed, null, BuyerSamples.now, {}, {}, {}) } }
        scroll("Kirim pengajuan Rp202.500")
        compose.onNodeWithText("Kirim pengajuan Rp202.500").assertIsNotEnabled()
        compose.onRoot().captureRoboImage("build/outputs/screenshots/14-pembeli-form-refund.png")
    }
}
