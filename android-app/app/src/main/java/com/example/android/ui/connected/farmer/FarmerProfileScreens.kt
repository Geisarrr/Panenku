package id.panenku.app.ui.connected.farmer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.ConnectedState
import java.time.LocalDate

@Composable internal fun FarmerProfileScreen(s: ConnectedState, back: () -> Unit, open: (String) -> Unit,
    save: (String, String, String, () -> Unit) -> Unit, logout: () -> Unit) {
    val p = s.profile ?: JsonObject()
    var dialog by rememberSaveable { mutableStateOf("") }
    var owner by rememberSaveable(p.text("ownerName")) { mutableStateOf(p.text("ownerName")) }
    var phone by rememberSaveable(p.text("contactPhone")) { mutableStateOf(p.text("contactPhone")) }
    var store by rememberSaveable(p.text("storeName")) { mutableStateOf(p.text("storeName")) }
    val verified = p.text("ktpStatus") == "disetujui"
    val verificationLabel = verificationLabel(p.text("ktpStatus"))
    val context = LocalContext.current
    FSettingsPage("Profil & Toko", "Kelola toko dan layanan panenmu", back, gap = 20.dp) {
        FCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FIcon(R.drawable.farmer_profile_avatar, 64.dp, "Ilustrasi petani")
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    FText(p.text("ownerName").ifBlank { "Petani" }, 18f, 700)
                    FText(s.user?.email.orEmpty(), 12f, color = FMuted)
                    if (p.text("contactPhone").isNotBlank()) FText(p.text("contactPhone"), 12f, color = FMuted)
                }
                Box(Modifier.size(20.dp).clickable(role = Role.Button) { dialog = "edit" }) { FIcon(R.drawable.farmer_edit, 20.dp, "Edit profil") }
            }
            FRule()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    FText(p.text("storeName").ifBlank { "Lengkapi nama toko" }, 14f, 700, FForest)
                    FText("Petani · ${p.text("pickupLocation").lineSequence().filter(String::isNotBlank).firstOrNull() ?: "Lokasi belum diisi"}", 10f, color = FMuted, maxLines = 2)
                }
                Row(Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(if(verified) Color(0xFFF3FBF2) else Color(0xFFFFF5E7)).padding(horizontal=8.dp,vertical=6.dp), horizontalArrangement=Arrangement.spacedBy(4.dp), verticalAlignment=Alignment.CenterVertically) {
                    FIcon(R.drawable.farmer_verified,14.dp)
                    FText(verificationLabel,10f,600,if(verified)Color(0xFF36912E) else Color(0xFFAD6819))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FText("Pengaturan toko", 14f, 600)
            FCard {
                FSettingsRow(R.drawable.farmer_edit, "Profil toko", "${p.text("storeName")} · Nama dan informasi toko") { dialog = "edit" }; FRule()
                FSettingsRow(R.drawable.farmer_location, "Lokasi kebun & pengambilan", "${(p.text("pickupLocation").lineSequence().drop(1).firstOrNull() ?: p.text("pickupLocation"))} · Ambil sendiri Rp0") { open("services") }; FRule()
                FSettingsRow(R.drawable.farmer_truck, "Layanan & area antar", "${if (p.flag("shippingEnabled")) "Aktif" else "Nonaktif"} · ${p.stringRows("serviceArea").joinToString(", ")} · Ongkir ${money(p.long("deliveryFee"))} per pesanan") { open("services") }; FRule()
                FSettingsRow(R.drawable.farmer_clock, "Jam layanan", p.stringRows("timeSlots").joinToString(" / ") { displaySlot(it) }.let { if (it.isBlank()) "Atur slot layanan toko" else "$it WIB. Antar & ambil sendiri." }) { open("services") }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FText("Pembayaran langsung", 14f, 600)
            FCard {
                FSettingsRow(R.drawable.farmer_bank, "Rekening bank petani", "Atur rekening penerima pembayaran pembeli") { open("bank") }; FRule()
                FSettingsRow(R.drawable.farmer_scan, "QRIS toko", "Kelola QRIS untuk pembayaran ke petani") { open("qris") }
            }
            FSettingsNotice("Dana masuk langsung ke rekeningmu", "Panenku tidak menyimpan saldo. Periksa dana masuk dan verifikasi bukti pembayaran pembeli secara manual.")
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FText("Akun & bantuan", 14f, 600)
            FCard {
                FSettingsRow(R.drawable.farmer_verified, "Status verifikasi petani", "$verificationLabel · Lihat informasi verifikasi") { open("verification") }; FRule()
                FSettingsRow(R.drawable.farmer_lock, "Preferensi & Keamanan", "Notifikasi dan pengaturan aplikasi") { dialog = "preferences" }; FRule()
                FSettingsRow(R.drawable.farmer_help, "Pusat Bantuan", "Panduan toko dan bantuan pesanan") { dialog = "help" }
            }
        }
        FCard(padding = 12.dp) {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { dialog = "logout" }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) { FIcon(R.drawable.farmer_logout, 18.dp); FText("Keluar dari akun", 14f, 600) }
        }
    }
    if (dialog == "edit") AlertDialog(onDismissRequest = { dialog = "" }, title = { FText("Profil toko", 18f, 700) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FField("Nama pemilik", owner, { owner = it.take(255) }); FField("Nomor HP", phone, { phone = it.take(20) }, keyboard = KeyboardType.Phone)
            FField("Nama toko", store, { store = it.take(255) })
        }
    }, confirmButton = { TextButton(onClick = { save(owner.trim(), phone.trim(), store.trim()) { dialog = "" } }, enabled = owner.isNotBlank() && store.isNotBlank() && !s.busy) { Text("Simpan") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Batal") } })
    if (dialog == "logout") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Keluar dari akun?") }, text = { Text("Masuk kembali untuk mengelola toko dan pesanan.") }, confirmButton = { TextButton(onClick = { dialog = ""; logout() }) { Text("Keluar") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Batal") } })
    if (dialog == "preferences") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Preferensi & Keamanan") }, text = { Text("Atur izin dan notifikasi Panenku melalui pengaturan Android. Jangan membagikan kata sandi atau bukti pembayaran kepada pihak lain.") }, confirmButton = { TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))); dialog = "" }) { Text("Buka pengaturan") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Tutup") } })
    if (dialog == "help") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Pusat Bantuan") }, text = { Text("Verifikasi identitas untuk menerbitkan produk. Pembayaran dan transfer refund dilakukan langsung melalui bank atau QRIS. Periksa dana masuk sebelum menyetujui pembayaran.") }, confirmButton = { TextButton(onClick = { dialog = ""; open("refunds") }) { Text("Kelola refund") } }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Tutup") } })
}
internal fun JsonObject.stringRows(key: String): List<String> = get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { runCatching { it.asString }.getOrNull() }.orEmpty()
internal fun verificationLabel(status: String) = when (status) { "disetujui" -> "Terverifikasi"; "ditolak" -> "Ditolak"; "menunggu" -> "Menunggu pemeriksaan"; else -> "Belum diajukan" }
internal fun parseServiceSlots(input: String): List<String> {
    val matches = Regex("(\\d{2})[:.](\\d{2})\\s*[–-]\\s*(\\d{2})[:.](\\d{2})").findAll(input).map { m -> "${m.groupValues[1]}:${m.groupValues[2]}-${m.groupValues[3]}:${m.groupValues[4]}" }.toList()
    require(matches.isNotEmpty()) { "Isi slot dengan format 09:00-12:00,13:00-16:00." }
    val clean = matches.distinct().sorted()
    clean.forEach { val start = java.time.LocalTime.parse(it.take(5)); val end = java.time.LocalTime.parse(it.takeLast(5)); require(start < end) { "Jam akhir harus setelah jam mulai." } }
    require(clean.zipWithNext().none { (a,b) -> a.takeLast(5) > b.take(5) }) { "Slot layanan tidak boleh bertumpang tindih." }
    return clean
}
internal fun farmerProfileBody(p: JsonObject, store: String = p.text("storeName"), location: String = p.text("pickupLocation"), areas: List<String> = p.stringRows("serviceArea"), fee: Long = p.long("deliveryFee"), shipping: Boolean = p.flag("shippingEnabled"), slots: List<String> = p.stringRows("timeSlots")) =
    json("storeName" to store, "pickupLocation" to location.takeIf(String::isNotBlank), "serviceArea" to areas, "deliveryFee" to fee, "shippingEnabled" to shipping, "timeSlots" to slots)

