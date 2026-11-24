package id.panenku.app.ui.auth

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.panenku.app.R
import id.panenku.app.data.Role
import id.panenku.app.ui.components.*
import id.panenku.app.ui.theme.*

@Composable
fun SplashScreen(variant: Int = 0) {
    Box(Modifier.fillMaxSize().background(if (variant == 1) Color(0xFFE8F3E2) else MaterialTheme.colorScheme.background)) {
        if (variant != 2) {
            Box(Modifier.size(230.dp).align(Alignment.TopEnd).offset(x = 120.dp, y = 20.dp).clip(CircleShape).background(Color(0xFFE8F3E2)))
            Box(Modifier.size(340.dp).align(Alignment.BottomStart).offset(x = (-170).dp, y = 150.dp).clip(CircleShape).background(Color(0xFFE8F3E2)))
        }
        if (variant == 0) Image(painterResource(R.drawable.logo_panenku), "Panenku", Modifier.width(180.dp).align(Alignment.Center))
    }
}
@Composable
fun RoleScreen(onContinue: (Role) -> Unit, onLogin: (Role) -> Unit, onAdmin: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(Role.BUYER) }
    Page("Buat akun baru", "Langkah 1 dari 2") {
        item { LinearProgressIndicator(progress = { 0.33f }, modifier = Modifier.fillMaxWidth()) }
        item { Text("Anda ingin menggunakan Panenku sebagai?", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Pilih peran sesuai kebutuhan. Peran tidak dapat diubah setelah akun diverifikasi.", style = MaterialTheme.typography.bodyMedium) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Role.BUYER, Role.FARMER).forEach { role ->
                    OutlinedCard(onClick = { selected = role }, colors = CardDefaults.outlinedCardColors(
                        containerColor = if (selected == role) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, if (selected == role) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (role == Role.BUYER) Icons.Outlined.ShoppingCart else Icons.Outlined.Inventory2, null)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(role.label, style = MaterialTheme.typography.titleMedium)
                                Text(if (role == Role.BUYER) "Belanja hasil tani langsung dari petani lokal." else "Jual hasil panen dan kelola pesanan toko.", style = MaterialTheme.typography.bodySmall)
                            }
                            RadioButton(selected == role, onClick = null)
                        }
                    }
                }
            }
        }
        item { PrimaryButton("Lanjutkan", { onContinue(selected) }) }
        item { TextButton(onClick = { onLogin(selected) }, modifier = Modifier.fillMaxWidth()) { Text("Sudah memiliki akun? Masuk") } }
        item { TextButton(onClick = onAdmin, modifier = Modifier.fillMaxWidth()) { Text("Akses administrator") } }
    }
}
@Composable
fun LoginScreen(role: Role, onBack: () -> Unit, onLogin: () -> Unit, onRegister: () -> Unit, onMessage: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val admin = role == Role.ADMIN
    Page(if (admin) "Akses administrator" else "Masuk sebagai ${role.label.lowercase()}",
        if (admin) "Khusus pengelola Panenku" else "Panen segar, langsung dari petani.", onBack, admin) {
        item { Image(painterResource(R.drawable.logo_panenku), "Logo Panenku", Modifier.width(100.dp).height(90.dp)) }
        item { Text(if (admin) "Panenku Admin" else "Selamat Datang Kembali!", style = MaterialTheme.typography.headlineSmall) }
        if (role == Role.FARMER) item { Notice("Akun petani", "Kelola hasil panen, stok, dan pesanan toko. Produk dapat dijual setelah identitas disetujui admin.") }
        item { Notice("Mode demo", "Masuk dengan email/nomor HP dan kata sandi contoh. Akun tidak diautentikasi ke server.", Tone.INFO) }
        item { Panel {
            Field(if (admin) "Email administrator" else "Email atau nomor HP", email, { email = it }, "contoh@email.com", keyboard = KeyboardType.Email,
                error = if (submitted && email.isBlank()) "Email atau nomor HP wajib diisi." else null)
            Field("Kata sandi", password, { password = it }, "Masukkan kata sandi", password = true,
                error = if (submitted && password.length < 8) "Gunakan minimal 8 karakter." else null)
            if (!admin) TextButton({ onMessage("Pemulihan kata sandi memerlukan layanan akun; belum tersedia pada demo.") }, Modifier.align(Alignment.End)) { Text("Lupa kata sandi?") }
            PrimaryButton(if (admin) "Masuk sebagai admin" else "Masuk", {
                submitted = true
                if (email.isNotBlank() && password.length >= 8) onLogin()
            })
            if (!admin) SecondaryButton("Lanjutkan dengan Google", { onMessage("Login Google belum dihubungkan. Gunakan formulir demo.") })
        } }
        if (!admin) item { TextButton(onClick = onRegister, Modifier.fillMaxWidth()) { Text("Belum memiliki akun? Daftar sekarang") } }
        if (admin) item { Notice("Tidak ada pendaftaran admin publik", "Akun hanya diberikan oleh pengelola berwenang.") }
    }
}
@Composable
fun RegistrationScreen(role: Role, onBack: () -> Unit, onNext: (Map<String, String>) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var agreed by rememberSaveable { mutableStateOf(false) }
    val valid = name.isNotBlank() && phone.length >= 10 && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() && password.length >= 8 && agreed && (role != Role.BUYER || address.isNotBlank())
    Page(if (role == Role.FARMER) "Lengkapi identitas Anda" else "Bergabung dengan Panenku", "Langkah 2 dari ${if (role == Role.FARMER) 3 else 2}", onBack) {
        item { LinearProgressIndicator(progress = { if (role == Role.FARMER) 0.66f else 1f }, modifier = Modifier.fillMaxWidth()) }
        item { Panel {
            Field("Nama lengkap", name, { name = it }, "Sesuai identitas")
            Field("Nomor HP", phone, { phone = it.filter(Char::isDigit).take(15) }, "08xxxxxxxxxx", keyboard = KeyboardType.Phone)
            Field("Email", email, { email = it }, "contoh@email.com", keyboard = KeyboardType.Email)
            if (role == Role.BUYER) Field("Alamat utama", address, { address = it }, "Nama jalan, kecamatan, kota", multiline = true)
            Field("Kata sandi", password, { password = it }, "Minimal 8 karakter", password = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(agreed, { agreed = it }); Text("Saya menyetujui Syarat & Ketentuan dan Kebijakan Privasi Panenku.", style = MaterialTheme.typography.bodySmall)
            }
            PrimaryButton(if (role == Role.FARMER) "Lanjut Verifikasi" else "Buat Akun Pembeli", {
                onNext(mapOf("Nama" to name, "Telepon" to phone, "Email" to email, "Alamat" to address))
            }, valid)
        } }
    }
}
@Composable
fun VerificationForm(onBack: () -> Unit, onSubmit: (Map<String, String>) -> Unit, rejected: Boolean = false) {
    var shop by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var area by rememberSaveable { mutableStateOf("") }
    var ktp by rememberSaveable { mutableStateOf<String?>(null) }
    Page(if (rejected) "Status verifikasi" else "Verifikasi identitas dan toko", if (rejected) "Pengajuan #VER-018 · Petani Contoh" else "Langkah 3 dari 3", onBack) {
        if (rejected) item { Notice("Pengajuan ditolak · Perlu perbaikan", "Foto dokumen kurang jelas. Unggah foto baru dengan nama dan alamat terbaca.", Tone.ERROR) }
        else item { LinearProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxWidth()) }
        item { Panel(if (rejected) "Perbaiki pengajuan" else "Verifikasi petani") {
            Field("Nama toko tani", shop, { shop = it }, "Contoh: Kebun Pak Darto")
            Field("Lokasi toko", location, { location = it }, "Kecamatan, kota/kabupaten")
            Field("Area pengantaran", area, { area = it }, "Contoh: Kota Malang, maksimal 10 km")
            MediaPicker(if (rejected) "Unggah foto KTP pengganti" else "Unggah foto KTP", ktp, { ktp = it })
            Notice("Proses verifikasi", "Admin akan memeriksa data Anda. Pada demo, gunakan berkas contoh.", Tone.WARNING)
            PrimaryButton(if (rejected) "Pilih dokumen & kirim ulang" else "Kirim Pendaftaran Petani", {
                onSubmit(mapOf("Nama toko" to shop, "Lokasi" to location, "Area" to area, "KTP" to ktp.orEmpty()))
            }, shop.isNotBlank() && location.isNotBlank() && area.isNotBlank() && ktp != null)
        } }
        if (rejected) item { Panel("Alur verifikasi") { Timeline(listOf("Perbaikan diperlukan" to "Unggah data yang diminta admin", "Menunggu pemeriksaan" to "Admin meninjau dokumen", "Disetujui" to "Syarat verifikasi untuk penerbitan produk terpenuhi")) } }
    }
}
@Composable
fun VerificationWaitingScreen(onLogin: () -> Unit) {
    Page("Verifikasi petani") {
        item { Column(Modifier.fillMaxWidth().padding(vertical = 50.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(Icons.Outlined.PendingActions, null, Modifier.size(76.dp), tint = MaterialTheme.colorScheme.secondary)
            Text("Menunggu verifikasi", style = MaterialTheme.typography.headlineSmall)
            Text("Pengajuan demo sudah dicatat. Pada layanan lengkap, admin akan memeriksa identitas dan toko Anda.")
            Notice("Pemberitahuan lewat email", "Status verifikasi memerlukan integrasi layanan akun dan backend.")
            PrimaryButton("Kembali ke halaman masuk", onLogin)
        } }
    }
}



@Preview(showBackground = true, widthDp = 375, heightDp = 862)
@Composable private fun RolePreview() { PanenkuTheme { RoleScreen({}, {}, {}) } }

@Preview(showBackground = true, widthDp = 375, heightDp = 862)
@Composable private fun LoginPreview() { PanenkuTheme { LoginScreen(Role.FARMER, {}, {}, {}, {}) } }

@Preview(showBackground = true, widthDp = 375, heightDp = 862)
@Composable private fun SplashPreview() { PanenkuTheme { SplashScreen() } }

@Preview(showBackground = true, widthDp = 375, heightDp = 862)
@Composable private fun SplashGreenPreview() { PanenkuTheme { SplashScreen(1) } }

@Preview(showBackground = true, widthDp = 375, heightDp = 862)
@Composable private fun SplashBlankPreview() { PanenkuTheme { SplashScreen(2) } }

@Preview(showBackground = true, widthDp = 375, heightDp = 862)
@Composable private fun SplashDecorPreview() { PanenkuTheme { SplashScreen(3) } }
