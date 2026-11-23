package id.panenku.app.ui.connected.farmer

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import com.github.takahirom.roborazzi.captureRoboImage
import id.panenku.app.data.remote.*
import id.panenku.app.ui.theme.PanenkuTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w375dp-h956dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FarmerScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun host(nav: String? = null, content: @Composable () -> Unit) {
        val names = listOf("chili", "shallot", "tomato", "cabbage", "tomato_stock", "shallot_hero")
        val images: Map<String, Painter> = names.associate { name ->
            val bitmap = javaClass.classLoader!!.getResourceAsStream("farmer-design/$name.jpg")!!.use { BitmapFactory.decodeStream(it) }
            "fixture://$name" to BitmapPainter(bitmap.asImageBitmap())
        }
        compose.setContent { PanenkuTheme {
            CompositionLocalProvider(LocalFarmerInsets provides WindowInsets(top = 47.dp, bottom = 34.dp), LocalFarmerPhoto provides { images[it] }) {
                FarmerTheme { Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { content() }
                    if (nav != null) FarmerNavigation(nav, {}, {})
                } }
            }
        } }
    }
    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/outputs/screenshots/farmer-$name.png")
    @Test fun navigationKeepsCreateActionOnEveryMainTab() {
        var route by mutableStateOf("home")
        var creations = 0
        compose.setContent { PanenkuTheme {
            CompositionLocalProvider(LocalFarmerInsets provides WindowInsets(0.dp)) {
                FarmerTheme { Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(1f))
                    FarmerNavigation(route, { route = it }, { creations++ })
                } }
            }
        } }
        listOf("home" to "Beranda", "stock" to "Stok", "orders" to "Pesanan", "profile" to "Profil").forEach { (key, label) ->
            compose.onNodeWithText(label).performClick()
            compose.runOnIdle { assertEquals(key, route) }
            compose.onNodeWithContentDescription("Buat produk baru").assertIsDisplayed().performClick()
            compose.onNodeWithTag("farmer-navbar").captureRoboImage("build/outputs/screenshots/navbar-$key.png")
        }
        compose.runOnIdle { assertEquals(4, creations) }
    }
    @Test fun renderHome() {
        val base = FarmerDesignFixtures.state()
        val products = listOf(FarmerDesignFixtures.product("cabai", "Cabai Merah", "Cabai", 28000, 50, 0, "aktif", "chili"),
            FarmerDesignFixtures.product("bawang", "Bawang Merah", "Bawang", 35000, 12, 0, "aktif", "shallot"),
            FarmerDesignFixtures.product("tomat", "Tomat Sayur", "Sayur", 12000, 0, 0, "aktif", "tomato"))
        val orders = (1..5).map { FarmerDesignFixtures.order(id = "pending-$it") } + (1..2).map { FarmerDesignFixtures.order("diproses", "shipping-$it") }
        host("home") { FarmerHomeScreen(base.copy(products = products, orders = orders), {}, {}, {}, {}, LocalDate.of(2026,6,24), "Selamat pagi") }
        capture("home")
    }
    @Test @Config(qualifiers = "w375dp-h1540dp-mdpi") fun renderStock() {
        host("stock") { FarmerStockScreen(FarmerDesignFixtures.state(), {}, {}, {}) }; capture("stock")
    }
    @Test @Config(qualifiers = "w375dp-h1438dp-mdpi") fun renderEditor() {
        val s = FarmerDesignFixtures.state(); val p = s.products.last().deepCopy().apply { addProperty("photoAccessUrl", "fixture://shallot_hero") }
        host { FarmerProductScreen(p, s.copy(products = s.products.dropLast(1) + p), {}) { _, _, _, _, _ -> } }; capture("editor")
    }
    @Test @Config(qualifiers = "w375dp-h986dp-mdpi") fun renderOrders() {
        host("orders") { FarmerOrdersScreen(FarmerDesignFixtures.state(), open = {}) }; capture("orders")
    }
    @Test @Config(qualifiers = "w375dp-h1278dp-mdpi") fun renderPending() {
        host { FarmerPendingScreen(FarmerDesignFixtures.state(), FarmerDesignFixtures.order(), {}, { _, _ -> }, {}) }; capture("pending")
    }
    @Test @Config(qualifiers = "w375dp-h1461dp-mdpi") fun renderPayment() {
        host { FarmerPaymentScreen(FarmerDesignFixtures.state(), FarmerDesignFixtures.order("verifikasi_pembayaran", "PES-2026-006"), {}, {}, { _, _, _ -> }, {}) }; capture("payment")
    }
    @Test @Config(qualifiers = "w375dp-h1157dp-mdpi") fun renderProcessing() {
        host { FarmerProcessingScreen(FarmerDesignFixtures.state(), FarmerDesignFixtures.order("diproses", "PES-2026-001"), {}, { _, _ -> }, {}) }; capture("processing")
    }
    @Test @Config(qualifiers = "w375dp-h1138dp-mdpi") fun renderSchedule() {
        host { FarmerScheduleScreen(FarmerDesignFixtures.state(), FarmerDesignFixtures.order(), {}, "Hujan deras membuat panen tertunda. Perlu satu hari tambahan agar sayuran tetap segar.") { _, _, _ -> } }; capture("schedule")
    }
    @Test @Config(qualifiers = "w320dp-h640dp-mdpi") fun smallPhoneCanScrollToProductActions() {
        val s = FarmerDesignFixtures.state()
        host { FarmerProductScreen(s.products.last(), s, {}) { _, _, _, _, _ -> } }
        compose.onNodeWithText("Simpan sebagai draft").performScrollTo().assertIsDisplayed()
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun sellerEntryWaitsForLoginThenLoadsHomeAndStockFromApi() {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()
        val vm = id.panenku.app.ui.connected.ConnectedViewModel(compose.activity.application, PanenkuApi(server.url("/api/v1/").toString()))
        fun enqueue(data: String) { server.enqueue(okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "application/json").setBody("""{"success":true,"data":$data,"message":"Berhasil"}""")) }
        enqueue("""{"accessToken":"access","refreshToken":"refresh","user":{"id":"farmer","email":"farmer@example.test","role":"petani","status":"active"}}""")
        repeat(2) {
            enqueue("""{"items":[],"totalElements":0}"""); enqueue("""{"items":[],"totalElements":0}""")
            enqueue("""{"ownerName":"Petani API","storeName":"Toko API"}"""); enqueue("[]"); enqueue("[]")
        }
        try {
            compose.runOnIdle { vm.login("farmer@example.test", "password") }
            compose.setContent { PanenkuTheme {
                val state by vm.state.collectAsState()
                if (state.user?.role == "petani") FarmerConnectedApp(state, vm)
            } }
            try { compose.waitUntil(15000) { vm.state.value.profile != null && !vm.state.value.busy } }
            catch (e: Throwable) { throw AssertionError("Requests=${server.requestCount}; busy=${vm.state.value.busy}; role=${vm.state.value.user?.role}; error=${vm.state.value.error}", e) }
            compose.onNodeWithText("Pantau hasil penjualan tani Anda").assertExists()
            compose.waitForIdle()
            compose.onNodeWithText("Stok", useUnmergedTree = true).performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.waitForIdle()
            try { compose.waitUntil(15000) { compose.runOnIdle { server.requestCount >= 11 && !vm.state.value.busy } } }
            catch (e: Throwable) { throw AssertionError("After navigation: requests=${server.requestCount}; busy=${vm.state.value.busy}; error=${vm.state.value.error}", e) }
            compose.onNodeWithText("Stok & Produk").assertExists()
            compose.onNodeWithText("Toko API · 0 produk").assertExists()
        } finally { server.shutdown(); kotlinx.coroutines.Dispatchers.resetMain() }
    }
    @Test fun stockFiltersAndSearchUseServerRows() {
        host { FarmerStockScreen(FarmerDesignFixtures.state(), {}, {}, {}) }
        compose.onNodeWithText("Draft (1)").performClick()
        compose.onNodeWithText("Bawang Merah").assertExists(); compose.onNodeWithText("Sawi Putih").assertDoesNotExist()
        compose.onNodeWithText("Semua (4)").performClick()
        compose.onNodeWithTag("farmer-search").performTextInput("tomat")
        compose.onNodeWithText("Tomat Sayur").assertExists(); compose.onNodeWithText("Bawang Merah").assertDoesNotExist()
    }
    @Test fun rejectingPaymentRequiresReasonAndDoesNotApproveImplicitly() {
        var accepted: Boolean? = null; var submittedReason = ""
        host { FarmerPaymentScreen(FarmerDesignFixtures.state(), FarmerDesignFixtures.order("verifikasi_pembayaran"), {}, {}, { _, yes, why -> accepted = yes; submittedReason = why }, {}) }
        compose.onNodeWithText("Tolak bukti dengan alasan").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("farmer-input-Alasan penolakan (wajib)").performScrollTo().performTextInput("Nominal belum sesuai")
        compose.onNodeWithText("Tolak bukti dengan alasan").performScrollTo().performClick()
        assertEquals(false, accepted); assertEquals("Nominal belum sesuai", submittedReason)
    }
    @Test fun scheduleCannotBypassPaidOrderRule() {
        var sent = false
        host { FarmerScheduleScreen(FarmerDesignFixtures.state(), FarmerDesignFixtures.order("diproses"), {}) { _, _, _ -> sent = true } }
        compose.onNodeWithText("Kirim usulan ke pembeli").performScrollTo().assertIsNotEnabled()
        assertFalse(sent)
    }
    @Test fun emptyCatalogDoesNotFabricateProductsOrCounts() {
        host { FarmerStockScreen(FarmerDesignFixtures.state().copy(products = emptyList()), {}, {}, {}) }
        compose.onNodeWithText("Semua (0)").assertExists()
        compose.onNodeWithText("Belum ada produk").assertExists(); compose.onNodeWithText("Sawi Putih").assertDoesNotExist()
    }
    @Test fun productDraftCanBeSavedWithoutPublishingAndStockReservationIsVisible() {
        var status = ""
        val s = FarmerDesignFixtures.state()
        host { FarmerProductScreen(s.products.last(), s, {}) { _, body, _, _, _ -> status = body.text("publicationStatus") } }
        compose.onNodeWithText("Simpan sebagai draft").performScrollTo().performClick()
        assertEquals("draft", status)
    }
}
