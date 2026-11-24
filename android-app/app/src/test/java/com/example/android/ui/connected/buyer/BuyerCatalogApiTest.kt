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
class BuyerCatalogApiTest {
    private lateinit var server:MockWebServer;private lateinit var vm:ConnectedViewModel
    @Before fun setup(){Dispatchers.setMain(Dispatchers.Unconfined);server=MockWebServer();server.start();vm=ConnectedViewModel(RuntimeEnvironment.getApplication(),PanenkuApi(server.url("/api/v1/").toString()));ok("""{"accessToken":"access","refreshToken":"refresh","user":{"id":"buyer-catalog-test","email":"buyer@example.test","role":"pembeli","status":"active"}}""");vm.login("buyer@example.test","password");idle();req()}
    @After fun close(){server.shutdown();Dispatchers.resetMain()}
    private fun ok(data:String)=server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"success":true,"data":$data}"""))
    private fun req()=server.takeRequest(5,TimeUnit.SECONDS)!!
    private fun idle()=runBlocking{withTimeout(10000){while(vm.state.value.busy)delay(10)}}
    private val cart="""{"farmerId":"farmer","items":[{"id":"a","productId":"p1","quantityKg":2,"available":true,"stockAvailable":10},{"id":"b","productId":"p2","quantityKg":3,"available":true,"stockAvailable":10}]}"""
    private fun loadCart(){ok(cart);ok("{\"shippingEnabled\":true,\"serviceArea\":[\"Lowokwaru\"]}");vm.buyerCart();idle();req();req()}
    @Test fun searchEncodesUserTextFiltersSortAndPaginatesWithoutDuplicates(){
        ok("""{"items":[{"id":"p"}],"totalElements":21}""");ok("[]")
        val criteria=BuyerSearch("sawi & tomat","commodity","price_desc")
        vm.buyerSearch(criteria);idle();val first=req();req();assertEquals("sawi & tomat",first.requestUrl!!.queryParameter("search"));assertEquals("commodity",first.requestUrl!!.queryParameter("commodityId"));assertEquals("price_desc",first.requestUrl!!.queryParameter("sort"));assertTrue(vm.state.value.buyerSearchMore)
        ok("""{"items":[{"id":"p"},{"id":"q"}],"totalElements":21}""");ok("[]");vm.buyerSearch(criteria,1);idle();assertEquals("1",req().requestUrl!!.queryParameter("page"));req();assertEquals(listOf("p","q"),vm.state.value.buyerResults.map{it.text("id")});assertFalse(vm.state.value.buyerSearchMore)
    }
    @Test fun storeSearchGroupsOnlyMatchingFarmersAcrossAllPages(){
        ok("""{"items":[{"id":"a","farmerId":"f1","storeName":"Kebun Sari"},{"id":"b","farmerId":"f1","storeName":"Kebun Sari"},{"id":"c","farmerId":"f2","storeName":"Toko Lain"}],"totalElements":101}""")
        ok("""{"items":[{"id":"d","farmerId":"f3","storeName":"Sari Baru"}],"totalElements":101}""");ok("[]")
        vm.buyerSearch(BuyerSearch("sari",stores=true));idle();repeat(3){req()};assertEquals(setOf("f1","f3"),vm.state.value.buyerResults.map{it.text("farmerId")}.toSet())
    }
    @Test fun cartSelectionSurvivesRefreshAndPreviewSendsOnlySelectedRows(){
        loadCart();vm.buyerSelectCart("b",false);assertEquals(setOf("a"),vm.state.value.buyerSelected)
        loadCart();assertEquals(setOf("a"),vm.state.value.buyerSelected)
        ok("""{"items":[],"canCheckout":true}""");vm.preview("pickup","","","2026-12-01","09:00-12:00");idle()
        val body=JsonParser.parseString(req().body.readUtf8()).asJsonObject;assertEquals(listOf("p1"),body.list("items").map{it.text("productId")})
    }
    @Test fun absoluteQuantityRejectsStockOverflowBeforeWriting(){loadCart();val count=server.requestCount;vm.buyerQuantity("a","11");idle();assertEquals(count,server.requestCount);assertNotNull(vm.state.value.error)
        ok("{}");ok(cart);ok("{}");vm.buyerQuantity("a","1.25");idle();val request=req();assertEquals("PUT",request.method);assertEquals("1.25",JsonParser.parseString(request.body.readUtf8()).asJsonObject.text("quantityKg"));req();req()
    }
    @Test fun deletingSelectedLeavesOtherCartRowsUntouched(){loadCart();vm.buyerSelectCart("b",false);ok("{}");ok("{\"items\":[]}");vm.buyerDeleteSelected();idle();val r=req();assertEquals("DELETE",r.method);assertEquals("/api/v1/buyer/cart/items/a",r.path);assertEquals("GET",req().method)}
    private fun quote(){ok("""{"canCheckout":true,"quoteToken":"quote","expectedFulfillmentToken":"slots","items":[{"productId":"p1","quantityKg":2,"pricePerKg":5000}],"deliveryFee":0}""");vm.preview("pickup","","","2026-12-01","09:00-12:00");idle();req()}
    @Test fun successfulCheckoutRemovesOnlyOrderedRowsAndRetainsUnselectedItems(){
        loadCart();vm.buyerSelectCart("b",false);quote();ok("{\"orderId\":\"order\"}");ok(cart);ok("{}");ok("""{"items":[{"id":"b","productId":"p2","quantityKg":3}]}""")
        var done=false;vm.buyerCheckout{done=true};idle();assertEquals("POST",req().method);req();assertEquals("/api/v1/buyer/cart/items/a",req().path);req();assertTrue(done);assertEquals("order",vm.state.value.detail!!.text("orderId"));assertEquals("b",vm.state.value.cart.list("items").single().text("id"))
    }
    @Test fun failedCartCleanupCannotTurnACreatedOrderIntoAFailedCheckoutOrRepeatPost(){
        loadCart();quote();ok("{\"orderId\":\"created\"}");server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        var done=false;vm.buyerCheckout{done=true};idle();assertEquals("POST",req().method);assertEquals("GET",req().method);assertTrue(done);assertEquals("created",vm.state.value.detail!!.text("orderId"));assertNull(vm.state.value.error);assertNotNull(vm.state.value.buyerCheckoutWarning)
        val count=server.requestCount;vm.buyerCheckout{};idle();assertEquals(count,server.requestCount)
    }
    @Test fun notificationReadUsesRealWriteAndRoutesScheduleToExistingOrderScreen(){
        val note="""{"id":"notice","isRead":false,"deepLink":"/orders/order/schedule"}"""
        ok("""{"items":[$note],"unreadCount":1,"totalElements":1}""");vm.buyerNotifications();idle();req()
        ok("{}");ok("{\"items\":[],\"unreadCount\":0,\"totalElements\":0}");ok("{\"orderId\":\"order\",\"pendingScheduleChange\":{\"id\":\"proposal\"}}")
        var target="";vm.buyerOpenNotification(JsonParser.parseString(note).asJsonObject){route,id->target="$route/$id"};idle();assertEquals("PUT",req().method);req();assertEquals("/api/v1/buyer/orders/order",req().path);assertEquals("schedule-decision/order",target);assertEquals(0,vm.state.value.unreadNotificationCount)
    }
    @Test fun notificationMarkAllUsesSupportedEndpoint(){ok("{}");ok("{\"items\":[],\"unreadCount\":0,\"totalElements\":0}");vm.buyerReadAll();idle();val r=req();assertEquals("PUT",r.method);assertEquals("/api/v1/notifications/read-all",r.path);req()}
    @Test fun internalLinksRejectExternalEncodedTraversalAndUnknownDestinations(){
        listOf("https://example.test/orders/a","javascript:alert(1)","/orders/../refunds/a","/orders/%2e%2e","/unknown/a","/refunds/a/schedule").forEach{assertNull(buyerLink(it))}
        assertEquals("orders" to "abc-123",buyerLink("/orders/abc-123/schedule"));assertEquals("refunds" to "r",buyerLink("/refunds/r"))
    }
    @Test fun fractionalKgUsesTheSameRupiahRoundingAsBackend(){assertEquals(13,buyerLineTotal(1250,"0.01"));assertEquals(1563,buyerLineTotal(1250,"1.25"));assertEquals(0,buyerLineTotal(1250,"invalid"))}
    @Test fun insufficientStockItemCanBeReducedAndSelectedForDeletion() {
        val shortage="""{"items":[{"id":"short","productId":"p1","quantityKg":3,"stockAvailable":1,"available":false}],"totalElements":1}"""
        ok(shortage);vm.buyerCart();idle();req()
        vm.buyerSelectCart("short",true)
        assertTrue(vm.state.value.buyerSelected.contains("short"))
        ok("{}");ok("{\"items\":[]}")
        vm.buyerQuantity("short","0.5");idle()
        val sent=req();assertEquals("PUT",sent.method)
        assertTrue(sent.body.readUtf8().contains("0.5"));req();assertNull(vm.state.value.error)
    }

    @Test fun unavailableProductCanBeSelectedAndDeleted() {
        ok("{\"items\":[{\"id\":\"inactive\",\"productId\":\"p1\",\"quantityKg\":1,\"stockAvailable\":0,\"available\":false}]}")
        vm.buyerCart();idle();req();vm.buyerSelectCart("inactive",true)
        ok("{}");ok("{\"items\":[]}");vm.buyerDeleteSelected();idle()
        val deletion=req();assertEquals("DELETE",deletion.method)
        assertEquals("/api/v1/buyer/cart/items/inactive",deletion.path);req()
        assertTrue(vm.state.value.cart.list("items").isEmpty());assertNull(vm.state.value.error)
    }
    @Test fun inactiveProductQuantityStillRequiresBackendApproval() {
        ok("{\"items\":[{\"id\":\"inactive\",\"productId\":\"p1\",\"quantityKg\":3,\"stockAvailable\":10,\"available\":false}]}")
        vm.buyerCart();idle();req()
        server.enqueue(MockResponse().setResponseCode(409).setBody("{\"success\":false,\"message\":\"Produk nonaktif\"}"))
        vm.buyerQuantity("inactive","1");idle()
        assertEquals("PUT",req().method);assertNotNull(vm.state.value.error)
        assertEquals("3",vm.state.value.cart.list("items").single().text("quantityKg"))
    }

}
