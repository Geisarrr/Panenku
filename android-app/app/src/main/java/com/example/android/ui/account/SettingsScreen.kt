package id.panenku.app.ui.account

import id.panenku.app.ui.components.*

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp


@Composable
fun ProfileScreen(onNavigate: (String) -> Unit, onLogout: () -> Unit, onInfo: (String) -> Unit, settings: Map<String, Map<String, String>> = emptyMap()) {
    Page("Profil & Toko", "Kelola toko dan layanan panenmu.") {
        item { Panel {
            Text("Darto Sobianto", style = MaterialTheme.typography.titleLarge)
            Text("darto@example.com · 0812 0000 0000", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(settings["shop"]?.get("Nama") ?: "Kebun Darto", style = MaterialTheme.typography.titleMedium)
            StatusBadge("Terverifikasi"); Text("Petani · Malang, Jawa Timur")
        } }
        item { Panel("Pengaturan toko") {
            MenuRow("Profil toko", "Nama, foto, dan deskripsi toko", Icons.Outlined.Storefront) { onNavigate("shop") }
            MenuRow("Lokasi kebun & pengambilan", "Lowokwaru, Kota Malang", Icons.Outlined.LocationOn) { onNavigate("service") }
            MenuRow("Layanan & area antar", "Ongkir Rp${settings["service"]?.get("Ongkir") ?: "2.500"} per pesanan", Icons.Outlined.LocalShipping) { onNavigate("service") }
            MenuRow("Jam layanan", settings["service"]?.get("Jam") ?: "Senin–Sabtu, 08.00–16.00 WIB", Icons.Outlined.Schedule) { onNavigate("service") }
        } }
        item { Panel("Pembayaran langsung") {
            MenuRow("Rekening bank petani", settings["bank"]?.let { "${it["Bank"]} · ${it["Nama"]}" } ?: "Atur rekening penerima pembayaran", Icons.Outlined.AccountBalance) { onNavigate("bank") }
            MenuRow("QRIS toko", "Kelola QRIS untuk pembayaran ke petani", Icons.Outlined.QrCode2) { onNavigate("qris") }
            Notice("Dana masuk langsung ke rekeningmu", "Panenku tidak menyimpan saldo. Verifikasi dana masuk dan bukti pembayaran secara manual.")
        } }
        item { Panel("Akun & bantuan") {
            MenuRow("Status verifikasi petani", "Lihat contoh pengajuan yang perlu diperbaiki", Icons.Outlined.VerifiedUser) { onNavigate("verification-rejected") }
            MenuRow("Preferensi & Keamanan", "Kata sandi, notifikasi, dan ukuran huruf", Icons.Outlined.Settings) { onInfo("Preferensi mengikuti pengaturan perangkat. Penggantian kata sandi memerlukan layanan akun.") }
            MenuRow("Pusat Bantuan", "Panduan toko dan bantuan pesanan", Icons.AutoMirrored.Outlined.HelpOutline) { onInfo("Demo: periksa menu Stok untuk produk, Pesanan untuk pemenuhan, dan Pembayaran langsung untuk rekening/QRIS toko.") }
            SecondaryButton("Keluar dari akun", onLogout)
        } }
    }
}

@Composable
fun ShopScreen(values: Map<String, String>, onBack: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    var name by rememberSaveable { mutableStateOf(values["Nama"] ?: "Kebun Darto") }
    var description by rememberSaveable { mutableStateOf(values["Deskripsi"] ?: "Hasil panen segar dari petani lokal Malang.") }
    Page("Profil toko", onBack = onBack) {
        item { Panel {
            Field("Nama toko", name, { name = it }); Field("Deskripsi toko", description, { description = it }, multiline = true)
            PrimaryButton("Simpan profil toko", { onSave(mapOf("Nama" to name, "Deskripsi" to description)) }, name.isNotBlank())
        } }
    }
}
