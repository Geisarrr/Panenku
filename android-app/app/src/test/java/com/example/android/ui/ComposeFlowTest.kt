package id.panenku.app.ui

import id.panenku.app.features.f1_catalog.*
import id.panenku.app.features.f2_stock_reservation.*
import id.panenku.app.ui.dashboard.*
import id.panenku.app.ui.components.*

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import id.panenku.app.data.*
import id.panenku.app.navigation.PanenkuApp
import id.panenku.app.ui.admin.AdminHomeScreen
import id.panenku.app.ui.auth.*
import id.panenku.app.ui.theme.PanenkuTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeFlowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun farmerCanLoginNavigateAndUpdateProductPrice() {
        compose.setContent { PanenkuTheme { PanenkuApp() } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Buat akun baru").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Petani").performClick()
        compose.onNodeWithText("Sudah memiliki akun? Masuk").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("petani@example.com")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("demopanenku")
        compose.onNodeWithText("Masuk").performScrollTo().performClick()
        compose.onNodeWithText("Pantau hasil penjualan tani Anda").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/screenshots/01-beranda.png")
        compose.onNodeWithText("Stok", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Stok & Produk").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/screenshots/02-stok.png")
        compose.onAllNodesWithText("Edit produk & stok")[0].performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("5000")).performScrollTo().performTextReplacement("6500")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Terbitkan produk"))
        compose.onNodeWithText("Terbitkan produk").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("Kembali").performScrollTo().performClick()
        compose.onNodeWithText("Rp6.500/kg").performScrollTo().assertIsDisplayed()
    }
    @Test fun productSearchShowsEmptyStateAndCanBeCleared() {
        compose.setContent { PanenkuTheme { ProductListScreen(SampleData.products, {}, {}, {}) } }
        compose.onNode(hasSetTextAction()).performTextInput("tidakada")
        compose.onNodeWithText("Belum ada data").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Hapus pencarian").performScrollTo().performClick()
        compose.onNodeWithText("Sawi Putih").performScrollTo().assertIsDisplayed()
    }
    @Test fun loginShowsValidationAndPasswordVisibilityControl() {
        compose.setContent { PanenkuTheme { LoginScreen(Role.FARMER, {}, {}, {}, {}) } }
        compose.onRoot().captureRoboImage("build/outputs/screenshots/03-login.png")
        compose.onNodeWithText("Masuk").performScrollTo().performClick()
        compose.onNodeWithText("Email atau nomor HP wajib diisi.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Tampilkan kata sandi").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Sembunyikan kata sandi").assertExists()
    }
    @Test @Config(qualifiers = "w800dp-h1000dp-mdpi")
    fun tabletDashboardKeepsAllMetricsVisible() {
        compose.setContent { PanenkuTheme { FarmerHomeScreen(SampleData.data, {}, {}, {}, {}) } }
        listOf("Pesanan Baru", "Jadwal Kirim", "Stok Panen", "Pembayaran").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onRoot().captureRoboImage("build/outputs/screenshots/04-tablet.png")
    }
    @Test @Config(qualifiers = "w320dp-h900dp-mdpi")
    fun smallPhoneSupportsProductEditing() {
        compose.setContent { PanenkuTheme { ProductFormScreen(SampleData.products.last(), {}, {}) } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Terbitkan produk"))
        compose.onNodeWithText("Terbitkan produk").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/screenshots/05-small-phone.png")
    }
    @Test fun adminHasWorkingNavigationCallbacks() {
        var destination = ""
        compose.setContent { PanenkuTheme { AdminHomeScreen({ destination = it }, {}) } }
        compose.onRoot().captureRoboImage("build/outputs/screenshots/06-admin.png")
        compose.onNodeWithText("Verifikasi petani").performClick()
        assert(destination == "admin-verification")
    }
}
