package id.panenku.app.ui.connected.buyer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalContext
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
internal val LocalBuyerSharedInsets = staticCompositionLocalOf<WindowInsets?> { null }
internal val LocalBuyerPhoto = staticCompositionLocalOf<((String) -> Painter?)?> { null }
@OptIn(ExperimentalTextApi::class)
internal val BuyerFont = FontFamily(*listOf(400, 500, 600, 700).map {
    Font(R.font.buyer_inter, weight = FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it)))
}.toTypedArray())

@Composable internal fun FText(text: String, size: Float = 12f, weight: Int = 400, color: Color = FInk,
    modifier: Modifier = Modifier, line: Float = size * 1.25f, maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null) {
    Text(text, modifier, color, fontSize = size.sp, fontWeight = FontWeight(weight), fontFamily = BuyerFont,
        letterSpacing = 0.sp, lineHeight = line.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align)
}
@Composable internal fun FIcon(id: Int, size: Dp = 20.dp, description: String? = null, tint: Color? = null) {
    Image(painterResource(id), description, Modifier.size(size), colorFilter = tint?.let { ColorFilter.tint(it) })
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
        FIcon(if (warning) R.drawable.buyer_shared_info_warning else R.drawable.buyer_shared_info, 18.dp)
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
    placeholder: String = "", multiline: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, enabled: Boolean = true, showLabel: Boolean = true, round: Dp = 12.dp) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if(showLabel) FText(label, 12f, color = Color(0xFF3F3F46), line = 16f)
        BasicTextField(value, onChange, Modifier.fillMaxWidth().testTag("buyer-input-$label"), enabled = enabled,
            singleLine = !multiline, minLines = if (multiline) 2 else 1,
            textStyle = TextStyle(fontFamily = BuyerFont, fontSize = 13.sp, lineHeight = 20.sp,
                color = Color(0xFF18181B), platformStyle = PlatformTextStyle(includeFontPadding = false)),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard), cursorBrush = SolidColor(FGreen),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(round)).background(Color.White)
                    .border(1.dp, Color(0xFFE4E4E7), RoundedCornerShape(round)).heightIn(min = if (multiline) 60.dp else 40.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) FText(placeholder, 13f, color = Color(0xFF9A9A9A), line = 20f)
                    inner()
                }
            })
    }
}
@Composable internal fun FPhoto(url: String, description: String, modifier: Modifier = Modifier) {
    val supplied = LocalBuyerPhoto.current?.invoke(url)
    Box(modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFFF6F8F4)), contentAlignment = Alignment.Center) {
        when {
            supplied != null -> Image(supplied, description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            url.isNotBlank() -> AsyncImage(url, description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> FText("Belum ada foto", 10f, color = FMuted, modifier = Modifier.padding(6.dp), align = TextAlign.Center)
        }
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

@Composable internal fun FSelectField(label: String, value: String, choices: List<Pair<String, String>>, selected: String,
    onSelect: (String) -> Unit, enabled: Boolean = true, inlineCaret: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FText(label, 12f, color = Color(0xFF3F3F46), line = 16f)
        Box {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                .border(1.dp, Color(0xFFE4E4E7), RoundedCornerShape(12.dp)).clickable(enabled, role = Role.Button) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                FText(value.ifBlank { "Pilih $label" }, 13f, modifier = if (inlineCaret) Modifier else Modifier.weight(1f), line = 20f)
                if (inlineCaret) FText("▾", 10f, color = FInk, modifier = Modifier.padding(start = 6.dp)) else FText("⌄", 14f, color = FMuted)
            }
            DropdownMenu(expanded, { expanded = false }) {
                choices.forEach { (id, name) -> DropdownMenuItem(text = { FText(name, 13f, if (selected == id) 600 else 400) }, onClick = { expanded = false; onSelect(id) }) }
            }
        }
    }
}


@Composable internal fun FUploadCard(title: String, subtitle: String, icon: Int = R.drawable.buyer_shared_upload, click: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFCFCFC))
        .drawWithContent { drawContent(); drawRoundRect(FBorder, cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),3.dp.toPx())))) }.clickable(role = Role.Button, onClick = click).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FIcon(icon, 26.dp); FText(title, 14f, 600, align = TextAlign.Center); FText(subtitle, 12f, color = FMuted, align = TextAlign.Center)
    }
}
/** Read metadata and validate before exposing a persistable URI to any upload action. */
@Composable internal fun rememberBuyerImagePicker(onSelected: (String, String, Long) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val selected by rememberUpdatedState(onSelected); val error by rememberUpdatedState(onError)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val resolver = context.contentResolver
            require(resolver.getType(uri) in listOf("image/jpeg", "image/png")) { "Pilih gambar JPG atau PNG." }
            var name = "Gambar dipilih"; var size = -1L
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) { name = c.getString(0) ?: name; if (!c.isNull(1)) size = c.getLong(1) }
            }
            if (size < 0) resolver.openInputStream(uri)!!.use { input ->
                val b = ByteArray(8192); size = 0
                while (size <= 5L * 1024 * 1024) { val n = input.read(b); if (n < 0) break; size += n }
            }
            require(size in 1..5L * 1024 * 1024) { "Ukuran gambar harus 1 byte sampai 5 MB." }
            runCatching { resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            selected(uri.toString(), name, size)
        }.onFailure { error(it.message ?: "Gambar tidak dapat dibuka.") }
    }
    return { picker.launch(arrayOf("image/jpeg", "image/png")) }
}
internal fun shortFileSize(size: Long): String = if (size >= 1024*1024) String.format(java.util.Locale("id","ID"), "%.1f MB", size / 1048576.0) else "${size / 1024} KB"
