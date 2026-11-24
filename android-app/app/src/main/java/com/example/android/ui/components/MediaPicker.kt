package id.panenku.app.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pemilihan lokal. Tidak mengunggah berkas ke server. */
@Composable
fun MediaPicker(title: String, initialUri: String? = null, onPicked: (String) -> Unit = {}) {
    val context = LocalContext.current
    var filename by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val metadata = runCatching { context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else "Berkas dipilih"
                name to size
            } }.getOrNull()
            if (metadata?.second == null || metadata.second!! > 5L * 1024 * 1024) {
                error = "Pilih JPG/PNG berukuran maksimal 5 MB dengan ukuran berkas yang dapat dibaca."
            } else {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                filename = metadata.first
                error = null
                onPicked(uri.toString())
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(title, { launcher.launch(arrayOf("image/jpeg", "image/png")) })
        if (filename.isNotEmpty() || initialUri != null) Text(filename.ifEmpty { "Berkas lokal dipilih" }, style = MaterialTheme.typography.bodySmall)
        Text("JPG/PNG · maksimal 5 MB · hanya pratinjau lokal", style = MaterialTheme.typography.bodySmall)
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
fun LocalPhoto(uri: String?, fallbackKey: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            if (uri == null) null else runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 900 || bounds.outHeight / sample > 900) sample *= 2
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            }.getOrNull()
        }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Foto yang dipilih", modifier, contentScale = ContentScale.Crop)
    else if (fallbackKey != null) ProductImage(fallbackKey, modifier)
    else Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(Icons.Outlined.UploadFile, "Pratinjau berkas belum tersedia", Modifier.size(48.dp))
    }
}
