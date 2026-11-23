package id.panenku.app.ui.connected.farmer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val FPaper = Color(0xFFFAFAFA)
internal val FGreen = Color(0xFF45AB3C)
internal val FForest = Color(0xFF275823)
internal val FInk = Color(0xFF1C1C1C)
internal val FMuted = Color(0xFF727272)
internal val FBorder = Color(0xFFECEFEC)
internal val LocalFarmerInsets = staticCompositionLocalOf<WindowInsets?> { null }
internal val LocalFarmerPhoto = staticCompositionLocalOf<((String) -> Painter?)?> { null }
@OptIn(ExperimentalTextApi::class)
internal val FarmerFont = FontFamily(*listOf(400, 500, 600, 700).map {
    Font(R.font.seller_inter, weight = FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it)))
}.toTypedArray())

/** Design coordinates are 375 px wide; scale typography and geometry together on phones. */
@Composable internal fun FarmerTheme(content: @Composable () -> Unit) {
    val device = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(FPaper), contentAlignment = Alignment.TopCenter) {
        val scale = maxWidth.value.coerceAtMost(480f) / 375f
        CompositionLocalProvider(LocalDensity provides Density(device.density * scale, device.fontScale.coerceAtLeast(1f))) {
            ProvideTextStyle(TextStyle(fontFamily = FarmerFont, color = FInk, fontSize = 14.sp,
                letterSpacing = 0.sp, platformStyle = PlatformTextStyle(includeFontPadding = false))) {
                Box(Modifier.widthIn(max = 375.dp).fillMaxWidth().fillMaxHeight()
                    .windowInsetsPadding(LocalFarmerInsets.current ?: WindowInsets.safeDrawing).imePadding()) { content() }
            }
        }
    }
}

