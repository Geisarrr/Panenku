package id.panenku.app.ui.connected

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import id.panenku.app.ui.theme.PanenkuTheme
import id.panenku.app.data.remote.ApiUser
import id.panenku.app.data.remote.json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
}
