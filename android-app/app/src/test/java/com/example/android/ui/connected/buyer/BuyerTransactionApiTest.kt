package id.panenku.app.ui.connected.buyer

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.ConnectedViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class BuyerTransactionApiTest {
    private lateinit var server:MockWebServer
    private lateinit var vm:ConnectedViewModel
    @Before fun setup(){
        Dispatchers.setMain(Dispatchers.Unconfined)
        server=MockWebServer();server.start()
        vm=ConnectedViewModel(RuntimeEnvironment.getApplication(),PanenkuApi(server.url("/api/v1/").toString()))
        respond("""{"accessToken":"access","refreshToken":"refresh","user":{"id":"buyer-test","email":"buyer@example.test","role":"pembeli","status":"active"}}""")
        vm.login("buyer@example.test","password");idle();request()
    }
    @After fun close(){server.shutdown();Dispatchers.resetMain()}
    private fun respond(body:String)=server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"success":true,"data":$body}"""))
    private fun request()=server.takeRequest(5,TimeUnit.SECONDS)!!
    private fun idle()=runBlocking{withTimeout(10000){while(vm.state.value.busy)delay(10)}}
    @Test fun pendingScheduleLoadsWithoutRequestingForbiddenPaymentOptions(){
        respond("""{"orderId":"order","orderStatus":"menunggu_pembayaran","expiresAt":"2099-01-01T00:00:00Z","pendingScheduleChange":{"id":"proposal"}}""")
        respond("[]");respond("{\"items\":[],\"totalElements\":0}")
        vm.buyerTransaction("order");idle()
        assertEquals("/api/v1/buyer/orders/order",request().path)
        assertEquals("/api/v1/buyer/orders/order/payment-proofs",request().path)
        assertEquals("/api/v1/buyer/refunds?page=0&size=100",request().path)
        assertTrue(vm.state.value.buyerTransactionLoaded);assertTrue(vm.state.value.options.isEmpty());assertNull(vm.state.value.destination)
        assertEquals(4,server.requestCount) // Includes login.
    }
    @Test fun missingDestinationIsOptionalButPaymentErrorsAreNotHidden(){
        respond("""{"orderId":"order","orderStatus":"menunggu_pembayaran","expiresAt":"2099-01-01T00:00:00Z"}""")
        respond("[]");respond("[]");respond("{\"items\":[],\"totalElements\":0}")
        server.enqueue(MockResponse().setResponseCode(404).setBody("{\"message\":\"Belum dipilih\"}"))
        vm.buyerTransaction("order");idle()
        repeat(5){request()}
        assertTrue(vm.state.value.buyerTransactionLoaded);assertNull(vm.state.value.error);assertNull(vm.state.value.destination)
    }
    @Test fun selectingAddressPreservesAccountOwnerAndInvalidatesCheckout(){
        val owner="""{"fullName":"Pemilik Akun","contactPhone":"08123456","deliveryAddress":"Alamat lama"}"""
        respond(owner);vm.buyerAddresses();idle();request()
        val row=SavedBuyerAddress(id="new",label="Dapur",recipient="Penerima Berbeda",phone="089999",address="Alamat penerima",district="Lowokwaru")
        respond(owner);respond("""{"fullName":"Pemilik Akun","contactPhone":"08123456","deliveryAddress":"Alamat penerima"}""")
        vm.saveBuyerAddress(row,true);idle();assertEquals("GET",request().method)
        val sent=JsonParser.parseString(request().body.readUtf8()).asJsonObject
        assertEquals("Pemilik Akun",sent.text("fullName"));assertEquals("08123456",sent.text("contactPhone"));assertEquals("Alamat penerima",sent.text("deliveryAddress"))
        assertEquals("new",vm.state.value.buyerAddressId);assertNull(vm.state.value.fulfillment);assertNull(vm.state.value.quote)
    }
    @Test fun previewIncludesNotesAndKeepsTheServerQuoteForCommit(){
        respond("""{"items":[],"canCheckout":true,"quoteToken":"q","expectedFulfillmentToken":"f","deliveryFee":2500}""")
        vm.preview("delivery","Lowokwaru","Alamat","2026-12-01","09:00-12:00","Hubungi saat tiba");idle()
        val sent=JsonParser.parseString(request().body.readUtf8()).asJsonObject
        assertEquals("Hubungi saat tiba",sent.text("buyerNotes"))
        assertEquals("Hubungi saat tiba",vm.state.value.checkoutBody!!.text("buyerNotes"))
        assertEquals("q",vm.state.value.checkoutBody!!.text("expectedCheckoutToken"))
    }
    @Test fun addressStorageIsPartitionedByAccount(){
        val store=BuyerAddressStore(RuntimeEnvironment.getApplication())
        val row=SavedBuyerAddress(id="private",label="Private",recipient="A",phone="08",address="Private address")
        store.write("user-a",listOf(row),row.id);assertEquals(row,store.read("user-a").single())
        assertTrue(store.read("user-b").isEmpty());assertNull(store.selected("user-b"))
    }
    @Test fun proofCanOnlyBeSentBeforeDeadlineWithoutPendingSchedule(){
        val now=Instant.parse("2026-11-23T00:00:00Z")
        val order=json("orderStatus" to "menunggu_pembayaran","paymentStatus" to "bukti_ditolak","expiresAt" to now.plusSeconds(1).toString())
        assertTrue(canUploadBuyerProof(order,now));assertFalse(canUploadBuyerProof(order,now.plusSeconds(2)))
        order.add("pendingScheduleChange",json("id" to "proposal"));assertFalse(canUploadBuyerProof(order,now))
        order.remove("pendingScheduleChange");order.addProperty("orderStatus","verifikasi_pembayaran");assertFalse(canUploadBuyerProof(order,now))
    }
    @Test fun refundRequiresVerifiedPaymentWithinWindowAndNoActiveOrCompletedRefund(){
        val now=Instant.parse("2026-11-23T00:00:00Z")
        val order=json("orderId" to "order","orderStatus" to "selesai","paymentStatus" to "terverifikasi","receivedAt" to now.minusSeconds(86399).toString(),"totalPayment" to 202500)
        assertTrue(canRequestBuyerRefund(order,emptyList(),now));assertFalse(canRequestBuyerRefund(order,emptyList(),now.plusSeconds(2)))
        for(status in listOf("diajukan","disetujui","menunggu_konfirmasi_dana","dieskalasi","selesai")) assertFalse(canRequestBuyerRefund(order,listOf(json("orderId" to "order","status" to status)),now))
        assertTrue(canRequestBuyerRefund(order,listOf(json("orderId" to "other","status" to "diajukan")),now))
        order.addProperty("paymentStatus","belum_dibayar");assertFalse(canRequestBuyerRefund(order,emptyList(),now))
    }
}
