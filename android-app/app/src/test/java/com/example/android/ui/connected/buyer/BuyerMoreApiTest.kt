package id.panenku.app.ui.connected.buyer

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
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class BuyerMoreApiTest {
    private lateinit var server:MockWebServer;private lateinit var vm:ConnectedViewModel
    @Before fun setup(){Dispatchers.setMain(Dispatchers.Unconfined);server=MockWebServer();server.start();vm=ConnectedViewModel(RuntimeEnvironment.getApplication(),PanenkuApi(server.url("/api/v1/").toString()));ok("""{"accessToken":"access","refreshToken":"refresh","user":{"id":"buyer-more-test","email":"buyer@example.test","role":"pembeli","status":"active"}}""");vm.login("buyer@example.test","password");idle();req()}
    @After fun close(){server.shutdown();Dispatchers.resetMain()}
    private fun ok(data:String)=server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"success":true,"data":$data}"""))
    private fun failResponse(status:Int)=server.enqueue(MockResponse().setResponseCode(status).setBody("{\"message\":\"Belum tersedia\"}"))
    private fun req()=server.takeRequest(5,TimeUnit.SECONDS)!!
    private fun idle()=runBlocking{withTimeout(10000){while(vm.state.value.busy)delay(10)}}
    private fun completed(){ok("""{"orderId":"order","orderStatus":"selesai"}""");ok("null");vm.buyerCompleted("order",false);idle();req();req()}
    @Test fun reviewPostsOnlySupportedFieldsAndRejectsSecondSubmission(){
        completed();ok("""{"id":"review","orderId":"order","rating":4,"comment":"Aspek: Pengiriman\\nBaik"}""")
        vm.buyerSubmitReview("order",4,setOf("Pengiriman"),"Baik");idle()
        val r=req();val body=JsonParser.parseString(r.body.readUtf8()).asJsonObject
        assertEquals("/api/v1/buyer/orders/order/review",r.path);assertEquals(4,body.long("rating"));assertTrue(body.text("comment").contains("Pengiriman"));assertFalse(body.has("tags"))
        val count=server.requestCount;vm.buyerSubmitReview("order",5,emptySet(),"Ulang");idle();assertEquals(count,server.requestCount);assertNotNull(vm.state.value.error)
    }
    @Test fun missingReviewEndpointDoesNotBecomeAnEmptyReviewReadyToSubmit(){
        ok("""{"orderId":"order","orderStatus":"selesai"}""");failResponse(404);vm.buyerCompleted("order",false);idle();req();req()
        assertTrue(vm.state.value.buyerReviewLoaded);assertFalse(vm.state.value.buyerReviewAvailable)
        val count=server.requestCount;vm.buyerSubmitReview("order",5,emptySet(),"");idle();assertEquals(count,server.requestCount)
    }
    @Test fun conflictReloadsTheExistingReviewInsteadOfClaimingAnotherWasCreated(){
        completed();failResponse(409);ok("""{"id":"saved","rating":5,"comment":"Sudah tersimpan"}""")
        vm.buyerSubmitReview("order",4,emptySet(),"Percobaan kedua");idle();assertEquals("POST",req().method);assertEquals("GET",req().method)
        assertEquals("saved",vm.state.value.buyerReview!!.text("id"));assertNull(vm.state.value.error)
    }
    @Test fun profileUpdateKeepsTheCurrentServerAddressAndEmailIdentity(){
        ok("""{"fullName":"Lama","contactPhone":"08","deliveryAddress":"Alamat tetap"}""");ok("""{"fullName":"Baru","contactPhone":"08123","deliveryAddress":"Alamat tetap"}""")
        vm.buyerSaveIdentity("Baru","08123");idle();req();val body=JsonParser.parseString(req().body.readUtf8()).asJsonObject
        assertEquals("Alamat tetap",body.text("deliveryAddress"));assertFalse(body.has("email"));assertEquals("buyer@example.test",vm.state.value.user!!.email)
    }
    @Test fun refundConfirmationNeedsTransferProofAndRefreshesOnlyAfterExplicitAction(){
        ok("""{"id":"refund","status":"menunggu_konfirmasi_dana","hasTransferProof":true}""");vm.buyerRefundDetail("refund");idle();req()
        ok("{}");ok("""{"id":"refund","status":"selesai","hasTransferProof":true}""");vm.buyerConfirmRefund("refund");idle()
        val r=req();assertEquals("POST",r.method);assertEquals("/api/v1/buyer/refunds/refund/confirm",r.path);req();assertEquals("selesai",vm.state.value.buyerRefund!!.text("status"))
        val count=server.requestCount;vm.buyerConfirmRefund("refund");idle();assertEquals(count,server.requestCount)
    }
    @Test fun lostTicketResponseReusesTheSameReasonAndKeyAndReturnsOnlyAServerId(){
        ok("""{"id":"refund","status":"menunggu_konfirmasi_dana","hasTransferProof":true}""");vm.buyerRefundDetail("refund");idle();req()
        failResponse(503);var id:String?=null;vm.buyerCreateTicket("refund","Dana belum diterima"){id=it};idle();val first=req();assertNull(id)
        ok("""{"id":"ticket-real","refundId":"refund","orderId":"order","status":"terbuka"}""");vm.buyerCreateTicket("refund","Alasan berbeda"){id=it};idle();val second=req()
        assertEquals(first.getHeader("Idempotency-Key"),second.getHeader("Idempotency-Key"));assertEquals(first.body.readUtf8(),second.body.readUtf8());assertEquals("ticket-real",id)
    }
    @Test fun ticketSupplementIsLocalAndScopedToItsAccount(){
        ok("""{"id":"ticket","refundId":"refund","orderId":"order","status":"terbuka"}""");ok("""{"id":"refund","status":"dieskalasi"}""");vm.buyerTicket("ticket");idle();req();req()
        val count=server.requestCount;vm.buyerTicketNote("ticket","Catatan","content://example/photo","foto.png");idle()
        assertEquals(count,server.requestCount);assertEquals("Catatan",vm.state.value.buyerTicketDraft.text("note"))
        val local=BuyerHelpStore(RuntimeEnvironment.getApplication());assertEquals("Catatan",local.draft("buyer-more-test","ticket").text("note"));assertEquals("",local.draft("someone-else","ticket").text("note"))
    }
    @Test fun whatsappDraftContainsNoBankAccountOrAttachmentData(){
        val t=json("id" to "ticket","orderId" to "order","refundId" to "refund","reason" to "Dana belum diterima rekening 123456789012 alamat rahasia")
        val text=buyerCsMessage(t);assertTrue(text.contains("ticket"));assertTrue(text.contains("Dana belum diterima"));assertFalse(text.contains("123456789012"));assertFalse(text.contains("rahasia"));assertEquals("62812345678",BUYER_CS_NUMBER)
    }
    @Test fun reorderRetryChecksAbsoluteTargetsAndDoesNotDoubleAnAlreadyAddedQuantity(){
        ok("""{"orderId":"order","farmerId":"farmer","orderStatus":"selesai","items":[{"productId":"p","productNameSnapshot":"Sayur","quantityKg":2}]}""")
        ok("{\"items\":[]}");ok("""{"product":{"id":"p","farmerId":"farmer","availableForPurchase":true,"stockAvailable":10}}""");ok("{\"items\":[]}");failResponse(503)
        var done=false;vm.buyerReorder("order"){done=true};idle();repeat(4){req()};val post=req();assertEquals("POST",post.method);assertFalse(done)
        ok("""{"items":[{"id":"row","productId":"p","farmerId":"farmer","quantityKg":2}]}""");vm.buyerReorder("order"){done=true};idle()
        val check=req();assertEquals("GET",check.method);assertEquals("/api/v1/buyer/cart",check.path);assertTrue(done);assertEquals(7,server.requestCount)
    }
    @Test fun unavailableReorderDoesNotPartiallyMutateTheCartDuringPreflight(){
        ok("""{"farmerId":"farmer","orderStatus":"selesai","items":[{"productId":"a","productNameSnapshot":"A","quantityKg":2},{"productId":"b","productNameSnapshot":"B","quantityKg":2}]}""")
        ok("{\"items\":[]}");ok("""{"product":{"availableForPurchase":true,"stockAvailable":10}}""");ok("""{"product":{"availableForPurchase":false,"stockAvailable":0}}""")
        vm.buyerReorder("order"){fail("Unavailable item cannot finish reorder")};idle();repeat(4){assertEquals("GET",req().method)};assertEquals(5,server.requestCount)
    }
    @Test fun storePaginationDoesNotMixProductsFromAnotherFarmer(){
        ok("""{"product":{"id":"seed","farmerId":"owner","storeName":"Toko"},"farmerRating":{"average":4.9,"reviewCount":48}}""")
        ok("""{"items":[{"id":"other","farmerId":"other-owner"},{"id":"seed","farmerId":"owner"}],"totalElements":101}""");ok("""{"items":[{"id":"second","farmerId":"owner"}],"totalElements":101}""")
        vm.buyerStore("seed");idle();repeat(3){req()};assertEquals(setOf("seed","second"),vm.state.value.buyerStoreProducts.map{it.text("id")}.toSet());assertTrue(vm.state.value.buyerStoreReviews.isEmpty())
    }
    @Test fun escalationsWaitForTheFarmerResponseWindowUnlessRejectedOrFundsAreMissing(){
        val now=java.time.Instant.parse("2026-11-23T05:00:00Z")
        val refund=json("status" to "diajukan","createdAt" to now.minusSeconds(86399).toString())
        assertFalse(canEscalateBuyerRefund(refund,now));assertTrue(canEscalateBuyerRefund(refund,now.plusSeconds(1)))
        refund.addProperty("status","ditolak");assertTrue(canEscalateBuyerRefund(refund,now))
        refund.addProperty("status","menunggu_konfirmasi_dana");assertFalse(canEscalateBuyerRefund(refund,now));refund.addProperty("hasTransferProof",true);assertTrue(canEscalateBuyerRefund(refund,now))
        refund.addProperty("status","selesai");assertFalse(canEscalateBuyerRefund(refund,now))
    }
    @Test fun completedOrderUsesTheImmutablePaymentDestinationSnapshot(){
        ok("""{"orderId":"order","orderStatus":"selesai"}""");ok("null");ok("""{"methodType":"bank_transfer","bankName":"BRI","accountHolderName":"Nama pada pesanan","accountNumber":"123456"}""")
        vm.buyerCompleted("order");idle();req();req();assertEquals("/api/v1/buyer/orders/order/payment-destination",req().path)
        assertEquals("BRI",vm.state.value.destination!!.text("bankName"));assertEquals("Nama pada pesanan",vm.state.value.destination!!.text("accountHolderName"))
    }

}
