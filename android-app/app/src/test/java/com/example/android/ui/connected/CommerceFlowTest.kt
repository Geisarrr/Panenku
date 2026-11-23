package id.panenku.app.ui.connected

import id.panenku.app.data.remote.*
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommerceFlowTest {
    private lateinit var server: MockWebServer
    private lateinit var vm: ConnectedViewModel
    @Before fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        server = MockWebServer(); server.start()
        vm = ConnectedViewModel(RuntimeEnvironment.getApplication(), PanenkuApi(server.url("/api/v1/").toString()))
        respond("""{"accessToken":"access","refreshToken":"refresh","user":{"id":"buyer-flow","email":"buyer@example.test","role":"pembeli","status":"active"}}""")
        vm.login("buyer@example.test", "password"); idle(); server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
    }
    @After fun close() { server.shutdown(); Dispatchers.resetMain() }
    private fun respond(body: String) = server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
        .setBody("""{"success":true,"data":$body}"""))
    private fun idle() = runBlocking { withTimeout(10000) { while (vm.state.value.busy) delay(10) } }
    private val quote = """{"canCheckout":true,"items":[{"productId":"sawi","quantityKg":0.25,"pricePerKg":25000}],"deliveryFee":10000,"totalPayment":16250,"quoteToken":"server-quote","expectedFulfillmentToken":"server-schedule"}"""
    private fun preview() {
        respond(quote); vm.preview("delivery", "kepanjen", "Alamat", "2026-12-01", "08:00-10:00"); idle(); server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
    }
    @Test fun cartLifecyclePreservesDecimalAndUsesServerAvailability() {
        respond("""{"items":[{"id":"sawi","availableForPurchase":true}],"totalElements":1}""")
        vm.catalog(); idle(); assertEquals("/api/v1/buyer/products?page=0&size=20", server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!.path)
        respond("{}"); vm.add("sawi", "0,25"); idle()
        assertTrue(server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!.body.readUtf8().contains("\"quantityKg\":0.25"))
        respond("""{"items":[{"id":"cart-row","productId":"sawi","quantityKg":0.25}],"hasUnavailableItems":false}""")
        respond("""{"serviceArea":["kepanjen","dau"],"shippingEnabled":true}""")
        vm.cart(); idle(); server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!; server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals(listOf("kepanjen", "dau"), vm.state.value.deliveryAreas)
        respond("{}"); respond("""{"items":[{"id":"cart-row","quantityKg":0.5}],"hasUnavailableItems":true}""")
        vm.quantity("cart-row", "0,5"); idle()
        assertTrue(server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!.body.readUtf8().contains("\"quantityKg\":0.5")); server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        assertTrue(vm.state.value.cart.flag("hasUnavailableItems"))
        respond("{}"); respond("{\"items\":[]}"); vm.remove("cart-row"); idle()
        assertEquals("DELETE", server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!.method); server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        assertTrue(vm.state.value.cart.list("items").isEmpty())
    }
    @Test fun checkoutSendsReviewedServerTokensAndClearsChangedQuote() {
        preview()
        server.enqueue(MockResponse().setResponseCode(409).setBody("{\"message\":\"Harga berubah.\"}"))
        vm.checkout { fail("A conflict cannot finish checkout") }; idle()
        val body = JsonParser.parseString(server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!.body.readUtf8()).asJsonObject
        assertEquals("server-quote", body.text("expectedCheckoutToken"))
        assertEquals("server-schedule", body.text("expectedFulfillmentToken"))
        assertEquals(25000L, body.list("items").single().long("expectedPricePerKg"))
        assertNull(vm.state.value.quote); assertNull(vm.state.value.checkoutBody)
        assertTrue(vm.state.value.error!!.contains("periksa total kembali"))
    }
    @Test fun transientServerFailureKeepsSameCheckoutKeyForRetry() {
        preview()
        server.enqueue(MockResponse().setResponseCode(503))
        vm.checkout { fail("503 cannot finish checkout") }; idle()
        val first = server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        assertNotNull(vm.state.value.quote)
        respond("{\"orderId\":\"order-1\"}")
        var completed = false
        vm.checkout { completed = true }; idle()
        val retry = server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals(first.getHeader("Idempotency-Key"), retry.getHeader("Idempotency-Key"))
        assertEquals(first.body.readUtf8(), retry.body.readUtf8())
        assertTrue(completed); assertNull(vm.state.value.quote)
    }
    @Test fun serverFailureIsVisibleAndCartCanBeRetried() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{\"message\":\"Koneksi terputus\"}"))
        vm.cart(); idle(); server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        assertFalse(vm.state.value.busy); assertTrue(vm.state.value.error!!.contains("Koneksi"))
        respond("{\"items\":[]}"); vm.cart(); idle(); server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        assertNull(vm.state.value.error); assertTrue(vm.state.value.cart.list("items").isEmpty())
    }
    @Test fun completedOrderRestoresSubmittedReviewAfterReopening() {
        respond("{\"orderId\":\"order-1\",\"orderStatus\":\"selesai\"}")
        respond("{\"rating\":5,\"comment\":\"Segar\"}")
        vm.order("order-1"); idle(); server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("/api/v1/buyer/orders/order-1/review", server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!.path)
        assertTrue(vm.state.value.reviewLoaded); assertEquals(5L, vm.state.value.review!!.long("rating"))
    }
    @Test fun farmerProductUploadUsesChosenBytesAndReturnedPhotoAccessUrl() {
        respond("""{"accessToken":"farmer-access","refreshToken":"refresh","user":{"id":"farmer-flow","email":"farmer@example.test","role":"petani","status":"active"}}""")
        vm.login("farmer@example.test", "password"); idle(); server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        respond("{\"id\":\"sawi\",\"publicationStatus\":\"draft\"}")
        var saved = false
        vm.saveProduct(null, json("publicationStatus" to "draft", "name" to "Sawi")) { saved = true }; idle()
        val create = server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("POST", create.method); assertEquals("/api/v1/petani/products", create.path); assertTrue(saved)
        respond("""{"items":[{"id":"sawi","publicationStatus":"draft"}],"totalElements":1}""")
        vm.catalog(); idle(); server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        val app = RuntimeEnvironment.getApplication()
        val photo = java.io.File(app.cacheDir, "sawi.png")
        // Same supplied demo image used by the backend local-storage integration test.
        val source = listOf("../backend", "../../backend").map { java.io.File(it, "demo-assets/foto-dummy/produk-sawi-segar.png") }.first { it.isFile }
        val bytes = source.readBytes()
        photo.writeBytes(bytes)
        val provider = object : android.content.ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: android.net.Uri) = "image/png"
            override fun openFile(uri: android.net.Uri, mode: String) = android.os.ParcelFileDescriptor.open(photo, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            override fun query(uri: android.net.Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): android.database.Cursor? = null
            override fun insert(uri: android.net.Uri, values: android.content.ContentValues?): android.net.Uri? = null
            override fun delete(uri: android.net.Uri, selection: String?, args: Array<out String>?) = 0
            override fun update(uri: android.net.Uri, values: android.content.ContentValues?, selection: String?, args: Array<out String>?) = 0
        }
        provider.attachInfo(app, android.content.pm.ProviderInfo().apply { authority = "panenku-test-photo"; exported = false })
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("panenku-test-photo", provider)
        val url = "http://backend.example/api/v1/product-photos/local?signature=from-server"
        respond("{}"); respond("""{"id":"sawi","photoAccessUrl":"$url","publicationStatus":"draft"}""")
        vm.productPhoto("sawi", "content://panenku-test-photo/sawi.png"); idle()
        assertNull(vm.state.value.error, vm.state.value.error)
        val upload = server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("/api/v1/petani/products/sawi/photo", upload.path)
        val multipart = upload.body.readByteArray()
        assertTrue(String(multipart, Charsets.ISO_8859_1).contains("name=\"file\""))
        assertTrue(String(multipart, Charsets.ISO_8859_1).contains(String(bytes, Charsets.ISO_8859_1)))
        server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        assertNull(vm.state.value.error)
        assertEquals(url, vm.state.value.products.single().text("photoAccessUrl"))
        respond("{\"id\":\"sawi\",\"publicationStatus\":\"aktif\"}")
        vm.saveProduct("sawi", json("publicationStatus" to "aktif", "name" to "Sawi Segar")) {}; idle()
        val update = server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("PUT", update.method); assertEquals("/api/v1/petani/products/sawi", update.path)
    }

    @Test fun scheduleOptionsAreTakenExactlyFromBackend() {
        respond("""{"dates":[{"date":"2026-12-01","timeSlots":["13:00-15:00"]}],"serviceArea":["dau"],"timeZone":"Asia/Jakarta","selectionToken":"schedule"}""")
        vm.schedules("delivery", "dau"); idle()
        val request = server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("/api/v1/buyer/fulfillment-options", request.path)
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("delivery", body.text("fulfillmentMethod")); assertEquals("dau", body.text("deliveryDistrict"))
        val day = vm.state.value.fulfillment!!.list("dates").single()
        assertEquals("2026-12-01", day.text("date"))
        assertEquals(listOf("13:00-15:00"), day.getAsJsonArray("timeSlots").map { it.asString })
    }

}
