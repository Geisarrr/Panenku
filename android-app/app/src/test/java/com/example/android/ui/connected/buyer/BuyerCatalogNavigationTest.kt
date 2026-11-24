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
class BuyerCatalogNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun activeBuyerEntryLoadsCatalogAndSystemBackReturnsFromProductAndResults(){
        val server=MockWebServer();server.start()
        try {
            fun ok(body:String)=server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("""{"success":true,"data":$body}"""))
            fun req()=server.takeRequest(5,TimeUnit.SECONDS)!!
            val product="""{"id":"p","farmerId":"farmer","name":"Sawi Putih","storeName":"Toko","stockAvailable":10,"availableForPurchase":true,"pricePerKg":5000}"""
            fun home(){ok("""{"items":[$product],"totalElements":1}""");ok("""[{"id":"c","name":"Sayur","isActive":true}]""");ok("{\"deliveryAddress\":\"Alamat utama\"}");ok("{\"items\":[],\"unreadCount\":0}");ok("{\"items\":[]}")}
            val vm=ConnectedViewModel(RuntimeEnvironment.getApplication(),PanenkuApi(server.url("/api/v1/").toString()))
            compose.setContent{PanenkuTheme{CompositionLocalProvider(LocalBuyerInsets provides WindowInsets(0,0,0,0)){ConnectedPanenkuApp(vm)}}}
            fun idle(){compose.waitUntil(15000){compose.runOnIdle{};!vm.state.value.busy};compose.waitForIdle()}
            ok("""{"accessToken":"a","refreshToken":"r","user":{"id":"buyer-back","email":"buyer@example.test","role":"pembeli","status":"active"}}""");home()
            compose.runOnIdle{vm.login("buyer@example.test","password")};idle();repeat(6){req()}
            ok("""{"items":[$product],"totalElements":1}""");compose.onNodeWithText("Cari bahan segar...").performClick();idle();assertEquals("/api/v1/buyer/products",req().requestUrl!!.encodedPath)
            ok("""{"product":$product,"pickupLocation":"Malang","farmerRating":{"reviewCount":0}}""");compose.onNodeWithText("Sawi Putih").performScrollTo().performClick();idle();assertEquals("/api/v1/buyer/products/p",req().path)
            ok("""{"items":[$product],"totalElements":1}""");compose.runOnIdle{compose.activity.onBackPressedDispatcher.onBackPressed()};idle();req();compose.onNodeWithText("Cari hasil panen").assertIsDisplayed()
            home();compose.runOnIdle{compose.activity.onBackPressedDispatcher.onBackPressed()};idle();repeat(5){req()};compose.onNodeWithText("Panenku · Segar dari petani").assertIsDisplayed();assertFalse(compose.activity.isFinishing)
        } finally {server.shutdown()}
    }
}
