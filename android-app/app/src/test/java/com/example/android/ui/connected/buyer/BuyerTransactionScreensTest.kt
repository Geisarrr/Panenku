package id.panenku.app.ui.connected.buyer

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
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
import java.time.Instant

internal object BuyerTransactionFixtures {
    val now:Instant=Instant.parse("2026-06-22T05:30:00Z")
    fun order(status:String="menunggu_penerimaan")=BuyerLocalFixtures.order(status).apply {
        addProperty("storeName","Toko Sobianto");addProperty("expiresAt","2026-06-23T06:00:00Z")
        add("history", com.google.gson.Gson().toJsonTree(listOf(
            json("toStatus" to "diproses","changedAt" to "2026-06-22T05:00:00Z","note" to "Darto memeriksa dana masuk"),
            json("toStatus" to "diantar","changedAt" to "2026-06-22T05:10:00Z","note" to "Berangkat dari Kebun Darto"),
            json("toStatus" to "menunggu_penerimaan","changedAt" to "2026-06-22T05:20:00Z","note" to "Ditandai tiba oleh Darto"))))
    }
    fun state(status:String="menunggu_penerimaan"):ConnectedState {
        val addr=SavedBuyerAddress(id="one",label="Dapur utama",recipient="Rina Catering",phone="0800 0000 0000",address="Gang H.Gumat 1 no.14, Lowokwaru, Kec Lowokwaru, Kota Malang, Jawatimur 64293",district="Lowokwaru",note="Dekat masjid al hidayah")
        val items=order().list("items").map { it.deepCopy().apply {addProperty("name",text("productNameSnapshot"));addProperty("pricePerKg",long("pricePerKgSnapshot"))} }
        val quote=json("canCheckout" to true,"items" to items,"subtotalItems" to 200000,"fulfillmentMethod" to "pickup","deliveryFee" to 0,"totalPayment" to 200000,"scheduledDate" to "2026-06-24","scheduledTimeSlot" to "09:00-12:00")
        return ConnectedState(user=ApiUser("buyer","rina@example.test","pembeli","active"),detail=order(status),buyerTransactionLoaded=true,
            products=BuyerLocalFixtures.stock(),shippingEnabled=true,deliveryAreas=listOf("Lowokwaru"),buyerAddresses=listOf(addr),buyerAddressId=addr.id,
            cart=json("items" to items,"storeName" to "Darto Sobianto"),quote=quote,
            fulfillment=json("pickupLocation" to "Kebun Darto, Lowokwaru, Malang","dates" to listOf(json("date" to "2026-06-24","timeSlots" to listOf("09:00-12:00")))),
            options=listOf(json("id" to "bank","methodType" to "bank_transfer","bankName" to "BRI","accountNumber" to "0000 0000 0000","accountHolderName" to "Darto Sobianto")))
    }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w375dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BuyerTransactionScreensTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val vm get()=ConnectedViewModel(RuntimeEnvironment.getApplication())
    private fun host(paper:androidx.compose.ui.graphics.Color=BPaper,content:@Composable ()->Unit) {
        val images:Map<String,Painter> = listOf("cabbage","tomato_stock").associate { n ->
            val bmp=javaClass.classLoader!!.getResourceAsStream("farmer-design/$n.jpg")!!.use{BitmapFactory.decodeStream(it)}
            "fixture://$n" to BitmapPainter(bmp.asImageBitmap())
        }
        compose.setContent { PanenkuTheme {CompositionLocalProvider(LocalBuyerInsets provides WindowInsets(top=47.dp,bottom=34.dp),LocalBuyerClock provides {BuyerTransactionFixtures.now},LocalBuyerPhoto provides {images[it]}) {BuyerTransactionTheme(paper,content)}} }
    }
    private fun capture(name:String)=compose.onRoot().captureRoboImage("build/outputs/screenshots/buyer7-$name.png")
    @Test @Config(qualifiers="w375dp-h1426dp-mdpi") fun renderCheckoutAndRequireExplicitConsent() {
        host(FPaper) {BuyerCheckoutScreen(BuyerTransactionFixtures.state(),vm,{}, {}, {})}
        compose.onNodeWithText("Buat Pesanan").assertIsNotEnabled()
        compose.onNodeWithTag("buyer-checkout-consent").performScrollTo().performClick()
        compose.onNodeWithText("Buat Pesanan").assertIsEnabled()
        capture("checkout")
    }
    @Test @Config(qualifiers="w375dp-h1384dp-mdpi") fun renderAddresses() {host {BuyerAddressesScreen(BuyerTransactionFixtures.state(),vm,{})};capture("addresses")}
    @Test @Config(qualifiers="w375dp-h1027dp-mdpi") fun renderCreated() {host{BuyerOrderCreatedScreen(BuyerTransactionFixtures.order("menunggu_petani"),{}, {})};capture("created")}
    @Test @Config(qualifiers="w375dp-h1361dp-mdpi") fun renderInitialPaymentWithoutSelectedProof() {
        host{BuyerPaymentScreen(BuyerTransactionFixtures.state("menunggu_pembayaran"),vm,{})};capture("payment")
        compose.onNodeWithText("Kirim Bukti Pembayaran").performScrollTo().assertIsNotEnabled()
    }
    @Test @Config(qualifiers="w375dp-h1114dp-mdpi") fun renderRejectedProofWithReason() {
        val s=BuyerTransactionFixtures.state("menunggu_pembayaran").let {it.copy(detail=it.detail!!.deepCopy().apply{addProperty("paymentStatus","bukti_ditolak")},proofs=listOf(json("id" to "proof","decision" to "tolak","status" to "bukti_ditolak","reason" to "Nominal dan penerima terpotong","submittedAt" to "2026-06-22T04:20:00Z","decidedAt" to "2026-06-22T06:00:00Z","originalFileName" to "bukti-awal.jpg")),destination=json("methodType" to "bank_transfer","accountHolderName" to "Darto Sobianto","bankName" to "BRI","accountNumber" to "0000 0000 0000"))}
        host{BuyerPaymentScreen(s,vm,{})};capture("rejected")
        compose.onAllNodesWithText("Nominal dan penerima terpotong").assertCountEquals(2)
        compose.onNodeWithText("Unggah ulang bukti").performScrollTo().assertIsNotEnabled()
    }
    @Test @Config(qualifiers="w375dp-h1373dp-mdpi") fun renderTrackingAndRequireConfirmation() {
        host{BuyerTrackingScreen(BuyerTransactionFixtures.state(),vm,{}, {}, {}, {}, {})};capture("tracking")
        compose.onNodeWithText("Konfirmasi pesanan diterima").performScrollTo().performClick()
        compose.onNodeWithText("Barang sudah diterima?").assertIsDisplayed()
        compose.onNodeWithText("Kembali").performClick()
    }
    @Test @Config(qualifiers="w375dp-h993dp-mdpi") fun renderSchedule() {
        val s=BuyerTransactionFixtures.state("menunggu_pembayaran");s.detail!!.add("pendingScheduleChange",json("id" to "proposal","baseVersion" to 0,"createdAt" to "2026-06-22T01:30:00Z","proposedDate" to "2026-06-25","proposedSlot" to "09:00-12:00","reason" to "Hujan deras membuat panen tertunda. Kami perlu satu hari tambahan agar sayur tiba dalam kondisi segar."))
        host{BuyerScheduleScreen(s,vm,{})};capture("schedule")
    }
    @Test @Config(qualifiers="w375dp-h1625dp-mdpi") fun renderRefundRequiresEvidenceAndConsent() {
        val s=BuyerTransactionFixtures.state("selesai");s.detail!!.addProperty("paymentStatus","terverifikasi");s.detail!!.addProperty("receivedAt","2026-06-22T03:00:00Z")
        host{BuyerRefundScreen(s,vm,{}, {})};capture("refund")
        compose.onNodeWithText("Kirim pengajuan Rp202.500").performScrollTo().assertIsNotEnabled()
    }
    @Test fun checkoutMustNotConfirmAQuoteForAnotherFulfillmentMethod() {
        val state=BuyerTransactionFixtures.state().let { it.copy(quote=it.quote!!.deepCopy().apply {
            addProperty("fulfillmentMethod","delivery")
        }) }
        host(FPaper) { BuyerCheckoutScreen(state,vm,{}, {}, {}) }
        compose.onNodeWithTag("buyer-checkout-consent").assertDoesNotExist()
        compose.onNodeWithText("Buat Pesanan").assertIsNotEnabled()
        compose.onNodeWithText("Periksa total dahulu").assertExists()
    }

