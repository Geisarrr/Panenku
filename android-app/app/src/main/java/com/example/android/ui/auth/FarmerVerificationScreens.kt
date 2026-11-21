package id.panenku.app.ui.auth

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import id.panenku.app.ui.theme.PanenkuColors

@Composable fun OpeningVerificationScreen(busy: Boolean, onSubmit: (FarmerVerificationInput) -> Unit,
    onLogin: () -> Unit, onBack: () -> Unit, accountCreated: Boolean = false) {
    var store by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var area by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var filename by rememberSaveable { mutableStateOf("") }
    var fileError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val resolver = context.contentResolver
                require(resolver.getType(uri) in setOf("image/jpeg", "image/png")) { "Pilih gambar JPG atau PNG." }
                var name = "Dokumen dipilih"
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val size = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (size >= 0 && !cursor.isNull(size)) require(cursor.getLong(size) <= 5L * 1024 * 1024) { "Ukuran gambar maksimal 5 MB." }
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) name = cursor.getString(index).orEmpty()
                    }
                }
                runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                filename = name; photo = uri.toString(); fileError = null
            } catch (e: Exception) { fileError = e.message ?: "Gambar belum dapat dibaca." }
        }
    }
    OpeningLayout {
        OpeningStep(3, onBack, busy, total = 3)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(22.dp))
            Text("Verifikasi petani", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF398D2D))
            Spacer(Modifier.height(14.dp))
            Text("Verifikasi identitas dan toko", fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = OpeningForest)
            Spacer(Modifier.height(8.dp))
            Text("Produk hanya dapat diterbitkan setelah identitas petani disetujui admin.", fontSize = 12.sp, lineHeight = 17.sp, color = PanenkuColors.Muted)
            Spacer(Modifier.height(30.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OpeningInput("Nama toko tani", store, { store = it.take(255) }, "Contoh: Kebun Pak Darto", enabled = !busy)
                OpeningInput("Lokasi toko", location, { location = it.take(2000) }, "Kecamatan, kota/kabupaten", enabled = !busy)
                OpeningInput("Area pengantaran", area, { area = it.take(2000) }, "Contoh: Lowokwaru, Blimbing", enabled = !busy)
            }
            Spacer(Modifier.height(16.dp))
            VerificationDocumentCard(photo != null, filename, !busy) { picker.launch(arrayOf("image/jpeg", "image/png")) }
            if (fileError != null) Text(fileError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF5E7)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Proses verifikasi", color = Color(0xFF92541B), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(if (accountCreated) "Akun sudah dibuat. Lengkapi pengajuan yang belum terkirim." else "Admin akan memeriksa data Anda.",
                    color = Color(0xFFA46C37), fontSize = 10.sp, lineHeight = 15.sp)
            }
            Spacer(Modifier.height(20.dp))
            OpeningAction("Kirim Pendaftaran Petani", store.isNotBlank() && location.isNotBlank() && area.isNotBlank() && photo != null, busy) {
                onSubmit(FarmerVerificationInput(store.trim(), location.trim(), area.trim(), photo!!))
            }
            Spacer(Modifier.height(24.dp))
        }
        OpeningFooter("Sudah memiliki akun?", "Masuk", !busy, onLogin)
        Spacer(Modifier.height(14.dp))
    }
}

@Composable internal fun VerificationDocumentCard(selected: Boolean, filename: String, enabled: Boolean, onChoose: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8FBF7))
        .clickable(enabled = enabled, onClick = onChoose).testTag("verification-photo")) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(Color(0xFFBCC9B7), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(OpeningMint), contentAlignment = Alignment.Center) {
                    Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.PhotoCamera, null, Modifier.size(21.dp), tint = Color(0xFF36902D))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Unggah foto KTP", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3A443A))
                    Text("Gunakan dokumen contoh. JPG/PNG maks. 5 MB.", fontSize = 10.sp, lineHeight = 14.sp, color = PanenkuColors.Muted)
                }
            }
            Text(if (selected) filename else "Pilih foto", fontSize = 11.sp, color = if (selected) OpeningForest else Color(0xFF8B978B),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White).padding(10.dp))
        }
    }
}

@Composable fun OpeningWaitingScreen(busy: Boolean, onLogin: () -> Unit) {
    OpeningLayout {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 72.dp, bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Verifikasi petani", color = Color(0xFF467D31), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(34.dp))
            Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color(0xFFEDF5E7)); drawCircle(Color(0xFFE0EED6), radius = size.minDimension * 0.40f)
                    drawCircle(Color.White, radius = size.minDimension * 0.29f)
                    drawCircle(Color(0xFFA5C991), radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.14f))
                    drawCircle(Color(0xFFA5C991), radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.14f, size.height * 0.86f))
                }
                Box(Modifier.size(64.dp)) {
                    Icon(Icons.Outlined.Description, null, Modifier.size(49.dp).align(Alignment.TopStart), tint = Color(0xFF4B8337))
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(28.dp).align(Alignment.BottomEnd).background(Color.White, CircleShape), tint = Color(0xFF4B8337))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("Menunggu verifikasi", color = Color(0xFF243B22), fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 35.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text("Pengajuan Anda sudah diterima. Admin Panenku sedang memeriksa data identitas dan toko Anda.", fontSize = 14.sp,
                lineHeight = 22.sp, color = Color(0xFF697267), textAlign = TextAlign.Center)
            Spacer(Modifier.height(38.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF0F7EB))
                .border(1.dp, Color(0xFFE1ECD9), RoundedCornerShape(14.dp)).padding(16.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE1EED6)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Email, null, Modifier.size(21.dp), tint = Color(0xFF4A7E34))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Pemberitahuan lewat email", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF294126))
                    Text("Gunakan email yang didaftarkan untuk masuk kembali dan memeriksa status pengajuan.", fontSize = 12.sp,
                        lineHeight = 17.sp, color = Color(0xFF66745F))
                }
            }
            Spacer(Modifier.height(26.dp))
            Button(onClick = onLogin, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A7F32))) {
                Text("Kembali ke halaman masuk", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
