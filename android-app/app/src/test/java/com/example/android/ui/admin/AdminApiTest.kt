package id.panenku.app.ui.admin

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
class AdminApiTest {
    private lateinit var server:MockWebServer;private lateinit var vm:ConnectedViewModel
    @Before fun setup(){Dispatchers.setMain(Dispatchers.Unconfined);server=MockWebServer();server.start();vm=ConnectedViewModel(RuntimeEnvironment.getApplication(),PanenkuApi(server.url("/api/v1/").toString()));login("admin")}
    @After fun close(){server.shutdown();Dispatchers.resetMain()}
    private fun ok(data:String)=server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"success":true,"data":$data}"""))
    private fun req()=server.takeRequest(5,TimeUnit.SECONDS)!!
    private fun idle()=runBlocking{withTimeout(10000){while(vm.state.value.busy)delay(10)}}
    private fun login(role:String){ok("""{"accessToken":"access","refreshToken":"refresh","user":{"id":"admin-user","email":"admin@example.test","role":"$role","status":"active"}}""");vm.login("admin@example.test","password");idle();req()}
    private val application="""{"farmerId":"farmer","applicationId":"app-1","status":"menunggu","submittedAt":"2026-11-23T00:00:00Z","hasDocument":true,"storeName":"Kebun","dummyName":"Pemilik"}"""
    private fun inspectFarmer(){ok(application);vm.adminInspectFarmer("farmer");idle();req()}
    @Test fun dashboardUsesServerTotalsRatherThanCountingThePreviewRows(){
        ok("""{"items":[{"farmerId":"farmer"}],"totalElements":123}""");ok("{\"items\":[],\"totalElements\":8}");ok("{\"items\":[],\"totalElements\":2}");ok("{\"items\":[]}")
        vm.adminLoadHome();idle();repeat(4){assertEquals("GET",req().method)};assertEquals(123L,vm.state.value.adminUi.verificationCount);assertEquals(8L,vm.state.value.adminUi.disputeCount);assertEquals(2L,vm.state.value.adminUi.suspendedCount)
    }
    @Test fun buyerCannotLoadAdministratorSnapshots(){login("pembeli");val count=server.requestCount;vm.adminLoadHome();idle();assertEquals(count,server.requestCount);assertNotNull(vm.state.value.error)}
    @Test fun queuePreservesEncodedSearchAndPagination(){
        ok("""{"items":[{"farmerId":"a"}],"totalElements":51}""");vm.adminLoadQueue("menunggu","kebun & tani");idle();val r=req();assertEquals("kebun & tani",r.requestUrl!!.queryParameter("search"));assertTrue(vm.state.value.adminUi.queueMore)
        ok("""{"items":[{"farmerId":"a"},{"farmerId":"b"}],"totalElements":51}""");vm.adminLoadQueue("menunggu","kebun & tani",1);idle();assertEquals("1",req().requestUrl!!.queryParameter("page"));assertEquals(2,vm.state.value.adminUi.queue.size)
    }
    @Test fun verificationNeedsChecksAndRejectsAChangedApplicationBeforeAnyWrite(){
        inspectFarmer();val count=server.requestCount;vm.adminVerifySelected(true,"",false);idle();assertEquals(count,server.requestCount)
        ok(application.replace("app-1","app-2"));vm.adminVerifySelected(true,"",true);idle();assertEquals("GET",req().method);assertEquals(count+1,server.requestCount);assertEquals("app-2",vm.state.value.adminUi.verification!!.text("applicationId"));assertNotNull(vm.state.value.error)
    }
    @Test fun approvalUsesSupportedEndpointAndDoesNotSuspendOrPublishProducts(){
        inspectFarmer();ok(application);ok("null");ok("{\"items\":[],\"totalElements\":0}")
        vm.adminVerifySelected(true,"",true);idle();req();val r=req();val body=JsonParser.parseString(r.body.readUtf8()).asJsonObject
        assertEquals("PUT",r.method);assertEquals("/api/v1/admin/farmers/farmer/verify",r.path);assertTrue(body.flag("approved"));assertEquals("GET",req().method);assertNull(vm.state.value.error)
    }
    @Test fun acknowledgedVerificationIsNotReportedFailedWhenQueueRefreshFails(){
        inspectFarmer();ok(application);ok("null");server.enqueue(MockResponse().setResponseCode(503).setBody("{}"));vm.adminVerifySelected(false,"Foto terpotong",true);idle();req();req();req()
        assertNull(vm.state.value.error);assertEquals("ditolak",vm.state.value.adminUi.verification!!.text("status"));assertTrue(vm.state.value.message!!.contains("tersimpan"));val count=server.requestCount;vm.adminVerifySelected(false,"Lagi",true);idle();assertEquals(count,server.requestCount)
    }
    @Test fun accountSuspensionWorksForBuyersAndRetainsTheCurrentListFilters(){
        ok("{\"items\":[],\"totalElements\":0}");vm.adminLoadUsers("pembeli","active","Rina");idle();req()
        ok("""{"id":"buyer","role":"pembeli","status":"active"}""");vm.adminInspectUser("buyer");idle();req()
        ok("null");ok("{\"items\":[],\"totalElements\":0}");ok("""{"id":"buyer","role":"pembeli","status":"suspended"}""");vm.adminChangeUser(true,"Laporan ditinjau");idle()
        val put=req();assertEquals("PUT",put.method);assertEquals("/api/v1/admin/users/buyer/suspend",put.path);assertTrue(JsonParser.parseString(put.body.readUtf8()).asJsonObject.flag("suspend"));val list=req();assertEquals("pembeli",list.requestUrl!!.queryParameter("role"));assertEquals("Rina",list.requestUrl!!.queryParameter("search"));req();assertEquals("suspended",vm.state.value.adminUi.user!!.text("status"))
    }
    @Test fun administratorAccountsCannotBeSuspendedAndReasonIsMandatory(){
        ok("""{"id":"other-admin","role":"admin","status":"active"}""");vm.adminInspectUser("other-admin");idle();req();val count=server.requestCount;vm.adminChangeUser(true,"Alasan");idle();assertEquals(count,server.requestCount)
        ok("""{"id":"farmer","role":"petani","status":"active"}""");vm.adminInspectUser("farmer");idle();req();vm.adminChangeUser(true,"");idle();assertEquals(count+1,server.requestCount)
    }
    @Test fun commodityWritesOnlySupportedFieldsAndKeepsSuccessWhenRefreshFails(){
        ok("""{"id":"commodity","name":"Sayur","isActive":true}""");server.enqueue(MockResponse().setResponseCode(503).setBody("{}"));var saved=false
        vm.adminSaveCommodity(null," Sayur ",true){saved=true};idle();val r=req();req();val body=JsonParser.parseString(r.body.readUtf8()).asJsonObject
        assertEquals(setOf("name","isActive"),body.keySet());assertEquals("Sayur",body.text("name"));assertTrue(saved);assertNull(vm.state.value.error);assertEquals("commodity",vm.state.value.adminUi.commodities.single().text("id"))
    }
    @Test fun settingsPreserveOptimisticLockValuesIncludingExplicitNullWhatsapp(){
        ok("""{"transactionLimit":10000000,"csWhatsapp":null,"settingsVersion":1}""");vm.adminSettings();idle();req()
        ok("""{"transactionLimit":12000000,"csWhatsapp":"+62812345678","settingsVersion":2}""");vm.saveAdminSettings(12000000,"+62812345678","Batas ditinjau");idle()
        val r=req();val body=JsonParser.parseString(r.body.readUtf8()).asJsonObject;assertEquals("PUT",r.method);assertEquals(10000000,body.long("expectedTransactionLimit"));assertTrue(body.has("expectedCsWhatsapp"));assertTrue(body.get("expectedCsWhatsapp").isJsonNull);assertEquals("Batas ditinjau",body.text("reason"))
    }
    private fun inspectDispute(){ok("""{"id":"ticket","refundId":"refund","orderId":"order","status":"terbuka"}""");ok("""{"id":"refund","status":"dieskalasi","hasTransferProof":true}""");vm.adminInspectDispute("ticket");idle();req();req()}
    @Test fun disputeDecisionDoesNotConfirmBuyerFundsOrAutomaticallySuspendFarmer(){
        inspectDispute();ok("""{"id":"ticket","refundId":"refund","status":"selesai","decision":"tutup_sengketa"}""");ok("{\"items\":[],\"totalElements\":0}");ok("""{"id":"refund","status":"menunggu_konfirmasi_dana"}""")
        vm.adminDecideSelected(false,"Bukti dan penjelasan ditinjau");idle();val r=req();assertEquals("/api/v1/admin/disputes/ticket/decide",r.path);assertEquals("tutup_sengketa",JsonParser.parseString(r.body.readUtf8()).asJsonObject.text("decision"));repeat(2){assertEquals("GET",req().method)};assertEquals("menunggu_konfirmasi_dana",vm.state.value.adminUi.refund!!.text("status"));assertEquals(1,vm.state.value.adminUi.decidedDisputes.size)
        val count=server.requestCount;vm.adminDecideSelected(false,"Ulang");idle();assertEquals(count,server.requestCount)
    }
    @Test fun returningToDisputesRetainsSelectionAndRefreshesRefundFacts(){
        inspectDispute();ok("{\"items\":[],\"totalElements\":0}");ok("{\"id\":\"ticket\",\"refundId\":\"refund\",\"status\":\"terbuka\"}");ok("{\"id\":\"refund\",\"status\":\"dieskalasi\",\"hasTransferProof\":true}")
        vm.adminLoadDisputes();idle();assertEquals("/api/v1/admin/disputes?page=0&size=50",req().path);assertEquals("/api/v1/admin/disputes/ticket",req().path);assertEquals("/api/v1/admin/refunds/refund",req().path);assertEquals("ticket",vm.state.value.adminUi.dispute!!.text("id"));assertTrue(vm.state.value.adminUi.refund!!.flag("hasTransferProof"))
    }
    @Test fun evidenceMetadataUsesAuthenticatedAdminPaymentProofEndpoint(){ok("[]");vm.adminLoadEvidence("order");idle();val r=req();assertEquals("/api/v1/admin/orders/order/payment-proofs",r.path);assertEquals("Bearer access",r.getHeader("Authorization"))}
    @Test fun wrongRoleAdminLoginRevokesTheSessionAndNeverEntersAdminUi(){
        ok("""{"accessToken":"buyer-token","refreshToken":"buyer-refresh","user":{"id":"buyer","email":"buyer@example.test","role":"pembeli","status":"active"}}""");ok("null")
        // Log out the initial admin session locally through a new view model instance for this login path.
        val candidate=ConnectedViewModel(RuntimeEnvironment.getApplication(),PanenkuApi(server.url("/api/v1/").toString()))
        candidate.loginAdmin("buyer@example.test","password");runBlocking{withTimeout(10000){while(candidate.state.value.busy)delay(10)}};assertEquals("/api/v1/auth/sessions",req().path);assertEquals("/api/v1/auth/logout",req().path);assertNull(candidate.state.value.user);assertNotNull(candidate.state.value.error)
    }
    @Test fun csPhoneValidationMatchesServerCountryCodeAndLengthRules(){assertTrue(adminPhoneValid("+62 812345678"));listOf("0812345678","+000000000","+62abc123456","+123","+1234567890123456").forEach{assertFalse(it,adminPhoneValid(it))}}
}