    @Test fun checkoutRejectsNonzeroPickupFee() {
        val state=BuyerTransactionFixtures.state().let {
            it.copy(quote=it.quote!!.deepCopy().apply {addProperty("deliveryFee",2500)})
        }
        host(FPaper) {BuyerCheckoutScreen(state,vm,{}, {}, {})}
        compose.onNodeWithTag("buyer-checkout-consent").assertDoesNotExist()
        compose.onNodeWithText("Buat Pesanan").assertIsNotEnabled()
    }
    @Test fun checkoutRejectsQuoteForOldDeliveryAddress() {
        val state=BuyerTransactionFixtures.state().let {
            it.copy(buyerFulfillment="delivery",quote=it.quote!!.deepCopy().apply {
                addProperty("fulfillmentMethod","delivery")
                addProperty("deliveryDistrict","Lowokwaru")
                addProperty("deliveryAddress","Alamat lama")
            })
        }
        host(FPaper) {BuyerCheckoutScreen(state,vm,{}, {}, {})}
        compose.onNodeWithTag("buyer-checkout-consent").assertDoesNotExist()
        compose.onNodeWithText("Buat Pesanan").assertIsNotEnabled()
    }

    @Test fun deliveryQuoteForSelectedAddressCanBeConfirmedWithServerTotal() {
        val state=BuyerTransactionFixtures.state().let { state ->
            state.copy(buyerFulfillment="delivery",quote=state.quote!!.deepCopy().apply {
                addProperty("fulfillmentMethod","delivery")
                addProperty("deliveryDistrict","Lowokwaru")
                addProperty("deliveryAddress",state.buyerAddresses.single().snapshot())
                addProperty("deliveryFee",2500)
                addProperty("totalPayment",202500)
            })
        }
        host(FPaper) {BuyerCheckoutScreen(state,vm,{}, {}, {})}
        compose.onNodeWithText("Buat Pesanan").assertIsNotEnabled()
        compose.onNodeWithTag("buyer-checkout-consent").performScrollTo().performClick()
        compose.onNodeWithText("Buat Pesanan").assertIsEnabled()
        compose.onAllNodesWithText("Rp202.500").assertCountEquals(2)
    }

}
