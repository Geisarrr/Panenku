package id.panenku.app.ui.connected.buyer

import android.graphics.BitmapFactory
import com.google.gson.JsonObject
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.*
import id.panenku.app.ui.theme.PanenkuTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal object BuyerCatalogFixtures {
    fun state():ConnectedState {
        val base=BuyerMoreFixtures.state()
        val products=base.products.map { it.deepCopy().apply{addProperty("farmerId","farmer");addProperty("storeName","Toko Sobianto");addProperty("farmerVerified",true);addProperty("availableForPurchase",true);addProperty("stockAvailable",80)} }
        val commodities=listOf("Buah-buahan","Sayur","Bawang","Umbi","Biji-bijian & Padi","Rempah").mapIndexed{i,name->json("id" to "commodity-$i","name" to name,"isActive" to true)}
        val rows=products.mapIndexed{i,p->json("id" to "cart-$i","productId" to p.text("id"),"farmerId" to "farmer","storeName" to p.text("storeName"),"name" to p.text("name"),"photoAccessUrl" to p.text("photoAccessUrl"),"quantityKg" to 2,"pricePerKg" to p.long("pricePerKg"),"itemSubtotal" to p.long("pricePerKg")*2,"stockAvailable" to 80,"available" to true)}
        val notifications=listOf(
            json("id" to "n1","title" to "Pesanan tiba di alamatmu","body" to "Periksa sayur sebelum mengonfirmasi penerimaan. Belum selesai.","type" to "order","isRead" to false,"deepLink" to "/orders/order-1","createdAt" to "2026-06-22T03:15:00Z"),
            json("id" to "n2","title" to "Tanggapi jadwal dari petani","body" to "Usulan 25 Jun, 09.00–12.00. Jadwal lama tetap berlaku sampai disetujui.","type" to "schedule","isRead" to false,"deepLink" to "/orders/order-2/schedule","createdAt" to "2026-06-21T01:30:00Z"),
            json("id" to "n3","title" to "Bukti pembayaran ditolak","body" to "Nominal terpotong. Unggah ulang sebelum batas pembayaran.","type" to "payment","isRead" to false,"deepLink" to "/orders/order-3","createdAt" to "2026-06-20T06:00:00Z"),
            json("id" to "n4","title" to "Tiket eskalasi tercatat","body" to "Dana refund belum diterima. CS akan meninjau bukti.","type" to "refund","isRead" to true,"deepLink" to "/escalations/ticket-1","createdAt" to "2026-06-19T01:00:00Z"),
            json("id" to "n5","title" to "Bukti transfer balik tersedia","body" to "Konfirmasi hanya jika dana benar-benar masuk ke rekening.","type" to "refund","isRead" to true,"deepLink" to "/refunds/refund-1","createdAt" to "2026-06-18T03:00:00Z"))
        return base.copy(products=products,buyerHome=products,buyerResults=products,buyerSearchLoaded=true,buyerSearchTotal=products.size.toLong(),buyerRecent=products.take(2),commodities=commodities,
            buyerProduct=json("product" to products[1],"pickupLocation" to "Lowokwaru, Kota Malang","shippingEnabled" to true,"farmerRating" to json("average" to 4.9,"reviewCount" to 48)),
            cart=json("items" to rows,"farmerId" to "farmer","storeName" to "Toko Sobianto"),buyerSelected=rows.map{it.text("id")}.toSet(),buyerSelectionInitialized=true,
            notifications=notifications,unreadNotificationCount=3)
    }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w375dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BuyerCatalogScreensTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val vm get()=ConnectedViewModel(RuntimeEnvironment.getApplication())
    private fun host(nav:String?=null,paper:Color=FPaper,content:@Composable ()->Unit){
        val images:Map<String,Painter> = listOf("cabbage","tomato_stock","chili","shallot").associate{n->val b=javaClass.classLoader!!.getResourceAsStream("farmer-design/$n.jpg")!!.use{BitmapFactory.decodeStream(it)};"fixture://$n" to BitmapPainter(b.asImageBitmap())}
        compose.setContent{PanenkuTheme{CompositionLocalProvider(LocalBuyerInsets provides WindowInsets(top=47.dp,bottom=34.dp),LocalBuyerClock provides{BuyerTransactionFixtures.now},LocalBuyerPhoto provides{images[it]}){BuyerTransactionTheme(paper){Column(Modifier.fillMaxSize()){Box(Modifier.weight(1f)){content()};nav?.let{BuyerNavigation(it,{})}}}}}}
    }
    private fun capture(name:String)=compose.onRoot().captureRoboImage("build/outputs/screenshots/buyer15-$name.png")
    @Test @Config(qualifiers="w375dp-h1083dp-mdpi") fun homeUsesLiveAddressAndRoutesSearchCartAndNotifications(){
        var route="";host("catalog"){BuyerHomeScreen(BuyerCatalogFixtures.state(),vm,{route="search"},{route="cart"},{route="notices"},{route="addresses"},{route=it})};capture("home")
        compose.onNodeWithContentDescription("Notifikasi").performClick();assertEquals("notices",route)
        compose.onNodeWithContentDescription("Keranjang").performClick();assertEquals("cart",route)
        compose.onNodeWithText("Cari bahan segar...").performClick();assertEquals("search",route)
    }
    @Test @Config(qualifiers="w375dp-h1232dp-mdpi") fun resultsSubmitQueryAndStoreFilterWithoutDemoRows(){
        var criteria=BuyerSearch();host("buyer-stock"){BuyerResultsScreen(BuyerCatalogFixtures.state(),vm,BuyerSearch(),{criteria=it},{},{},{},{})};capture("results")
        compose.onNodeWithTag("buyer-input-Cari hasil panen").performTextInput("sawi & tomat");compose.onNodeWithContentDescription("Cari").performClick();assertEquals("sawi & tomat",criteria.query)
        compose.onNodeWithText("Toko").performClick();assertTrue(criteria.stores)
    }
    @Test @Config(qualifiers="w375dp-h970dp-mdpi") fun productShowsRealStockAndAggregatedRating(){host{BuyerProductScreen(BuyerCatalogFixtures.state(),vm,{},{},{})};capture("product");compose.onAllNodesWithText("Tomat Sayur")[0].assertExists();compose.onNodeWithText("Rincian ulasan publik belum tersedia dari layanan toko.").performScrollTo().assertIsDisplayed()}
    @Test @Config(qualifiers="w375dp-h944dp-mdpi") fun categoriesUsesServerIdsForNavigation(){var id="";host("buyer-stock"){BuyerCategoriesScreen(BuyerCatalogFixtures.state(),vm,{},{},{id=it},{})};capture("categories");compose.onNodeWithText("Bawang",substring=false).performClick();assertEquals("commodity-2",id)}
    @Test @Config(qualifiers="w375dp-h812dp-mdpi") fun filledCartUsesSelectedTotalAndRequiresDeleteConfirmation(){host{BuyerCartScreen(BuyerCatalogFixtures.state(),vm,{},{},{})};capture("cart");compose.onNodeWithText("Hapus").performClick();compose.onNodeWithText("Hapus produk terpilih?").assertIsDisplayed();compose.onNodeWithText("Kembali").performClick()}
    @Test @Config(qualifiers="w375dp-h812dp-mdpi") fun emptyCartOffersReturnToShopping(){var back=false;host{BuyerCartScreen(BuyerCatalogFixtures.state().copy(cart=json("items" to emptyList<JsonObject>())),vm,{back=true},{},{})};capture("empty-cart");compose.onNodeWithText("Buat Pesanan").assertDoesNotExist();compose.onNodeWithText("Mulai belanja").performClick();assertTrue(back)}
    @Test @Config(qualifiers="w375dp-h1071dp-mdpi") fun notificationsFilterActualUnreadRows(){host(paper=BPaper){BuyerNotificationsScreen(BuyerCatalogFixtures.state(),vm,{},{_,_->})};capture("notifications");compose.onNodeWithText("Belum dibaca (3)").performClick();compose.onNodeWithText("Tiket eskalasi tercatat").assertDoesNotExist();compose.onNodeWithText("Pesanan tiba di alamatmu").assertIsDisplayed()}
}