@Composable internal fun FarmerServicesScreen(s: ConnectedState, back: () -> Unit, save: (JsonObject) -> Unit, initialDay: Int = LocalDate.now().dayOfWeek.value - 1) {
    val p = s.profile ?: JsonObject(); val parts = p.text("pickupLocation").split('\n')
    var name by rememberSaveable(p.toString()) { mutableStateOf(if (parts.size > 1) parts[0] else p.text("storeName")) }
    var address by rememberSaveable(p.toString()) { mutableStateOf(if (parts.size > 1) parts[1] else parts[0]) }
    var directions by rememberSaveable(p.toString()) { mutableStateOf(parts.drop(2).joinToString("\n")) }
    var areas by rememberSaveable(p.toString()) { mutableStateOf(p.stringRows("serviceArea").joinToString(", ")) }
    var fee by rememberSaveable(p.toString()) { mutableStateOf(p.long("deliveryFee").toString()) }
    var shipping by rememberSaveable(p.toString()) { mutableStateOf(p.flag("shippingEnabled")) }
    var slots by rememberSaveable(p.toString()) { mutableStateOf(p.stringRows("timeSlots").joinToString(" / ") { displaySlot(it) }) }
    var day by rememberSaveable { mutableIntStateOf(initialDay) }
    var error by remember { mutableStateOf<String?>(null) }
    var locationDialog by remember { mutableStateOf(false) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    fun submit() { runCatching {
        val amount = fee.toLongOrNull(); require(amount != null && amount >= 0) { "Ongkir tidak boleh negatif." }
        val districts = areas.split(',').map(String::trim).filter(String::isNotBlank)
        require(!shipping || districts.isNotEmpty()) { "Isi minimal satu kecamatan untuk layanan antar." }
        require(address.isNotBlank()) { "Isi alamat pengambilan." }
        val allSlots = parseServiceSlots(slots)
        error = null; focus.clearFocus()
        save(farmerProfileBody(p, location = listOf(name.trim(), address.trim(), directions.trim()).filter(String::isNotBlank).joinToString("\n"), areas = districts, fee = amount, shipping = shipping, slots = allSlots))
    }.onFailure { error = it.message ?: "Periksa pengaturan layanan." } }
    FSettingsPage("Layanan & area antar", "${p.text("storeName")} · Pengaturan operasional", back) {
        FCard("Lokasi pengambilan") {
            FField("Nama lokasi", name, { name = it }); FField("Alamat ambil", address, { address = it }); FField("Petunjuk lokasi", directions, { directions = it })
            FButton("Ubah titik pengambilan", { locationDialog = true }, primary = false)
        }
        FCard("Layanan tersedia") {
            FKeyValue("Ambil sendiri", "✓ Aktif · Rp0")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FText("Antar oleh petani", 12f, color = FMuted, modifier = Modifier.weight(1f))
                FText(if (shipping) "✓ Aktif" else "Nonaktif", 12f, 600, FForest, Modifier.clickable(role = Role.Switch) { shipping = !shipping })
            }
            FText("Pembeli memilih salah satu saat checkout.", 12f, color = FMuted)
            FField("Kecamatan yang dilayani", areas, { areas = it }, "Pisahkan kecamatan dengan koma")
            FField("Ongkir per pesanan", fee.toLongOrNull()?.let(::money).orEmpty(), { fee = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number)
            FText("Tarif tetap untuk alamat dalam area layanan.", 10f, color = FMuted)
        }
        FCard("Hari & jam layanan") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Sen","Sel","Rab","Kam","Jum","Sab","Min").forEachIndexed { i,label -> FChip(label, day == i) { day = i } }
            }
            FText("Pratinjau hari layanan. Daftar slot yang sama berlaku untuk setiap hari, baik antar maupun ambil sendiri.", 10f, color = FMuted)
            val parsed = runCatching { parseServiceSlots(slots) }.getOrDefault(emptyList())
            FField("Jam buka – tutup", if (parsed.isEmpty()) "—" else "${parsed.first().take(5).replace(':','.')} – ${parsed.last().takeLast(5).replace(':','.')} WIB", {}, enabled = false)
            FField("Slot antar", parsed.firstOrNull()?.let { "${displaySlot(it)} WIB" }.orEmpty(), {}, enabled = false)
            FField("Slot ambil sendiri", slots, { slots = it }, "09:00-12:00 / 13:00-16:00")
        }
        FSettingsNotice("Jadwal checkout mengikuti layanan", "Tanggal terawal mengikuti produk dengan tanggal siap paling akhir. Alamat di luar area toko tidak bisa memilih antar dari toko ini.")
        if (error != null) FSettingsNotice("Periksa kembali", error!!, "red")
        FButton("Simpan pengaturan layanan", ::submit, enabled = !s.busy)
        FText("Perubahan berlaku untuk pesanan baru. Jadwal pesanan yang sudah dibuat tetap tersimpan.", 10f, color = FMuted)
    }
    if (locationDialog) AlertDialog(onDismissRequest = { locationDialog = false }, title = { Text("Titik pengambilan") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { FField("Nama lokasi", name, { name = it }); FField("Alamat ambil", address, { address = it }); FField("Petunjuk lokasi", directions, { directions = it }) } }, confirmButton = { TextButton(onClick = { locationDialog = false }, enabled = address.isNotBlank()) { Text("Gunakan lokasi") } })
}
