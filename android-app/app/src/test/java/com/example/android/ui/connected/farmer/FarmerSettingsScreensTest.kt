package id.panenku.app.ui.connected.farmer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.gson.JsonObject
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.ConnectedState
import id.panenku.app.ui.theme.PanenkuTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal object FarmerSettingsFixtures {
    fun profile() = json("storeName" to "Kebun Darto", "ownerName" to "Darto Sobianto", "contactPhone" to "081835209860",
        "pickupLocation" to "Kebun Darto\nLowokwaru, Kota Malang, Jawa Timur\nGerbang kebun, sebelah pos warga",
        "serviceArea" to listOf("Lowokwaru"), "deliveryFee" to 2500, "shippingEnabled" to true,
        "timeSlots" to listOf("09:00-12:00","13:00-16:00"), "ktpStatus" to "disetujui")
    fun refund(status: String="diajukan") = json("id" to "RF-002", "orderId" to "PES-2026-002", "buyerId" to "buyer", "farmerId" to "farmer",
        "reason" to "Sayuran rusak saat diterima\nSawi layu dan beberapa tomat rusak. Pembeli meminta pengembalian penuh, termasuk ongkir yang sudah dibayar.",
        "refundAmount" to 202500, "destinationType" to "bank", "destinationProvider" to "BCA", "destinationAccount" to "000000000000",
        "destinationName" to "Rina Catering", "status" to status, "hasEvidence" to true, "hasTransferProof" to (status == "menunggu_konfirmasi_dana"),
        "createdAt" to "2026-06-20T05:00:00Z")
    fun verification(status: String="ditolak") = json("farmerId" to "farmer", "applicationId" to "VER-018", "status" to status,
        "submittedAt" to "2026-06-21T02:00:00Z", "reviewedAt" to "2026-06-22T02:00:00Z", "rejectionReason" to "Foto dokumen kurang jelas\nBagian nama dan alamat pada foto terpotong. Ambil ulang seluruh KTP, tanpa pantulan cahaya, lalu kirim kembali.",
        "dummyName" to "Petani Contoh", "dummyNik" to "DUMMY-0000", "fileName" to "ktp-contoh.jpg", "fileSizeBytes" to 1887436, "hasDocument" to true)
    fun state(): ConnectedState {
        val base = FarmerDesignFixtures.state()
        val bank = json("id" to "bank-id", "methodType" to "bank_transfer", "bankName" to "BRI", "accountHolderName" to "Darto Sobianto", "accountNumberLast4" to "0000")
        val qris = json("id" to "qris-id", "methodType" to "qris", "accountHolderName" to "Darto Sobianto", "hasQrisImage" to true)
        val order = FarmerDesignFixtures.order("selesai","PES-2026-002").apply { addProperty("receivedAt","2026-06-20T03:00:00Z") }
        return base.copy(profile=profile(),paymentMethods=listOf(bank,qris),paymentMethodDetail=bank.deepCopy().apply { addProperty("accountNumber","000000000000") },farmerRefund=refund(),farmerRefundOrder=order,farmerVerification=verification())
    }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w375dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FarmerSettingsScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun host(nav:Boolean=false,content:@Composable ()->Unit) { compose.setContent { PanenkuTheme {
        CompositionLocalProvider(LocalFarmerInsets provides WindowInsets(top=47.dp,bottom=34.dp)) { FarmerTheme {
            Column(Modifier.fillMaxSize()) { Box(Modifier.weight(1f)) { content() }; if(nav)FarmerNavigation("profile",{}, {}) }
        } }
    } } }
    private fun capture(name:String)=compose.onRoot().captureRoboImage("build/outputs/screenshots/seller13-$name.png")
    @Test @Config(qualifiers="w375dp-h1361dp-mdpi") fun renderProfile() { host(true) { FarmerProfileScreen(FarmerSettingsFixtures.state(),{}, {}, {_,_,_,_->}, {}) };capture("profile") }
    @Test @Config(qualifiers="w375dp-h1389dp-mdpi") fun renderServices() { host { FarmerServicesScreen(FarmerSettingsFixtures.state(),{}, {},initialDay=2) };capture("services") }
    @Test fun renderBank() { host { FarmerBankScreen(FarmerSettingsFixtures.state(),{}, {}) {_,_,_,_->} };capture("bank") }
    @Test fun renderQris() { host { FarmerQrisScreen(FarmerSettingsFixtures.state(),{}, {}) {_,_,_->} };capture("qris") }
    @Test @Config(qualifiers="w375dp-h1285dp-mdpi") fun renderVerification() { host { FarmerVerificationScreen(FarmerSettingsFixtures.state(),{}, {}) {} };capture("verification") }
    @Test @Config(qualifiers="w375dp-h1283dp-mdpi") fun renderRefundReview() { host { FarmerRefundReviewScreen(FarmerSettingsFixtures.state(),FarmerSettingsFixtures.refund(),{}, {}, {_,_->}, {}) };capture("refund-review") }
    @Test @Config(qualifiers="w375dp-h1217dp-mdpi") fun renderRefundTransfer() { host { FarmerRefundTransferScreen(FarmerSettingsFixtures.state(),FarmerSettingsFixtures.refund("disetujui"),{}, {}) {} };capture("refund-transfer") }
    @Test fun bankDoesNotSubmitAMaskedOrMissingAccount() {
        var sent=false
        host { FarmerBankScreen(FarmerSettingsFixtures.state().copy(paymentMethodDetail=null),{}, {}) {_,_,_,_->sent=true} }
        compose.onNodeWithText("Simpan Rekening").assertIsNotEnabled();assertFalse(sent)
    }
    @Test fun bankUsesTheLoadedFullAccountAndExistingMethodId() {
        var saved=""
        host { FarmerBankScreen(FarmerSettingsFixtures.state(),{}, {}) {id,bank,account,holder->saved="$id|$bank|$account|$holder"} }
        compose.onNodeWithText("Simpan Rekening").performClick();assertEquals("bank-id|BRI|000000000000|Darto Sobianto",saved)
    }
    @Test fun qrisNeedsNewImageBeforeSaving() {
        host { FarmerQrisScreen(FarmerSettingsFixtures.state(),{}, {}) {_,_,_->fail("No image selected")} }
        compose.onNodeWithText("Simpan QRIS").assertIsNotEnabled()
    }
    @Test fun servicesPreserveStoreAndUseSharedServerSlots() {
        var saved:JsonObject?=null
        host { FarmerServicesScreen(FarmerSettingsFixtures.state(),{}, {saved=it}) }
        compose.onNodeWithText("Simpan pengaturan layanan").performScrollTo().performClick()
        assertEquals("Kebun Darto",saved!!.text("storeName"));assertEquals(listOf("09:00-12:00","13:00-16:00"),saved!!.stringRows("timeSlots"));assertEquals(2500L,saved!!.long("deliveryFee"))
        assertFalse(saved!!.has("weekdays"));assertFalse(saved!!.has("deliverySlots"))
    }
    @Test fun overlappingSlotsCannotBeSubmitted() {
        var saved=false
        host { FarmerServicesScreen(FarmerSettingsFixtures.state(),{}, {saved=true}) }
        compose.onNodeWithTag("farmer-input-Slot ambil sendiri").performScrollTo().performTextReplacement("08:00-12:00 / 09:00-13:00")
        compose.onNodeWithText("Simpan pengaturan layanan").performScrollTo().performClick()
        compose.onNodeWithText("Slot layanan tidak boleh bertumpang tindih.").performScrollTo().assertIsDisplayed();assertFalse(saved)
    }
    @Test fun rejectingRefundRequiresReasonAndPreservesDecision() {
        var accepted:Boolean?=null;var reason=""
        host { FarmerRefundReviewScreen(FarmerSettingsFixtures.state(),FarmerSettingsFixtures.refund(),{}, {}, {a,r->accepted=a;reason=r}, {}) }
        compose.onNodeWithText("Tolak pengajuan dengan alasan").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("farmer-input-Alasan penolakan (wajib)").performScrollTo().performTextInput("Bukti belum jelas")
        compose.onNodeWithText("Tolak pengajuan dengan alasan").performScrollTo().performClick()
        assertEquals(false,accepted);assertEquals("Bukti belum jelas",reason)
    }
    @Test fun refundTransferDoesNotCompleteWithoutBuyerConfirmation() {
        host { FarmerRefundTransferScreen(FarmerSettingsFixtures.state(),FarmerSettingsFixtures.refund("menunggu_konfirmasi_dana"),{}, {}) {fail("Already submitted")} }
        compose.onNodeWithText("Kirim bukti transfer balik").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Hanya setelah pembeli memilih Dana sudah diterima.").performScrollTo().assertExists()
    }
    @Test fun copyTransferDestinationUsesFullAccountWithoutChangingRefundStatus() {
        val refund=FarmerSettingsFixtures.refund("disetujui")
        host { FarmerRefundTransferScreen(FarmerSettingsFixtures.state(),refund,{}, {}) {} }
        compose.onNodeWithText("Salin tujuan transfer").performScrollTo().performClick()
        compose.runOnIdle {
            val clipboard=compose.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            assertEquals("BCA 000000000000 a.n. Rina Catering",clipboard.primaryClip!!.getItemAt(0).text.toString())
            assertEquals("disetujui",refund.text("status"))
        }
    }
    @Test fun approvedVerificationCannotSubmitAgain() {
        val s=FarmerSettingsFixtures.state().copy(farmerVerification=FarmerSettingsFixtures.verification("disetujui"))
        host { FarmerVerificationScreen(s,{}, {}) {fail("Already approved")} }
        compose.onNodeWithText("Pilih dokumen & kirim ulang").assertDoesNotExist()
    }
}
