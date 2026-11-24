package id.panenku.app.ui.admin

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.*
import id.panenku.app.ui.connected.buyer.LocalBuyerInsets
import id.panenku.app.ui.theme.PanenkuTheme
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w375dp-h1000dp-mdpi")
class AdminNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun roleSelectionAdminLoginAndUserActionUseTheConnectedApplication(){
        val server=MockWebServer();server.start()
        try {
            fun ok(body:String)=server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"success":true,"data":$body}"""))
            fun req()=server.takeRequest(5,TimeUnit.SECONDS)!!
            val vm=ConnectedViewModel(RuntimeEnvironment.getApplication(),PanenkuApi(server.url("/api/v1/").toString()))
            compose.setContent{PanenkuTheme{CompositionLocalProvider(LocalBuyerInsets provides WindowInsets(0,0,0,0)){ConnectedPanenkuApp(vm)}}}
            fun idle(){compose.waitUntil(15000){compose.runOnIdle{};!vm.state.value.busy};compose.waitForIdle()}
            compose.mainClock.advanceTimeBy(3000);compose.waitForIdle()
            compose.onNodeWithText("Akses administrator").performClick();compose.waitForIdle()
            compose.onNodeWithTag("buyer-input-Email administrator").performTextInput("admin@example.test")
            compose.onAllNodes(hasSetTextAction())[1].performTextInput("password")
            ok("""{"accessToken":"a","refreshToken":"r","user":{"id":"admin","email":"admin@example.test","role":"admin","status":"active"}}""")
            ok("{\"items\":[],\"totalElements\":0}");ok("{\"items\":[],\"totalElements\":0}");ok("{\"items\":[],\"totalElements\":0}");ok("{\"items\":[]}")
            compose.onNodeWithText("Masuk sebagai admin").performScrollTo().performClick();idle();assertEquals("/api/v1/auth/sessions",req().path);repeat(4){assertTrue(req().path!!.startsWith("/api/v1/admin/"))};compose.onNodeWithText("Ringkasan admin").assertIsDisplayed()
            ok("""{"items":[{"id":"farmer","displayName":"Darto","email":"farmer@example.test","role":"petani","status":"active"}],"totalElements":1}""")
            compose.onAllNodesWithText("Pengguna & status akun")[0].performScrollTo().performClick();idle();assertEquals("/api/v1/admin/users?page=0&size=50",req().path)
            ok("""{"id":"farmer","displayName":"Darto","role":"petani","status":"active"}""");compose.onNodeWithText("Darto · #farmer").performClick();idle();assertEquals("/api/v1/admin/users/farmer",req().path)
            compose.onNodeWithTag("buyer-input-Alasan suspensi (wajib)").performScrollTo().performTextInput("Laporan ditinjau")
            val before=server.requestCount;compose.onNodeWithText("Suspensi akun dengan alasan").performScrollTo().performClick();compose.onNodeWithText("Suspensi akun ini?").assertIsDisplayed();assertEquals(before,server.requestCount)
            ok("null");ok("{\"items\":[],\"totalElements\":0}");ok("""{"id":"farmer","displayName":"Darto","role":"petani","status":"suspended"}""")
            compose.onNodeWithText("Lanjutkan").performClick();idle();assertEquals("PUT",req().method);repeat(2){assertEquals("GET",req().method)};assertEquals("suspended",vm.state.value.adminUi.user!!.text("status"))
        } finally {server.shutdown()}
    }
    @Test fun evidenceRoundTripKeepsSelectedTicketAndUnsavedDecisionNotes(){
        val server=MockWebServer();server.start()
        try {
            fun ok(body:String)=server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"success":true,"data":$body}"""))
            fun req()=server.takeRequest(5,TimeUnit.SECONDS)!!
            val ticket="""{"id":"ticket","refundId":"refund","orderId":"order","status":"terbuka","reason":"Dana belum diterima"}"""
            val refund="""{"id":"refund","status":"dieskalasi","hasEvidence":true,"hasTransferProof":true,"history":[]}"""
            val vm=ConnectedViewModel(RuntimeEnvironment.getApplication(),PanenkuApi(server.url("/api/v1/").toString()))
            compose.setContent{PanenkuTheme{CompositionLocalProvider(LocalBuyerInsets provides WindowInsets(0,0,0,0)){ConnectedPanenkuApp(vm)}}}
            fun idle(){compose.waitUntil(15000){compose.runOnIdle{};!vm.state.value.busy};compose.waitForIdle()}
            ok("""{"accessToken":"a","refreshToken":"r","user":{"id":"admin","email":"admin@example.test","role":"admin","status":"active"}}""")
            ok("{\"items\":[],\"totalElements\":0}");ok("""{"items":[$ticket],"totalElements":1}""");ok("{\"items\":[],\"totalElements\":0}");ok("{\"items\":[]}")
            compose.runOnIdle{vm.loginAdmin("admin@example.test","password")};idle();repeat(5){req()}
            ok("""{"items":[$ticket],"totalElements":1}""");compose.onNodeWithText("Tiket eskalasi").performClick();idle();req()
            ok(ticket);ok(refund);compose.onNodeWithText("#ticket").performClick();idle();req();req()
            compose.onNodeWithTag("buyer-input-Hasil pemeriksaan / tindak lanjut").performScrollTo().performTextInput("Periksa bukti transfer sebelum keputusan")
            ok("[]");compose.onNodeWithText("Tinjau seluruh bukti").performScrollTo().performClick();idle();assertEquals("/api/v1/admin/orders/order/payment-proofs",req().path)
            ok("""{"items":[$ticket],"totalElements":1}""");ok(ticket);ok(refund)
            compose.onNodeWithContentDescription("Kembali").performClick();idle();repeat(3){req()}
            compose.onNodeWithTag("buyer-input-Hasil pemeriksaan / tindak lanjut").performScrollTo().assertTextEquals("Periksa bukti transfer sebelum keputusan")
            assertEquals("ticket",vm.state.value.adminUi.dispute!!.text("id"))
        } finally {server.shutdown()}
    }

}