@Composable internal fun FText(text: String, size: Float = 12f, weight: Int = 400, color: Color = FInk,
    modifier: Modifier = Modifier, line: Float = size * 1.25f, maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null) {
    Text(text, modifier, color, fontSize = size.sp, fontWeight = FontWeight(weight), fontFamily = FarmerFont,
        letterSpacing = 0.sp, lineHeight = line.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align)
}
@Composable internal fun FIcon(id: Int, size: Dp = 20.dp, description: String? = null, tint: Color? = null) {
    Image(painterResource(id), description, Modifier.size(size), colorFilter = tint?.let { ColorFilter.tint(it) })
}
@Composable internal fun FHeader(title: String, subtitle: String, back: () -> Unit, titleWeight: Int = 600) {
    Row(Modifier.fillMaxWidth().heightIn(min = 73.dp).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White).border(1.dp, FBorder, CircleShape)
            .clickable(role = Role.Button, onClick = back), contentAlignment = Alignment.Center) {
            FIcon(R.drawable.farmer_back, description = "Kembali")
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            FText(title, 18f, titleWeight, line = 27f, maxLines = 2)
            if (subtitle.isNotBlank()) FText(subtitle, 12f, color = FMuted, line = 15f, maxLines = 1)
        }
    }
}
@Composable internal fun FPage(title: String, subtitle: String = "", back: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(FPaper)) {
        FHeader(title, subtitle, back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
@Composable internal fun FCard(title: String? = null, modifier: Modifier = Modifier,
    padding: Dp = 14.dp, gap: Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
        .border(1.dp, FBorder, RoundedCornerShape(16.dp)).padding(padding), verticalArrangement = Arrangement.spacedBy(gap)) {
        if (title != null) FText(title, 14f, 600, line = 18f)
        content()
    }
}
@Composable internal fun FButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = true, enabled: Boolean = true, height: Dp = 44.dp, font: Float = 14f) {
    val shape = RoundedCornerShape(1000.dp)
    Box(modifier.fillMaxWidth().heightIn(min = height).clip(shape)
        .background(if (!enabled && primary) Color(0xFFE1E5E0) else if (primary) FGreen else Color.White)
        .border(1.dp, if (primary && enabled) Color(0xFF2C6F26) else if (!enabled && primary) Color(0xFFD6DBD5) else Color(0xFFDFE3DF), shape)
        .clickable(enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = if (height < 40.dp) 6.dp else 8.dp),
        contentAlignment = Alignment.Center) {
        FText(label, font, 600, if (!enabled && primary) Color(0xFF929B90) else if (primary) Color.White else FInk,
            line = if (height < 40.dp) 16f else 20f, align = TextAlign.Center)
    }
}
@Composable internal fun FNotice(title: String, body: String = "", warning: Boolean = false) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (warning) Color(0xFFFFF5E7) else Color(0xFFF3FBF2)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        FIcon(if (warning) R.drawable.farmer_info_warning else R.drawable.farmer_info, 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            FText(title, 13f, 600, line = 18f)
            if (body.isNotBlank()) FText(body, 12f, color = FMuted, line = 15f)
        }
    }
}
@Composable internal fun FBadge(label: String, kind: String = "green", compact: Boolean = false) {
    val (background, ink) = when (kind) {
        "orange" -> Color(0xFFFFF5E7) to Color(0xFFAD6819)
        "blue" -> Color(0xFFE7F3FF) to Color(0xFF026ECC)
        "red" -> Color(0xFFFCECE8) to Color(0xFFA63A28)
        "gray" -> Color(0xFFF1F1F1) to Color(0xFF727272)
        else -> Color(0xFFF3FBF2) to Color(0xFF36912E)
    }
    Box(Modifier.clip(RoundedCornerShape(if (compact) 5.dp else 6.dp)).background(background)
        .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 4.dp else 5.dp)) {
        FText(label, if (compact) 8.72f else 12f, 600, ink)
    }
}
@Composable internal fun FRule() = HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 1.dp)
@Composable internal fun FKeyValue(label: String, value: String, strong: Boolean = false, size: Float = 12f) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        FText(label, size, color = FMuted, modifier = Modifier.widthIn(max = 150.dp), line = if (size >= 14f) 20f else 15f)
        FText(value, size, if (strong) 700 else 500, if (strong) FForest else FInk,
            modifier = Modifier.weight(1f), align = TextAlign.End, line = if (size >= 14f) 20f else 15f)
    }
}
@Composable internal fun FField(label: String, value: String, onChange: (String) -> Unit,
    placeholder: String = "", multiline: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, enabled: Boolean = true) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FText(label, 12f, color = Color(0xFF3F3F46), line = 16f)
        BasicTextField(value, onChange, Modifier.fillMaxWidth().testTag("farmer-input-$label"), enabled = enabled,
            singleLine = !multiline, minLines = if (multiline) 2 else 1,
            textStyle = TextStyle(fontFamily = FarmerFont, fontSize = 13.sp, lineHeight = 20.sp,
                color = Color(0xFF18181B), platformStyle = PlatformTextStyle(includeFontPadding = false)),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard), cursorBrush = SolidColor(FGreen),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .border(1.dp, Color(0xFFE4E4E7), RoundedCornerShape(12.dp)).heightIn(min = if (multiline) 60.dp else 40.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) FText(placeholder, 13f, color = Color(0xFF9A9A9A), line = 20f)
                    inner()
                }
            })
    }
}
@Composable internal fun FPhoto(url: String, description: String, modifier: Modifier = Modifier) {
    val supplied = LocalFarmerPhoto.current?.invoke(url)
    Box(modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFFF6F8F4)), contentAlignment = Alignment.Center) {
        when {
            supplied != null -> Image(supplied, description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            url.isNotBlank() -> AsyncImage(url, description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> FText("Belum ada foto", 10f, color = FMuted, modifier = Modifier.padding(6.dp), align = TextAlign.Center)
        }
    }
}
@Composable internal fun FDestination(icon: Int, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FIcon(icon, 22.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FText(title, 13f, 600, line = 18f); FText(subtitle, 12f, color = FMuted, line = 15f)
        }
        if (onClick != null) FIcon(R.drawable.farmer_chevron, 16.dp)
    }
}
internal fun money(value: Long) = id.panenku.app.data.rupiah(value).replace("Rp ", "Rp")
internal fun quantity(value: String): String = value.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: value.ifBlank { "0" }
internal fun JsonObject.qty(name: String) = quantity(text(name))
internal fun shortId(value: String) = if (value.length > 20) value.take(8).uppercase() else value
internal fun dateLabel(value: String, short: Boolean = false): String = runCatching {
    LocalDate.parse(value.take(10)).format(DateTimeFormatter.ofPattern(if (short) "d MMM yyyy" else "d MMMM yyyy", Locale("id", "ID")))
}.getOrDefault(value.ifBlank { "Belum dijadwalkan" })
internal fun dateTimeLabel(value: String): String = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Asia/Jakarta")).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH.mm", Locale("id", "ID")))
}.getOrDefault(value.ifBlank { "—" })
internal fun JsonObject.scheduleDate() = obj("effectiveSchedule").text("scheduledDate").ifBlank { text("scheduledDate") }
internal fun JsonObject.scheduleSlot() = obj("effectiveSchedule").text("scheduledTimeSlot").ifBlank { text("scheduledTimeSlot") }
internal fun displaySlot(value: String) = value.replace(':', '.').replace("-", "–")
internal fun JsonObject.orderItemsLabel() = list("items").joinToString(" + ") { "${it.text("productNameSnapshot")} ${it.qty("quantityKg")} kg" }
internal fun JsonObject.totalKg() = list("items").fold(java.math.BigDecimal.ZERO) { sum, row -> sum + (row.text("quantityKg").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) }.stripTrailingZeros().toPlainString()
internal fun orderLabel(status: String) = when (status) {
    "menunggu_petani" -> "Menunggu Petani"; "menunggu_pembayaran" -> "Menunggu Pembayaran"
    "verifikasi_pembayaran" -> "Verifikasi Pembayaran"; "diproses" -> "Diproses"; "diantar" -> "Diantar"
    "siap_diambil" -> "Siap Diambil"; "menunggu_penerimaan" -> "Menunggu Penerimaan"; "selesai" -> "Selesai"
    "dibatalkan" -> "Dibatalkan"; "kedaluwarsa" -> "Kedaluwarsa"; "gagal_dipenuhi" -> "Gagal Dipenuhi"
    else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
internal fun paymentLabel(status: String) = when (status) {
    "belum_dibayar" -> "Belum dibayar"; "menunggu_verifikasi" -> "Menunggu verifikasi"; "diterima", "terverifikasi" -> "Pembayaran diterima"
    "ditolak", "bukti_ditolak" -> "Bukti ditolak"; else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
internal fun canProposeSchedule(order: JsonObject) = order.text("orderStatus") in listOf("menunggu_petani", "menunggu_pembayaran") &&
    order.text("paymentStatus") == "belum_dibayar" && order.obj("pendingScheduleChange").text("id").isBlank() && order.list("history").none { it.text("toStatus") == "verifikasi_pembayaran" }
