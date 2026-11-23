package id.panenku.app.ui.connected.farmer

import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.ConnectedViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FarmerApiFlowTest {
    private lateinit var server: MockWebServer
    private lateinit var vm: ConnectedViewModel
    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer(); server.start()
        vm = ConnectedViewModel(RuntimeEnvironment.getApplication(), PanenkuApi(server.url("/api/v1/").toString()))
        enqueue("""{"accessToken":"access","refreshToken":"refresh","user":{"id":"farmer","email":"darto@example.test","role":"petani","status":"active"}}""")
        vm.login("darto@example.test", "password"); idle(); assertEquals("/api/v1/auth/sessions", request().path)
    }
    @After fun close() { vm.viewModelScope.cancel(); server.shutdown(); Dispatchers.resetMain() }
    private fun enqueue(data: String) { server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"success":true,"data":$data,"message":"Berhasil"}""")) }
    private suspend fun idle() { withTimeout(10000) { vm.state.first { !it.busy } } }
    private fun request() = server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS) ?: error("Request tidak diterima")
    private fun body(status: String = "draft") = json("commodityId" to "Bawang", "name" to "Bawang Merah", "pricePerKg" to 35000,
        "stockPhysical" to 12, "readyDate" to "2026-06-23", "publicationStatus" to status, "description" to "Segar")
    @Test fun workspaceLoadsAllPagesAndRealProofMetadata() = runBlocking {
        val page = (0..19).map { json("id" to "p$it") }
        enqueue(json("items" to page, "totalElements" to 21).toString())
        enqueue(json("items" to listOf(json("id" to "p20")), "totalElements" to 21).toString())
        enqueue(json("items" to listOf(FarmerDesignFixtures.order("verifikasi_pembayaran")), "totalElements" to 1).toString())
        enqueue("{\"storeName\":\"Toko server\"}"); enqueue("[]"); enqueue("[]")
        enqueue("[${FarmerDesignFixtures.proof()}]")
        vm.farmerWorkspace(); idle()
        assertNull(vm.state.value.error); assertEquals(21, vm.state.value.products.size)
        assertEquals("Toko server", vm.state.value.profile!!.text("storeName"))
        assertEquals(1, vm.state.value.farmerOrderProofs["PES-2026-004"]!!.size)
        val paths = (1..7).map { request().also { assertEquals("Bearer access", it.getHeader("Authorization")) }.path }
        assertEquals(listOf("/api/v1/petani/products?page=0&size=20", "/api/v1/petani/products?page=1&size=20", "/api/v1/petani/orders?page=0&size=20", "/api/v1/petani/profile", "/api/v1/petani/payment-methods", "/api/v1/buyer/commodities", "/api/v1/petani/orders/PES-2026-004/payment-proofs"), paths)
    }
    @Test fun orderLoadsProofAndDestinationTogether() = runBlocking {
        enqueue(FarmerDesignFixtures.order("verifikasi_pembayaran").toString()); enqueue("[${FarmerDesignFixtures.proof()}]"); enqueue("{\"bankName\":\"BRI\"}")
        vm.farmerOrder("PES-2026-004"); idle()
        assertEquals("/api/v1/petani/orders/PES-2026-004", request().path)
        assertEquals("/api/v1/petani/orders/PES-2026-004/payment-proofs", request().path)
        assertEquals("/api/v1/petani/orders/PES-2026-004/payment-destination", request().path)
        assertEquals(1, vm.state.value.proofs.size); assertEquals("BRI", vm.state.value.destination!!.text("bankName"))
    }
    @Test fun newProductIsDraftThenUploadedThenPublishedWithAssignedId() = runBlocking {
        val uri = photo()
        enqueue("{\"id\":\"new-product\",\"publicationStatus\":\"draft\"}"); enqueue("{}"); enqueue("{\"id\":\"new-product\",\"publicationStatus\":\"aktif\"}")
        var assigned: String? = null; var done = false
        vm.saveFarmerProduct(null, body("aktif"), uri, { assigned = it }, { done = true }); idle()
        assertNull(vm.state.value.error); assertEquals("new-product", assigned); assertTrue(done)
        val create = request(); assertEquals("POST", create.method); assertEquals("/api/v1/petani/products", create.path)
        assertEquals("draft", com.google.gson.JsonParser.parseString(create.body.readUtf8()).asJsonObject.text("publicationStatus"))
        val upload = request(); assertEquals("/api/v1/petani/products/new-product/photo", upload.path)
        assertTrue(upload.body.readUtf8().contains("name=\"file\"")); assertNotNull(upload.getHeader("Idempotency-Key"))
        val publish = request(); assertEquals("PUT", publish.method); assertEquals("/api/v1/petani/products/new-product", publish.path)
        assertEquals("aktif", com.google.gson.JsonParser.parseString(publish.body.readUtf8()).asJsonObject.text("publicationStatus"))
    }
    @Test fun uploadFailureRetainsDraftIdAndRetryDoesNotCreateAnotherProduct() = runBlocking {
        val uri = photo(); var assigned: String? = null; var done = false
        enqueue("{\"id\":\"draft-saved\",\"publicationStatus\":\"draft\"}")
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Content-Type", "application/json").setBody("{\"success\":false,\"message\":\"Foto belum tersimpan\"}"))
        vm.saveFarmerProduct(null, body("aktif"), uri, { assigned = it }, { done = true }); idle()
        assertEquals("draft-saved", assigned); assertFalse(done); assertNotNull(vm.state.value.error)
        assertEquals("POST", request().method); val failed = request()
        enqueue("{}"); enqueue("{\"id\":\"draft-saved\",\"publicationStatus\":\"aktif\"}")
        vm.saveFarmerProduct(assigned, body("aktif"), uri, { fail("Must reuse the existing draft") }, { done = true }); idle()
        assertTrue(done); assertNull(vm.state.value.error)
        val retry = request(); assertEquals("/api/v1/petani/products/draft-saved/photo", retry.path)
        assertEquals(failed.getHeader("Idempotency-Key"), retry.getHeader("Idempotency-Key"))
        assertEquals("PUT", request().method)
    }
    @Test fun identitySavePreservesServerDeliveryConfiguration() = runBlocking {
        val profile = FarmerSettingsFixtures.profile()
        enqueue(profile.toString()); enqueue("""{"fullName":"Pemilik Baru","contactPhone":"08123456789"}""")
        enqueue(profile.deepCopy().apply { addProperty("ownerName","Pemilik Baru"); addProperty("storeName","Toko Baru") }.toString())
        var done=false
        vm.saveFarmerIdentity("Pemilik Baru","08123456789","Toko Baru") {done=true}; idle()
        assertTrue(done);assertNull(vm.state.value.error)
        assertEquals("/api/v1/petani/profile",request().path)
        val account=request();assertEquals("/api/v1/auth/profile",account.path)
        assertEquals("Pemilik Baru",com.google.gson.JsonParser.parseString(account.body.readUtf8()).asJsonObject.text("fullName"))
        val store=request();assertEquals("/api/v1/petani/profile",store.path)
        val sent=com.google.gson.JsonParser.parseString(store.body.readUtf8()).asJsonObject
        assertEquals("Toko Baru",sent.text("storeName"));assertEquals(profile.get("timeSlots"),sent.get("timeSlots"));assertEquals(profile.get("serviceArea"),sent.get("serviceArea"));assertEquals(2500L,sent.long("deliveryFee"))
    }
    @Test fun partialIdentitySaveDoesNotPretendBothUpdatesSucceeded() = runBlocking {
        enqueue(FarmerSettingsFixtures.profile().toString());enqueue("""{"fullName":"Nama Tersimpan","contactPhone":"08123456789"}""")
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"success":false,"message":"Toko belum tersimpan"}"""))
        var done=false
        vm.saveFarmerIdentity("Nama Tersimpan","08123456789","Toko Baru") {done=true};idle()
        assertFalse(done);assertNotNull(vm.state.value.error);assertEquals("Nama Tersimpan",vm.state.value.profile!!.text("ownerName"));assertEquals("Kebun Darto",vm.state.value.profile!!.text("storeName"))
        repeat(3){request()}
    }
    @Test fun refundReviewLoadsRealOrderAndTransferWaitsForBuyer() = runBlocking {
        enqueue(FarmerSettingsFixtures.refund().toString());enqueue(FarmerDesignFixtures.order("selesai","PES-2026-002").toString())
        vm.farmerRefundDetail("RF-002");idle()
        assertEquals("/api/v1/petani/refunds/RF-002",request().path);assertEquals("/api/v1/petani/orders/PES-2026-002",request().path)
        enqueue(FarmerSettingsFixtures.refund("disetujui").toString());var done=false
        vm.farmerRefundDecision("RF-002",true,"") {done=true};idle();assertTrue(done)
        val decision=request();assertEquals("/api/v1/petani/refunds/RF-002/respond",decision.path);assertEquals("PUT",decision.method)
        assertEquals("terima",com.google.gson.JsonParser.parseString(decision.body.readUtf8()).asJsonObject.text("decision"))
        enqueue(FarmerSettingsFixtures.refund("menunggu_konfirmasi_dana").toString())
        vm.farmerRefundTransfer("RF-002",photo());idle()
        val transfer=request();assertEquals("/api/v1/petani/refunds/RF-002/transfer-proof",transfer.path);assertNotNull(transfer.getHeader("Idempotency-Key"))
        assertEquals("menunggu_konfirmasi_dana",vm.state.value.farmerRefund!!.text("status"));assertEquals(5,server.requestCount)
    }
    @Test fun qrisImageLoadsIntoSettingsWithoutOpeningProofDialog() = runBlocking {
        val bytes=javaClass.classLoader!!.getResourceAsStream("farmer-design/cabbage.jpg")!!.use{it.readBytes()}
        enqueue("{}")
        server.enqueue(MockResponse().setHeader("Content-Type","image/jpeg").setBody(okio.Buffer().write(bytes)))
        vm.farmerQris("qris-id");idle()
        assertEquals("/api/v1/auth/me",request().path)
        assertEquals("/api/v1/petani/payment-methods/qris-id/qris",request().path)
        assertEquals("qris-id",vm.state.value.farmerQrisId);assertArrayEquals(bytes,vm.state.value.farmerQrisImage);assertNull(vm.state.value.image)
    }
    @Test fun verificationResubmissionUsesDummyMetadataAndRefreshesStatus() = runBlocking {
        enqueue(FarmerSettingsFixtures.verification().toString());vm.farmerVerification();idle();assertEquals("/api/v1/petani/ktp/status",request().path)
        enqueue(FarmerSettingsFixtures.verification("menunggu").toString());enqueue(FarmerSettingsFixtures.profile().apply{addProperty("ktpStatus","menunggu")}.toString())
        vm.farmerResubmitKtp(photo());idle();assertNull(vm.state.value.error)
        val upload=request();assertEquals("/api/v1/petani/ktp/application",upload.path)
        val multipart=upload.body.readUtf8();assertTrue(multipart.contains("DUMMY-0000"));assertTrue(multipart.contains("dummyName"));assertTrue(multipart.contains("name=\"image\""))
        assertEquals("/api/v1/petani/profile",request().path);assertEquals("menunggu",vm.state.value.farmerVerification!!.text("status"))
    }
    @Test fun disabledVerificationFeatureReportsServerError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"success":false,"message":"Fitur KTP dummy tidak diaktifkan."}"""))
        vm.farmerVerification();idle();request()
        assertEquals("Fitur KTP dummy tidak diaktifkan.",vm.state.value.error);assertNull(vm.state.value.farmerVerification)
    }
    @Test fun bankUpdateUsesExistingMethodAndFullAccount() = runBlocking {
        enqueue("{}");enqueue("[]");var done=false
        vm.saveBank("bank-id","BRI","0011223344","Darto") {done=true};idle();assertTrue(done)
        val update=request();assertEquals("PUT",update.method);assertEquals("/api/v1/petani/payment-methods/bank-id/bank",update.path)
        assertEquals("0011223344",com.google.gson.JsonParser.parseString(update.body.readUtf8()).asJsonObject.text("accountNumber"))
        assertEquals("/api/v1/petani/payment-methods",request().path)
    }
    private fun photo(): String {
        val app = RuntimeEnvironment.getApplication()
        val file = File(app.cacheDir, "farmer-test-photo.jpg")
        javaClass.classLoader!!.getResourceAsStream("farmer-design/cabbage.jpg")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        val provider = TestPhotoProvider(file)
        provider.attachInfo(app, ProviderInfo().apply { authority = "farmer.test" })
        ShadowContentResolver.registerProviderInternal("farmer.test", provider)
        return "content://farmer.test/photo"
    }
    private class TestPhotoProvider(private val file: File) : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri) = "image/jpeg"
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
