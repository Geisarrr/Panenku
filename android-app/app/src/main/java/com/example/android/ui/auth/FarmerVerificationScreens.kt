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
import id.panenku.app.R
import androidx.compose.ui.res.painterResource
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
    SellerLayout(inset = 23.dp) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SellerStep(3, onBack, busy)
            SellerHeading("Verifikasi petani", "Verifikasi identitas dan toko",
                "Produk hanya dapat diterbitkan setelah identitas petani disetujui admin.")
            Column(verticalArrangement = Arrangement.spacedBy(13.5.dp)) {
                SellerInput("Nama toko tani", store, { store = it.take(255) }, "Contoh: Kebun Pak Darto", enabled = !busy)
                SellerInput("Lokasi toko", location, { location = it.take(2000) }, "Kecamatan, kota/kabupaten", enabled = !busy)
            }
            Spacer(Modifier.height(12.dp))
            VerificationDocumentCard(photo != null, filename, !busy) { picker.launch(arrayOf("image/jpeg", "image/png")) }
            if (fileError != null) SellerText(fileError!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.5.dp)).background(Color(0xFFFFF6E9)).padding(10.2.dp),
                verticalArrangement = Arrangement.spacedBy(4.3.dp)) {
                SellerText("Proses verifikasi", 10.2f, true, Color(0xFF8A551E))
                SellerText(if (accountCreated) "Akun sudah dibuat. Lengkapi pengajuan yang belum terkirim." else "Admin akan memeriksa data Anda",
                    8.5f, color = Color(0xFF8A551E))
            }
            Spacer(Modifier.height(14.5.dp))
            SellerAction("Kirim Pendaftaran Petani", busy = busy, labelSize = 11.93f) {
                if (store.isBlank() || location.isBlank() || photo == null) {
                    fileError = "Lengkapi data toko dan pilih foto dokumen terlebih dahulu."
                } else {
                    fileError = null
                    onSubmit(FarmerVerificationInput(store.trim(), location.trim(), "", photo!!))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        SellerFooter("Sudah memiliki akun?", "Masuk", busy, onLogin)
    }
}

@Composable internal fun VerificationDocumentCard(selected: Boolean, filename: String, enabled: Boolean, onChoose: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8FBF7))
        .clickable(enabled = enabled, onClick = onChoose).testTag("verification-photo")) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(Color(0xFFBCC9B7), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
        }
        Column(Modifier.padding(10.2.dp), verticalArrangement = Arrangement.spacedBy(9.4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.7.dp).clip(RoundedCornerShape(9.4.dp)).background(Color(0xFFE7F5E5)), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.seller_camera), null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(9.4.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.4.dp)) {
                    SellerText("Unggah foto KTP", 11.93f, true, Color(0xFF404040))
                    SellerText("Gunakan dokumen contoh. JPG/PNG maks. 5 MB.", 10.2f)
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 27.7.dp).clip(RoundedCornerShape(9.4.dp)).background(Color.White)
                .padding(horizontal = 9.dp, vertical = 6.dp)) {
                SellerText(if (selected) filename else "Pilih foto", 10.2f)
            }
        }
    }
}

@Composable fun OpeningWaitingScreen(busy: Boolean, onLogin: () -> Unit) {
    SellerLayout(inset = 24.dp) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 54.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            SellerText("Verifikasi petani", 13f, true, Color(0xFF477B35), line = 16f)
            Spacer(Modifier.height(34.dp))
            Image(painterResource(R.drawable.seller_waiting), null, Modifier.size(132.dp))
            Spacer(Modifier.height(21.dp))
            SellerText("Menunggu verifikasi", 27f, true, Color(0xFF20351F), line = 33f, align = TextAlign.Center)
            Spacer(Modifier.height(13.dp))
            SellerText("Pengajuan Anda sudah diterima. Admin Panenku sedang memeriksa data identitas dan toko Anda.",
                14f, color = Color(0xFF515B50), line = 22f, align = TextAlign.Center)
            Spacer(Modifier.height(38.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFFF0F7EB))
                .border(1.dp, Color(0xFFE1ECD9), RoundedCornerShape(11.dp)).heightIn(min = 108.dp).padding(15.dp),
                verticalAlignment = Alignment.Top) {
                Image(painterResource(R.drawable.seller_mail), null, Modifier.size(38.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SellerText("Pemberitahuan lewat email", 13.3f, true, Color(0xFF20351F))
                    SellerText("Gunakan email yang didaftarkan untuk masuk kembali dan memeriksa status pengajuan.",
                        12.36f, color = Color(0xFF515B50), line = 15.5f)
                }
            }
            Spacer(Modifier.height(24.dp))
            SellerAction("Kembali ke halaman masuk", busy = busy, dark = true, onClick = onLogin)
        }
    }
}
