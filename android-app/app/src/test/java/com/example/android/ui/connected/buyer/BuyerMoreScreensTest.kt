package id.panenku.app.ui.connected.buyer

import android.graphics.BitmapFactory
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

internal object BuyerMoreFixtures {
    fun refund()=json("id" to "RF-002","orderId" to "PES-2026-002","refundAmount" to 202500,"status" to "menunggu_konfirmasi_dana","reason" to "Sayur rusak saat diterima. Disetujui petani untuk refund penuh.","destinationProvider" to "BCA","destinationAccount" to "000000000000","destinationName" to "Rina Catering","hasEvidence" to true,"hasTransferProof" to true,"transferSubmittedAt" to "2026-06-21T05:00:00Z","history" to listOf(json("toStatus" to "diajukan","changedAt" to "2026-06-20T05:00:00Z","note" to "Rina · Kondisi sayur + foto"),json("toStatus" to "disetujui","changedAt" to "2026-06-21T02:00:00Z","note" to "Darto · Refund Rp202.500"),json("toStatus" to "menunggu_konfirmasi_dana","changedAt" to "2026-06-21T05:00:00Z","note" to "Bukti transfer balik diunggah")))
    fun state():ConnectedState {
        val base=BuyerTransactionFixtures.state("selesai")
        val order=base.detail!!.deepCopy().apply{addProperty("orderStatus","selesai");addProperty("paymentStatus","terverifikasi");addProperty("receivedAt","2026-06-22T03:00:00Z")}
        val statuses=listOf("menunggu_petani","menunggu_pembayaran","verifikasi_pembayaran","diproses","selesai")
        val products=base.products.take(3).map{it.deepCopy().apply{addProperty("farmerId","farmer");addProperty("availableForPurchase",long("stockAvailable")>0)}}
        return base.copy(detail=order,orders=statuses.mapIndexed{i,status->order.deepCopy().apply{addProperty("orderId","PES-2026-00${i+1}");addProperty("orderStatus",status)}},buyerReviewLoaded=true,
            profile=json("fullName" to "Rina Catering","contactPhone" to "0800 0000 0000","deliveryAddress" to "Alamat utama"),buyerRefund=refund(),
            buyerTicket=json("id" to "TKT-026","refundId" to "RF-002","orderId" to "PES-2026-002","status" to "terbuka","reason" to "Dana belum diterima. Bukti transfer balik tersedia sejak 21 Jun, 12.00. Saya sudah mengecek rekening, tetapi dana belum masuk.","createdAt" to "2026-06-22T01:00:00Z"),buyerTicketDraft=json("note" to "Mutasi rekening belum menunjukkan dana masuk.","name" to "cek-mutasi-demo.png"),
            buyerStore=json("product" to products.first().deepCopy().apply{addProperty("storeName","Toko Sobianto");addProperty("farmerVerified",true)},"pickupLocation" to "Lowokwaru, Kota Malang · Kebun Darto","shippingEnabled" to true,"deliveryFee" to 2500,"serviceArea" to listOf("Lowokwaru"),"timeSlots" to listOf("08:00-16:00"),"farmerRating" to json("average" to 4.9,"reviewCount" to 48)),buyerStoreProducts=products)
    }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w375dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BuyerMoreScreensTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val vm get()=ConnectedViewModel(RuntimeEnvironment.getApplication())
    private fun host(nav:String?=null,paper:Color=FPaper,content:@Composable ()->Unit){
        val images:Map<String,Painter> = listOf("cabbage","tomato_stock","chili").associate{n->val b=javaClass.classLoader!!.getResourceAsStream("farmer-design/$n.jpg")!!.use{BitmapFactory.decodeStream(it)};"fixture://$n" to BitmapPainter(b.asImageBitmap())}
        compose.setContent{PanenkuTheme{CompositionLocalProvider(LocalBuyerInsets provides WindowInsets(top=47.dp,bottom=34.dp),LocalBuyerClock provides{BuyerTransactionFixtures.now},LocalBuyerPhoto provides{images[it]}){BuyerTransactionTheme(paper){Column(Modifier.fillMaxSize()){Box(Modifier.weight(1f)){content()};nav?.let{BuyerNavigation(it,{})}}}}}}
    }
    private fun capture(name:String)=compose.onRoot().captureRoboImage("build/outputs/screenshots/buyer8-$name.png")
    @Test @Config(qualifiers="w375dp-h1584dp-mdpi") fun renderOrdersAndFilterLocally(){
        host("orders"){BuyerOrdersScreen(BuyerMoreFixtures.state(),vm,{}, {}, {}, {}, {})};capture("orders")
        compose.onNodeWithText("Aktif",useUnmergedTree=true).performClick();compose.onNodeWithText("Beli Lagi").assertDoesNotExist()
    }
    @Test @Config(qualifiers="w375dp-h1103dp-mdpi") fun renderCompleted(){host{BuyerCompletedScreen(BuyerMoreFixtures.state(),vm,{}, {}, {}, {}, {})};capture("completed")}
    @Test @Config(qualifiers="w375dp-h812dp-mdpi") fun renderReviewAndRequireRating(){
        host{BuyerReviewScreen(BuyerMoreFixtures.state(),vm,{})};capture("review")
        compose.onNodeWithText("Kirim").performClick();compose.onNodeWithText("Pilih 1–5 bintang").assertIsDisplayed();compose.onNodeWithText("OK").performClick();compose.onNodeWithContentDescription("4 bintang").performScrollTo().performClick();compose.onNodeWithText("Kirim").assertIsEnabled()
    }
    @Test @Config(qualifiers="w375dp-h1371dp-mdpi") fun renderRefundAndRequireConfirmation(){
        host(paper=BPaper){BuyerRefundDetailScreen(BuyerMoreFixtures.state(),vm,"RF-002",{}, {})};capture("refund")
        compose.onNodeWithText("Dana sudah diterima").performScrollTo().performClick();compose.onNodeWithText("Dana benar-benar sudah diterima?").assertIsDisplayed();compose.onNodeWithText("Kembali").performClick()
    }
    @Test @Config(qualifiers="w375dp-h1321dp-mdpi") fun renderTicket(){host(paper=BPaper){BuyerTicketScreen(BuyerMoreFixtures.state(),vm,"TKT-026",{})};capture("ticket")}
    @Test @Config(qualifiers="w375dp-h1361dp-mdpi") fun renderStoreWithActualRatingAndNoFabricatedPublicReviews(){
        host("store"){BuyerStoreScreen(BuyerMoreFixtures.state(),vm,{})};capture("store")
        compose.onNodeWithText("Ulasan (48)").performScrollTo().performClick()
        compose.onNodeWithText("Rating keseluruhan ditampilkan di atas. Rincian ulasan pembeli belum tersedia pada layanan toko ini.").performScrollTo().assertIsDisplayed()
    }
    @Test @Config(qualifiers="w375dp-h1205dp-mdpi") fun renderAccountWithReadOnlyEmail(){host("account"){BuyerAccountScreen(BuyerMoreFixtures.state(),vm,{}, {}, {}, {},notifications={})};capture("account");compose.onNodeWithTag("buyer-input-Email").assertIsNotEnabled()}
    @Test fun buyerNavigationHasCartActionAndNoSellerProductCreation(){var route="";compose.setContent{BuyerNavigation("orders"){route=it}};compose.onNodeWithContentDescription("Tambah belanja").performClick();assertEquals("cart",route);compose.onNodeWithContentDescription("Buat produk baru").assertDoesNotExist()}
}
