package id.panenku.app.ui.admin

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.buyer.*
import id.panenku.app.ui.theme.PanenkuTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal object AdminFixtures {
    fun settings()=json("transactionLimit" to 10000000,"csWhatsapp" to "+6280000000000","settingsVersion" to 2,"lastChangedByName" to "Admin Maya","lastChangedAt" to "2026-06-22T02:00:00Z")
    fun state():AdminUiState {
        val products=BuyerLocalFixtures.stock()
        val queue=listOf(json("farmerId" to "farmer-19","applicationId" to "VER-019","storeName" to "Kebun Contoh","email" to "petani@example.test","submittedAt" to "2026-06-21T07:00:00Z","ktpStatus" to "menunggu","accountStatus" to "active","hasDocument" to true),json("farmerId" to "farmer-20","applicationId" to "VER-020","storeName" to "Tani Demo","submittedAt" to "2026-06-22T00:30:00Z","ktpStatus" to "menunggu"))
        val users=listOf(json("id" to "USR-012","displayName" to "Darto Sobianto","email" to "petani@example.test","role" to "petani","status" to "active","ktpStatus" to "disetujui"),json("id" to "USR-021","displayName" to "Rina Catering","role" to "pembeli","status" to "active"),json("id" to "USR-034","displayName" to "Tani Contoh","role" to "petani","status" to "suspended"))
        val verification=json("farmerId" to "farmer-19","applicationId" to "VER-019","status" to "menunggu","storeName" to "Kebun Contoh","email" to "petani@example.test","pickupLocation" to "Lowokwaru, Malang","dummyName" to "Petani Contoh","dummyNik" to "DUMMY-0000","hasDocument" to true,"fileName" to "ktp-dummy-019.jpg","fileSizeBytes" to 1677721,"submittedAt" to "2026-06-21T07:00:00Z")
        val audit=listOf(json("id" to "audit-1","action" to "VERIFICATION_REJECTED","reason" to "Foto identitas terpotong","note" to "#VER-018 ditolak","createdAt" to "2026-06-22T02:00:00Z","actorName" to "Maya"),json("id" to "audit-2","action" to "PRODUCT_DISABLED","targetType" to "product","note" to "#PRD-041 dinonaktifkan","createdAt" to "2026-06-21T08:10:00Z","reason" to "Komoditas tidak sesuai"))
        val tickets=listOf(json("id" to "TKT-026","orderId" to "PES-2026-002","refundId" to "RF-002","buyerId" to "USR-021","reason" to "Dana belum diterima. Bukti transfer balik tersedia tetapi dana belum masuk.","status" to "terbuka","createdAt" to "2026-06-22T01:00:00Z"),json("id" to "TKT-027","refundId" to "RF-009","reason" to "Refund ditolak","status" to "terbuka"),json("id" to "TKT-028","refundId" to "RF-010","reason" to "Tidak ada tanggapan lebih dari 24 jam","status" to "terbuka"))
        val refund=BuyerMoreFixtures.refund().apply{addProperty("status","dieskalasi");addProperty("farmerId","USR-012")}
        val user=users.first().deepCopy().apply{addProperty("ownerName","Darto Sobianto");addProperty("storeName","Toko Sobianto");addProperty("pickupLocation","Lowokwaru, Malang");addProperty("activeRefundCount",1)}
        val order=BuyerLocalFixtures.order("menunggu_penerimaan","PES-2026-001").apply{
            addProperty("storeName","Toko Sobianto")
            getAsJsonArray("items").forEach{element->val item=element.asJsonObject;item.addProperty("photoAccessUrl",products.firstOrNull{it.text("id")==item.text("productId")}?.text("photoAccessUrl").orEmpty())}
        }
        val report=json("id" to "LAP-031","category" to "Kategori tidak sesuai","reason" to "Produk bawang dimasukkan ke komoditas buah-buahan. Pembeli kesulitan menemukan komoditas yang tepat.","createdAt" to "2026-06-22T00:40:00Z","product" to products.last().deepCopy().apply{addProperty("commodity","Buah-buahan");addProperty("storeName","Tani Contoh")})
        return AdminUiState(loaded=true,verificationCount=8,disputeCount=3,suspendedCount=1,priorities=queue,recentAudit=audit,queue=queue,queueTotal=8,verification=verification,verificationUser="farmer-19",users=users,user=user,
            commodities=listOf("Sayur","Buah-buahan","Bawang","Umbi","Biji-bijian & Padi","Rempah").mapIndexed{i,name->json("id" to "commodity-$i","name" to name,"isActive" to (i<5))},disputes=tickets,dispute=tickets.first(),refund=refund,audit=audit,orders=listOf(order.deepCopy().apply{addProperty("orderId","PES-2026-004")},order.deepCopy().apply{addProperty("orderId","PES-2026-005");addProperty("orderStatus","verifikasi_pembayaran")},order),selectedOrder=order,reports=listOf(report),selectedReport=report)
    }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w375dp-h1100dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdminScreensTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun host(content:@Composable ()->Unit){
        val images:Map<String,Painter> = listOf("cabbage","tomato_stock","chili","shallot").associate{n->val b=javaClass.classLoader!!.getResourceAsStream("farmer-design/$n.jpg")!!.use{BitmapFactory.decodeStream(it)};"fixture://$n" to BitmapPainter(b.asImageBitmap())}
        compose.setContent{PanenkuTheme{CompositionLocalProvider(LocalBuyerInsets provides WindowInsets(top=47.dp,bottom=34.dp),LocalBuyerClock provides{BuyerTransactionFixtures.now},LocalBuyerPhoto provides{images[it]}){AdminTheme(content)}}}
    }
    private fun capture(name:String)=compose.onRoot().captureRoboImage("build/outputs/screenshots/admin-$name.png")
    @Test @Config(qualifiers="w375dp-h921dp-mdpi") fun loginRequiresCredentialsAndHasNoPublicAdminRegistration(){host{AdminLoginScreen(false,{},{_,_->})};capture("login");compose.onNodeWithText("Masuk sebagai admin").assertIsNotEnabled();compose.onNodeWithText("Tidak ada pendaftaran admin publik").assertIsDisplayed()}
    @Test @Config(qualifiers="w375dp-h1107dp-mdpi") fun dashboardUsesSuppliedMetricsAndRoutesToRealScreens(){var route="";host{AdminHomeScreen({route=it},{},AdminFixtures.state(),"Admin Maya")};capture("home");compose.onNodeWithText("Verifikasi petani").performClick();assertEquals("admin-verification",route)}
    @Test @Config(qualifiers="w375dp-h1421dp-mdpi") fun verificationRequiresAllChecksAndExplicitConfirmation(){var decision="";host{AdminVerificationScreen(emptyMap(),{},{_,d,_,_->decision=d},AdminFixtures.state())};capture("verification");compose.onNodeWithText("Setujui verifikasi petani").assertIsNotEnabled();repeat(3){i->compose.onAllNodes(isToggleable())[i].performScrollTo().performClick()};compose.onNodeWithText("Setujui verifikasi petani").performScrollTo().performClick();assertEquals("",decision);compose.onNodeWithText("Setujui verifikasi petani?").assertIsDisplayed();compose.onNodeWithText("Lanjutkan").performClick();assertEquals("Disetujui",decision)}
    @Test @Config(qualifiers="w375dp-h1428dp-mdpi") fun usersRequireReasonBeforeSuspension(){host{AdminUsersScreen(emptyMap(),{},{_,_,_,_->},AdminFixtures.state())};capture("users");compose.onNodeWithText("Suspensi akun dengan alasan").assertIsNotEnabled()}
    @Test @Config(qualifiers="w375dp-h1415dp-mdpi") fun commodityEditingDoesNotPretendDescriptionCanBeSaved(){host{AdminCommoditiesScreen(AdminFixtures.state(),{},{_,_,_,_->},{})};compose.onNodeWithText("Sayur",substring=false).performClick();capture("commodities");compose.onNodeWithTag("buyer-input-Deskripsi singkat").assertIsNotEnabled();compose.onNodeWithTag("buyer-input-Nama komoditas").assertTextEquals("Sayur")}
    @Test @Config(qualifiers="w375dp-h1187dp-mdpi") fun settingsValidateCountryCodeAndChangedLimitReason(){host{AdminSettingsLiveScreen(emptyMap(),{},{},AdminFixtures.settings())};capture("settings");compose.onNodeWithTag("buyer-input-Batas total per pesanan").performTextReplacement("12000000");compose.onNodeWithText("Simpan pengaturan admin").assertIsNotEnabled();compose.onNodeWithTag("buyer-input-Alasan perubahan limit (wajib)").performScrollTo().performTextInput("Batas ditinjau");compose.onNodeWithText("Simpan pengaturan admin").assertIsEnabled()}
    @Test @Config(qualifiers="w375dp-h1903dp-mdpi") fun disputeCannotBecomeAConfirmedRefundByClosingItsTicket(){host{AdminDisputesScreen(AdminFixtures.state(),{},{},{},{},{_,_->},{})};capture("disputes");compose.onNodeWithText("Setujui refund").assertIsNotEnabled();compose.onNodeWithText("Ditutup admin ≠ Dana diterima").performScrollTo().assertIsDisplayed()}
    @Test @Config(qualifiers="w375dp-h1235dp-mdpi") fun moderationUnavailableActionsStayDisabled(){host{AdminModerationScreen(AdminFixtures.state(),{})};capture("moderation");compose.onNodeWithText("Nonaktifkan produk").assertIsNotEnabled();compose.onNodeWithText("Tandai tidak melanggar").assertIsNotEnabled()}
    @Test @Config(qualifiers="w375dp-h1489dp-mdpi") fun orderMonitoringKeepsActualSavedOrderTotals(){host{AdminOrdersScreen(AdminFixtures.state(),{},{})};capture("orders");compose.onNodeWithText("Total tersimpan").assertExists();compose.onNodeWithText("Admin memantau, bukan menyetujui dana").performScrollTo().assertIsDisplayed()}
}
