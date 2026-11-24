package id.panenku.app.ui.connected

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import id.panenku.app.ui.theme.PanenkuTheme
import id.panenku.app.data.remote.ApiUser
import id.panenku.app.data.remote.json
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectedAppTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun openingFlowReachesSellerLoginAndDoesNotOfferDemoAccess() {
        compose.setContent { PanenkuTheme { ConnectedPanenkuApp() } }
        compose.mainClock.advanceTimeBy(3000)
        compose.waitForIdle()
        compose.onNodeWithText("Pembeli").assertExists()
        compose.onNodeWithText("Petani").assertExists()
        compose.onNodeWithTag("role-petani").performClick()
        compose.onNodeWithText("Sudah memiliki akun? Masuk").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Selamat Datang Kembali!").assertExists()
        compose.onNodeWithTag("input-Email atau nomor HP").assertExists()
        compose.onNodeWithText("Masuk").assertIsEnabled()
        compose.onNodeWithText("Mode demo").assertDoesNotExist()
    }

    @Test fun adminHubExposesEveryManagementArea() {
        compose.setContent { PanenkuTheme { ConnectedAdminHub { } } }
        listOf("Daftar pengguna", "Pengaturan transaksi & WhatsApp CS", "Audit log", "Sengketa refund",
            "Laporan pembayaran terlambat", "Kelola komoditas").forEach { label ->
            compose.onNodeWithText(label).assertExists()
        }
    }

    @Test fun farmerPaymentMethodsHaveEmptyStateAndSafeDisabledActions() {
        val vm = ConnectedViewModel(compose.activity.application)
        val farmer = ApiUser("farmer", "farmer@example.test", "petani", "active")
        compose.setContent { PanenkuTheme { ConnectedAccount(ConnectedState(user = farmer), vm) } }
        compose.onNodeWithText("Belum ada rekening atau QRIS.").assertExists()
        compose.onNodeWithText("Simpan rekening").assertIsNotEnabled()
        compose.onNodeWithText("Simpan QRIS").assertIsNotEnabled()
    }

    @Test fun farmerCanEnterQrisReplacementMode() {
        val vm = ConnectedViewModel(compose.activity.application)
        val farmer = ApiUser("farmer", "farmer@example.test", "petani", "active")
        val qris = json("id" to "qris-id", "methodType" to "qris", "accountHolderName" to "Asep Laris", "hasQrisImage" to true)
        compose.setContent { PanenkuTheme { ConnectedAccount(ConnectedState(user = farmer, paymentMethods = listOf(qris)), vm) } }
        compose.onNodeWithText("Ganti QRIS").performScrollTo().performClick()
        compose.onNodeWithText("Ganti QRIS terpilih").assertExists()
        compose.onNodeWithText("Batal ganti QRIS").assertExists()
    }

    @Test fun latePaymentReportRequiresCompleteForm() {
        val vm = ConnectedViewModel(compose.activity.application)
        compose.setContent { PanenkuTheme { ConnectedLatePayments(ConnectedState(), vm, false) } }
        compose.onNodeWithText("Kirim laporan").performScrollTo().assertIsNotEnabled()
    }

    @Test fun adminLatePaymentsShowEmptyState() {
        val vm = ConnectedViewModel(compose.activity.application)
        compose.setContent { PanenkuTheme { ConnectedLatePayments(ConnectedState(), vm, true) } }
        compose.onNodeWithText("Belum ada laporan pembayaran terlambat.").assertExists()
    }
    @Test fun checkoutNeedsConsentAndChangedQuoteRequiresNewConsent() {
        val quote = androidx.compose.runtime.mutableStateOf(json("canCheckout" to true, "totalPayment" to 16250,
            "subtotalItems" to 6250, "deliveryFee" to 10000, "transactionLimitApplied" to 10000000,
            "scheduledDate" to "2026-12-01", "scheduledTimeSlot" to "08:00-10:00", "timeZone" to "Asia/Jakarta",
            "fulfillmentMethod" to "delivery", "deliveryDistrict" to "kepanjen", "deliveryAddress" to "Jl. Panen 4",
            "items" to listOf(json("name" to "Sawi Segar", "quantityKg" to "0.25", "pricePerKg" to 25000, "itemSubtotal" to 6250))))
        compose.setContent { PanenkuTheme { CheckoutSummary(quote.value, false) {} } }
        compose.onNodeWithText("Buat pesanan").assertIsNotEnabled()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Buat pesanan").assertIsEnabled()
        if (System.getProperty("roborazzi.test.record") == "true") compose.onRoot().captureRoboImage("build/outputs/screenshots/finalisasi-checkout-consent.png")
        compose.runOnIdle { quote.value = quote.value.deepCopy().apply { addProperty("totalPayment", 17000) } }
        compose.onNodeWithText("Buat pesanan").assertIsNotEnabled()
        compose.onNodeWithText("Jadwal: 2026-12-01 · 08:00-10:00 (Asia/Jakarta)").assertExists()
    }

    @Test fun cartShowsEmptyLoadingAndUnavailableItemStates() {
        val vm = ConnectedViewModel(compose.activity.application)
        val state = androidx.compose.runtime.mutableStateOf(ConnectedState(busy = true))
        compose.setContent { PanenkuTheme { ConnectedCart(state.value, vm, {}, {}) } }
        compose.onNodeWithText("Memuat keranjang…").assertExists()
        compose.onNodeWithText("Keranjangmu masih kosong.").assertDoesNotExist()
        compose.runOnIdle { state.value = ConnectedState() }
        compose.onNodeWithText("Keranjangmu masih kosong.").assertExists()
        compose.runOnIdle {
            state.value = ConnectedState(cart = json("items" to listOf(json("id" to "row", "name" to "Sawi",
                "quantityKg" to 1, "available" to false, "unavailableReason" to "Stok tidak cukup")), "hasUnavailableItems" to true))
        }
        compose.onNodeWithText("Stok tidak cukup").assertExists()
        if (System.getProperty("roborazzi.test.record") == "true") compose.onRoot().captureRoboImage("build/outputs/screenshots/finalisasi-cart-stock.png")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Lanjut checkout"))
        compose.onNodeWithText("Lanjut checkout").assertIsNotEnabled()
    }

}
