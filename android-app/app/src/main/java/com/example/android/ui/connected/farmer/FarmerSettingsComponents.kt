package id.panenku.app.ui.connected.farmer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import id.panenku.app.R

@Composable internal fun FSettingsPage(title: String, subtitle: String, back: () -> Unit,
    gap: Dp = 16.dp, footer: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        FHeader(title, subtitle, back, titleWeight = 700)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(gap), content = content)
        if (footer != null) Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) { footer() }
    }
}
@Composable internal fun FSettingsNotice(title: String, body: String, kind: String = "green") {
    val bg = when (kind) { "red" -> Color(0xFFFFF0EE); "orange" -> Color(0xFFFFF5E7); else -> Color(0xFFF3FBF2) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FIcon(when (kind) { "red" -> R.drawable.farmer_error_info; "orange" -> R.drawable.farmer_refund_info; else -> R.drawable.farmer_check_info }, 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            FText(title, 14f, 600); FText(body, 12f, color = FMuted)
        }
    }
}
@Composable internal fun FSettingsRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(32.dp).heightIn(min = 36.dp), contentAlignment = Alignment.Center) { FIcon(icon, 22.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FText(title, 14f, 600); FText(subtitle, 12f, color = FMuted)
        }
        FIcon(R.drawable.farmer_chevron, 18.dp)
    }
}
@Composable internal fun FUploadCard(title: String, subtitle: String, icon: Int = R.drawable.farmer_upload, click: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFCFCFC))
        .drawWithContent { drawContent(); drawRoundRect(FBorder, cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),3.dp.toPx())))) }.clickable(role = Role.Button, onClick = click).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FIcon(icon, 26.dp); FText(title, 14f, 600, align = TextAlign.Center); FText(subtitle, 12f, color = FMuted, align = TextAlign.Center)
    }
}
/** Read metadata and validate before exposing a persistable URI to any upload action. */
@Composable internal fun rememberFarmerImagePicker(onSelected: (String, String, Long) -> Unit, onError: (String) -> Unit): () -> Unit {
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
