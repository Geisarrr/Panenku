package id.panenku.app.ui.connected.buyer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*
import java.time.Instant

internal val LocalBuyerClock = staticCompositionLocalOf<() -> Instant> { { Instant.now() } }
internal val BPaper = Color(0xFFF3FBF2)
internal val LocalBuyerInsets = staticCompositionLocalOf<WindowInsets?> { null }
@Composable internal fun BuyerTransactionTheme(paper: Color = BPaper, content: @Composable () -> Unit) {
    val device = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(paper), contentAlignment = Alignment.TopCenter) {
        val scale = maxWidth.value.coerceAtMost(480f) / 375f
        CompositionLocalProvider(LocalDensity provides Density(device.density * scale, device.fontScale.coerceAtLeast(1f))) {
            ProvideTextStyle(TextStyle(fontFamily = BuyerFont, color = FInk, fontSize = 14.sp,
                letterSpacing = 0.sp, platformStyle = PlatformTextStyle(includeFontPadding = false))) {
                Box(Modifier.widthIn(max = 375.dp).fillMaxWidth().fillMaxHeight()
                    .windowInsetsPadding(LocalBuyerInsets.current ?: WindowInsets.safeDrawing).imePadding()) { content() }
            }
        }
    }
}
@Composable internal fun BPage(title: String, subtitle: String, back: () -> Unit, brand: Boolean = true,
    paper: Color = BPaper, footer: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(paper)) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(Color.White, CircleShape).clickable(role = Role.Button, onClick = back), contentAlignment = Alignment.Center) { FIcon(R.drawable.buyer_shared_back, 20.dp, "Kembali") }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (brand) FText("PEMBELI · PANENKU", 10f, 700, Color(0xFF36912E))
                FText(title, 18f, 700, line = 27f)
                if (subtitle.isNotBlank()) FText(subtitle, 12f, color = FMuted)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        footer?.invoke()
    }
}
@Composable internal fun BNotice(title: String, body: String, kind: String = "green") {
    val bg = when (kind) { "red" -> Color(0xFFFFF1F0); "orange" -> Color(0xFFFFF5E7); else -> Color(0xFFF3FBF2) }
    Row(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FIcon(when(kind) { "red" -> R.drawable.buyer_alert; "orange" -> R.drawable.buyer_shared_info_warning; else -> R.drawable.buyer_info },18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { FText(title,14f,600,line=18f); FText(body,12f,color=FMuted) }
    }
}
@Composable internal fun BItems(items: List<JsonObject>, photos: List<JsonObject> = emptyList()) {
    items.forEach { item ->
        val title = item.text("productNameSnapshot").ifBlank { item.text("name") }
        val image = item.text("photoAccessUrl").ifBlank { photos.firstOrNull { it.text("id") == item.text("productId") }?.text("photoAccessUrl").orEmpty() }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (image.isNotBlank()) FPhoto(image,title,Modifier.size(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FText(title,13f,600); FText("${item.text("quantityKg")} kg × ${money(item.long("pricePerKgSnapshot").takeIf { it != 0L } ?: item.long("pricePerKg"))}/kg",10f,color=FMuted)
            }
            FText(money(item.long("itemSubtotal")),12f,600)
        }
    }
}
@Composable internal fun BTotals(order: JsonObject, title: String = "Rincian total") {
    FCard(title) {
        FKeyValue("Subtotal produk",money(order.long("subtotalItems")))
        FKeyValue("Ongkos kirim petani",money(order.long("deliveryFee")))
        FRule(); FKeyValue("Total pesanan",money(order.long("totalPayment")),strong=true,size=14f)
    }
}
@Composable internal fun BSummary(order: JsonObject, photos: List<JsonObject>) {
    FCard("#${shortId(order.text("orderId"))}") {
        FBadge(buyerStatus(order.text("orderStatus")))
        FText(order.text("storeName"),12f,color=FMuted)
        BItems(order.list("items"),photos); FRule()
        FKeyValue("Subtotal produk",money(order.long("subtotalItems")))
        FKeyValue("Ongkir dibayar",money(order.long("deliveryFee")))
        FKeyValue("Total pesanan",money(order.long("totalPayment")),true)
    }
}
internal fun buyerStatus(value: String) = when(value) {
    "menunggu_petani" -> "Menunggu konfirmasi petani"; "menunggu_pembayaran" -> "Menunggu pembayaran"
    "verifikasi_pembayaran" -> "Verifikasi pembayaran"; "diproses" -> "Sedang disiapkan"
    "diantar" -> "Diantar oleh petani"; "siap_diambil" -> "Siap diambil"; "menunggu_penerimaan" -> "Menunggu penerimaan"
    "bukti_ditolak" -> "Bukti ditolak"; "menunggu_verifikasi" -> "Menunggu verifikasi"; "selesai" -> "Selesai"; else -> value.replace('_',' ')
}
internal fun effectiveDate(o: JsonObject) = o.obj("effectiveSchedule").text("scheduledDate").ifBlank { o.text("scheduledDate") }
internal fun effectiveSlot(o: JsonObject) = o.obj("effectiveSchedule").text("scheduledTimeSlot").ifBlank { o.text("scheduledTimeSlot") }
internal fun canUploadBuyerProof(o: JsonObject, now: Instant = Instant.now()): Boolean =
    o.text("orderStatus") == "menunggu_pembayaran" && o.text("paymentStatus") in setOf("belum_dibayar","bukti_ditolak") && o.obj("pendingScheduleChange").text("id").isBlank() &&
        runCatching { Instant.parse(o.text("expiresAt")).isAfter(now) }.getOrDefault(false)
internal fun canRequestBuyerRefund(o: JsonObject, refunds: List<JsonObject>, now: Instant = Instant.now()): Boolean {
    if (o.text("paymentStatus") != "terverifikasi" || o.long("totalPayment") <= 0) return false
    if (refunds.any { it.text("orderId") == o.text("orderId") && it.text("status") in setOf("diajukan","disetujui","menunggu_konfirmasi_dana","dieskalasi","selesai") }) return false
    if (o.text("orderStatus") == "gagal_dipenuhi") return true
    return o.text("orderStatus") == "selesai" && runCatching {
        val received = Instant.parse(o.text("receivedAt")); !now.isBefore(received) && !now.isAfter(received.plusSeconds(86400))
    }.getOrDefault(false)
}

@Composable internal fun BButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, height: Dp = 44.dp) =
    FButton(label,onClick,modifier,primary,enabled,height=height,font=14f)

@Composable internal fun BDateField(value: String, choices: List<Pair<String,String>>, onSelect: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(12.dp)).border(1.dp,FBorder,RoundedCornerShape(12.dp))
            .clickable(role=Role.Button){expanded=true}.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            FIcon(R.drawable.buyer_calendar,20.dp);FText(value.ifBlank{"Pilih tanggal"},13f,600,modifier=Modifier.weight(1f))
            FIcon(R.drawable.buyer_shared_chevron,14.dp)
        }
        DropdownMenu(expanded,{expanded=false}) { choices.forEach { (id,label)->DropdownMenuItem(text={FText(label,13f)},onClick={expanded=false;onSelect(id)}) } }
    }
}
