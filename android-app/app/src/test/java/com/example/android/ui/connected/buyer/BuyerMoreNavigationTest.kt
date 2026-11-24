package id.panenku.app.ui.connected.buyer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.*
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
@Config(sdk=[34],qualifiers="w375dp-h900dp-mdpi")
class BuyerMoreNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun activeEntryConnectsOrdersReviewAndAccountToTheirRealApiRoutes(){
        val server=MockWebServer();server.start()
        try {
            fun ok(body:String)=server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"success":true,"data":$body}"""))
            fun req()=server.takeRequest(5,TimeUnit.SECONDS)!!
            val vm=ConnectedViewModel(RuntimeEnvironment.getApplication(),PanenkuApi(server.url("/api/v1/").toString()))
            compose.setContent{PanenkuTheme{CompositionLocalProvider(LocalBuyerInsets provides WindowInsets(0,0,0,0)){ConnectedPanenkuApp(vm)}}}
            fun idle(){compose.waitUntil(15000){compose.runOnIdle{};!vm.state.value.busy};compose.waitForIdle()}
            ok("""{"accessToken":"access","refreshToken":"refresh","user":{"id":"buyer-nav","email":"buyer@example.test","role":"pembeli","status":"active"}}""")
            ok("""{"items":[],"totalElements":0}""")
            ok("[]");ok("{}");ok("{\"items\":[],\"unreadCount\":0}");ok("{\"items\":[]}")
            compose.runOnIdle{vm.login("buyer@example.test","password")};idle();assertEquals("/api/v1/auth/sessions",req().path);assertEquals("/api/v1/buyer/products?search=&sort=price_asc&page=0&size=20",req().path);repeat(4){req()}
            ok("""{"items":[{"orderId":"order","orderStatus":"selesai","storeName":"Toko","items":[],"totalPayment":1000}],"totalElements":1}""")
            compose.onNodeWithContentDescription("Pesanan").performClick();idle();assertEquals("/api/v1/buyer/orders?page=0&size=20",req().path)
            ok("""{"orderId":"order","orderStatus":"selesai","items":[],"totalPayment":1000}""");ok("null")
            compose.onNodeWithText("Beri Ulasan").performScrollTo().performClick();idle();assertEquals("/api/v1/buyer/orders/order",req().path);assertEquals("/api/v1/buyer/orders/order/review",req().path)
            compose.onNodeWithText("Ulasan Pesanan").assertIsDisplayed()
            ok("""{"orderId":"order","orderStatus":"selesai","items":[]}""");ok("null");server.enqueue(MockResponse().setResponseCode(404))
            compose.onNodeWithContentDescription("Kembali").performClick();idle();repeat(3){req()}
            ok("""{"items":[],"totalElements":0}""");compose.onNodeWithContentDescription("Kembali").performClick();idle();req()
            ok("""{"fullName":"Pemilik","contactPhone":"08123","deliveryAddress":"Alamat tetap"}""")
            compose.onNodeWithContentDescription("Profil").performClick();idle();assertEquals("/api/v1/auth/profile",req().path)
            compose.onNodeWithTag("buyer-input-Nama pembeli / usaha").assertTextEquals("Pemilik")
            compose.onNodeWithTag("buyer-input-Email").assertIsNotEnabled()
        }finally{server.shutdown()}
    }
}
