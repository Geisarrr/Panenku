package id.panenku.app.ui

import id.panenku.app.features.f3_delivery_schedule.*
import id.panenku.app.features.f4_checkout.*
import id.panenku.app.features.f5_order_status.*
import id.panenku.app.features.f6_payment_verification.*
import id.panenku.app.ui.components.*

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import id.panenku.app.data.*
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
@Config(sdk=[34],qualifiers="w375dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransactionComposeTest {
    @get:Rule val compose=createComposeRule()
    private fun scroll(text:String)=compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    @Test fun buyerCanCheckoutAndProceedAfterFarmerAcceptance() {
        compose.setContent { PanenkuTheme { PanenkuApp() } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Buat akun baru").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sudah memiliki akun? Masuk").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("rina@example.com")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("demopanenku")
        compose.onNodeWithText("Masuk").performScrollTo().performClick()
        compose.onNodeWithText("Toko",useUnmergedTree=true).performClick()
        scroll("Tambah ke keranjang · 1 kg");compose.onAllNodesWithText("Tambah ke keranjang · 1 kg")[0].performScrollTo().performClick()
        compose.onNodeWithText("Keranjang",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Lanjut Checkout").performClick()
        compose.onNodeWithText("Checkout").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/screenshots/17-checkout.png")
        scroll("Buat Pesanan");compose.onNodeWithText("Buat Pesanan").performClick()
        compose.onNodeWithText("Pesanan berhasil dibuat").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/screenshots/18-pesanan-dibuat.png")
        scroll("Simulasikan petani menerima");compose.onNodeWithText("Simulasikan petani menerima").performClick()
        scroll("Lanjut pembayaran");compose.onNodeWithText("Lanjut pembayaran").performClick()
        compose.onNodeWithText("Pembayaran ke Petani").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/screenshots/19-pembayaran.png")
        scroll("Kirim Bukti Pembayaran");compose.onNodeWithText("Kirim Bukti Pembayaran").assertIsNotEnabled()
    }
    @Test @Config(qualifiers="w320dp-h1000dp-mdpi")
    fun buyerCanAddAddressWithoutChangingPrimary() {
        val repo=DemoBuyerRepository()
        compose.setContent { val data by repo.data.collectAsState();PanenkuTheme {
            BuyerAddressScreen(data,{}, { id->runBlocking {repo.selectAddress(id)} }, { address,primary,done->runBlocking {repo.saveAddress(address,primary)};done() })
        } }
        compose.onRoot().captureRoboImage("build/outputs/screenshots/20-alamat.png")
        scroll("Simpan alamat baru");compose.onNodeWithText("Simpan alamat baru").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Label alamat").performScrollTo().performTextInput("Dapur produksi")
        compose.onNodeWithContentDescription("Alamat lengkap").performScrollTo().performTextInput("Jl. Mawar nomor 12, Lowokwaru, Malang")
        scroll("Simpan alamat baru");compose.onNodeWithText("Simpan alamat baru").performClick()
        assertEquals(3,repo.data.value.addresses.size);assertEquals(0,repo.data.value.profile.selectedAddress)
    }
    @Test fun rejectedPaymentExplainsReasonAndRequiresReplacement() {
        val repo=DemoBuyerRepository();runBlocking {repo.submitPaymentProof("PES-2026-003","content://test/proof");repo.simulatePaymentReview("PES-2026-003",false)}
        compose.setContent { val data by repo.data.collectAsState();PanenkuTheme {
            BuyerPaymentScreen(data.orders.first {it.id=="PES-2026-003"},repo.now,{}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Bukti pembayaran ditolak").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/screenshots/21-bukti-ditolak.png")
        scroll("Unggah ulang bukti");compose.onNodeWithText("Unggah ulang bukti").assertIsNotEnabled()
        assertEquals(OrderStatus.PAYMENT,repo.data.value.orders.first {it.id=="PES-2026-003"}.order.status)
    }
    @Test fun arrivedOrderWaitsForExplicitReceiptConfirmation() {
        val repo=DemoBuyerRepository()
        compose.setContent { val data by repo.data.collectAsState();PanenkuTheme {
            BuyerTrackingScreen(data.orders.first {it.id=="PES-2026-001"},{}, {runBlocking {repo.confirmReceipt("PES-2026-001")}}, {}, {_,done->done()}, {}, {}, {})
        } }
        compose.onRoot().captureRoboImage("build/outputs/screenshots/22-tracking.png")
        scroll("Konfirmasi pesanan diterima");compose.onNodeWithText("Konfirmasi pesanan diterima").performClick()
        assertNull(repo.data.value.orders.first {it.id=="PES-2026-001"}.receivedAt)
        compose.onNodeWithText("Ya, pesanan diterima").performClick()
        assertEquals(OrderStatus.COMPLETED,repo.data.value.orders.first {it.id=="PES-2026-001"}.order.status)
    }
    @Test fun scheduleCanBeAcceptedOnlyOnce() {
        val repo=DemoBuyerRepository()
        compose.setContent { val data by repo.data.collectAsState();PanenkuTheme {
            BuyerScheduleResponseScreen(data.orders.first {it.id=="PES-2026-006"},{}, {approved->runBlocking {repo.respondSchedule("PES-2026-006",approved)}})
        } }
        compose.onRoot().captureRoboImage("build/outputs/screenshots/23-perubahan-jadwal.png")
        scroll("Setujui jadwal baru");compose.onNodeWithText("Setujui jadwal baru").performClick()
        compose.onNodeWithText("Setujui jadwal baru").assertIsNotEnabled()
        assertEquals(ProposalStatus.ACCEPTED,repo.data.value.orders.first {it.id=="PES-2026-006"}.proposal!!.status)
    }
    @Test @Config(qualifiers="w800dp-h1000dp-mdpi")
    fun checkoutOutsideAreaAllowsPickup() {
        val data=BuyerSamples.initial.copy(profile=BuyerProfile(selectedAddress=1),cart=mapOf("sawi" to 20,"tomat" to 10))
        compose.setContent {PanenkuTheme {BuyerCheckoutScreen(data,BuyerSamples.now,10000000,{}, {}, {})}}
        scroll("Buat Pesanan");compose.onNodeWithText("Buat Pesanan").assertIsNotEnabled()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("Ambil sendiri").performClick()
        compose.onRoot().captureRoboImage("build/outputs/screenshots/24-checkout-tablet.png")
        scroll("Buat Pesanan");compose.onNodeWithText("Buat Pesanan").assertIsEnabled()
    }
}
