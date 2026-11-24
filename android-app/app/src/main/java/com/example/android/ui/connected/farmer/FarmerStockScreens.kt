package id.panenku.app.ui.connected.farmer

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.TextStyle
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.ConnectedState
import java.time.*

@Composable internal fun FarmerHomeScreen(s: ConnectedState, onStock: () -> Unit, onEdit: (JsonObject) -> Unit,
    onOrders: (String) -> Unit, onProfile: () -> Unit, today: LocalDate = LocalDate.now(ZoneId.of("Asia/Jakarta")),
    greeting: String = when (LocalTime.now(ZoneId.of("Asia/Jakarta")).hour) { in 4..10 -> "Selamat pagi"; in 11..14 -> "Selamat siang"; in 15..17 -> "Selamat sore"; else -> "Selamat malam" }) {
    val owner = s.profile?.text("ownerName").orEmpty().ifBlank { "Petani" }
    val fresh = s.orders.count { it.text("orderStatus") == "menunggu_petani" }
    val shipping = s.orders.count { it.scheduleDate() == today.toString() && it.text("orderStatus") in listOf("diproses", "diantar", "siap_diambil") }
    val low = s.products.count { (it.text("stockAvailable").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) <= 12.toBigDecimal() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 17.44.dp, end = 17.44.dp, top = 19.2.dp, bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 45.35.dp).padding(horizontal = 3.5.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.5.dp)) {
                FText("$greeting, $owner", 10.465f, 500, FMuted)
                FText("Pantau hasil penjualan tani Anda", 13.95f, 700, line = 21f)
            }
            Box(Modifier.size(34.9.dp).clip(RoundedCornerShape(7.85.dp)).clickable(role = Role.Button, onClick = onProfile)) {
                FIcon(R.drawable.farmer_avatar, 34.9.dp, "Profil petani")
            }
        }
        Spacer(Modifier.height(17.44.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.85.dp)) {
            HomeTile("Pesanan Baru", fresh.toString(), "Menunggu konfirmasi", R.drawable.farmer_tile_order, Color(0xFF0D270C), Modifier.weight(1f)) { onOrders("menunggu") }
            HomeTile("Jadwal Kirim", shipping.toString(), "Harus dikirim hari ini", R.drawable.farmer_tile_schedule, Color(0xFF2C6F26), Modifier.weight(1f)) { onOrders("diproses") }
        }
        Spacer(Modifier.height(7.85.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.85.dp)) {
            HomeTile("Stok Panen", low.toString(), "Perlu segera diperbarui", R.drawable.farmer_tile_stock, Color(0xFF20491E), Modifier.weight(1f), onStock)
            HomeTile("Pembayaran", "Tinjau bukti", "Periksa bukti masuk", R.drawable.farmer_tile_payment, Color(0xFF6AC662), Modifier.weight(1f)) { onOrders("verifikasi") }
        }
        Row(Modifier.fillMaxWidth().padding(top = 19.2.dp), verticalAlignment = Alignment.CenterVertically) {
            FText("Stok panen aktif", 13.95f, 700, modifier = Modifier.weight(1f), line = 21f)
            FText("Lihat Semua", 10.465f, color = Color(0xFF36912E), modifier = Modifier.clickable(role = Role.Button, onClick = onStock))
        }
        Spacer(Modifier.height(12.2.dp))
        val active = s.products.filter { it.text("publicationStatus") == "aktif" }.take(3)
        if (active.isEmpty()) FNotice("Belum ada produk aktif", "Tambahkan hasil panen atau terbitkan produk yang sudah siap dijual.")
        active.forEachIndexed { i, p ->
            if (i > 0) Spacer(Modifier.height(7.85.dp))
            HomeStockCard(p) { onEdit(p) }
        }
    }
}
@Composable private fun HomeTile(title: String, value: String, hint: String, icon: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 99.42.dp).clip(RoundedCornerShape(11.34.dp)).background(color)
        .border(0.87.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(11.34.dp)).clickable(role = Role.Button, onClick = onClick)) {
        Canvas(Modifier.matchParentSize()) { drawOval(Color.White.copy(alpha = 0.05f), topLeft = androidx.compose.ui.geometry.Offset(68.46.dp.toPx(), 49.71.dp.toPx()), size = androidx.compose.ui.geometry.Size(148.26.dp.toPx(), 87.21.dp.toPx())) }
        Column(Modifier.padding(11.34.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.23.dp)) {
                FIcon(icon, 19.19.dp); FText(title, 10.465f, 600, Color.White)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.49.dp)) {
                FText(value, 15.7f, 700, Color.White, line = 23.55f)
                FText(hint, 8.72f, 500, Color(0xFFE6E6E6))
            }
        }
    }
}
@Composable private fun HomeStockCard(p: JsonObject, edit: () -> Unit) {
    FCard(modifier = Modifier.heightIn(min = 131.7.dp), padding = 10.465.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(11.34.dp)) {
            FPhoto(p.text("photoAccessUrl"), p.text("name"), Modifier.size(90.7.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    FText(p.text("name"), 12.21f, 700, modifier = Modifier.weight(1f), maxLines = 2)
                    val stock = p.text("stockAvailable").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
                    FBadge(if (stock.signum() <= 0) "Habis" else if (stock <= 12.toBigDecimal()) "Hampir Habis" else "Tersedia",
                        if (stock.signum() <= 0) "gray" else if (stock <= 12.toBigDecimal()) "orange" else "blue", true)
                }
                Spacer(Modifier.height(7.85.dp))
                FText("Stok: ${p.qty("stockAvailable")} kg", 10.465f, line = 15.7f)
                Spacer(Modifier.height(2.6.dp))
                FText("${money(p.long("pricePerKg"))}/Kg", 12.21f, 700, FForest)
                Spacer(Modifier.height(7.85.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.1.dp)) {
                    FButton("Edit stok", edit, Modifier.weight(1f), height = 32.27.dp, font = 10.465f)
                    FButton("Detail", edit, Modifier.weight(1f), primary = false, height = 32.27.dp, font = 10.465f)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun FarmerStockScreen(s: ConnectedState, back: () -> Unit, create: () -> Unit, edit: (JsonObject) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("semua") }
    val labels = listOf("semua" to "Semua", "aktif" to "Aktif", "draft" to "Draft", "nonaktif" to "Nonaktif")
    FPage("Stok & Produk", "${s.profile?.text("storeName").orEmpty().ifBlank { "Toko tani" }} · ${s.products.size} produk", back) {
        Row(Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(Color.White).border(1.dp, FBorder, CircleShape)
            .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FIcon(R.drawable.farmer_search)
            BasicTextField(search, { search = it }, Modifier.weight(1f).testTag("farmer-search"), singleLine = true,
                textStyle = TextStyle(fontFamily = FarmerFont, fontSize = 12.sp, color = FInk), decorationBox = { inner ->
                    if (search.isBlank()) FText("Cari nama produk")
                    inner()
                })
            FText("×", 12f, 500, Color(0xFF36912E), Modifier.clickable(role = Role.Button) { search = "" })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEach { (value, label) ->
                val count = if (value == "semua") s.products.size else s.products.count { it.text("publicationStatus") == value }
                FChip("$label ($count)", filter == value) { filter = value }
            }
        }
        FButton("+ Buat produk baru", create)
        FNotice("Tersedia = fisik − dipesan", "Stok dipesan ditahan untuk pesanan aktif. Saat pesanan ditolak, dibatalkan, kedaluwarsa, atau gagal dipenuhi, bagian yang ditahan dilepas satu kali.")
        val shown = s.products.filter { (filter == "semua" || it.text("publicationStatus") == filter) && it.text("name").contains(search, true) }
        if (shown.isEmpty()) FNotice("Belum ada produk", if (search.isBlank()) "Tambahkan produk pertama untuk toko Anda." else "Tidak ada produk yang cocok dengan pencarian.")
        shown.forEach { p -> StockProductCard(p) { edit(p) } }
        FText("Produk draft dan nonaktif tidak dapat dibeli.", 10f, color = FMuted)
    }
}
@Composable internal fun FChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(CircleShape).background(if (selected) Color(0xFF36912E) else Color.White)
        .border(1.dp, FBorder, CircleShape).clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        FText(label, 12f, 500, if (selected) Color.White else FMuted)
    }
}
@Composable private fun StockProductCard(p: JsonObject, edit: () -> Unit) {
    FCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FPhoto(p.text("photoAccessUrl"), p.text("name"), Modifier.size(62.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                FText(p.text("name"), 14f, 600, line = 18f)
                FText("${money(p.long("pricePerKg"))}/kg", 12f, color = FMuted)
                val status = p.text("publicationStatus")
                FBadge(when (status) { "aktif" -> "Aktif"; "draft" -> "Draft"; else -> "Nonaktif" }, if (status == "aktif") "green" else "gray")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Fisik" to p.qty("stockPhysical"), "Dipesan" to p.qty("stockReserved"), "Tersedia" to p.qty("stockAvailable")).forEach { (label, count) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(Color(0xFFF5F5F5)).padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    FText(label, 11f, color = FMuted); FText("$count kg", 13f, 700, FForest)
                }
            }
        }
        if (p.text("stockAvailable").toBigDecimalOrNull()?.signum() == 0) FBadge("Tidak dapat dibeli · Stok habis", "orange")
        FButton(if (p.text("publicationStatus") == "draft") "Lanjutkan draft" else "Edit produk & stok", edit, primary = false)
    }
}

@Composable internal fun FDateField(label: String, date: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FText(label, 12f, color = Color(0xFF3F3F46), line = 16f)
        Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
            .border(1.dp, Color(0xFFE4E4E7), RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                val current = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
                DatePickerDialog(context, { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day).toString()) }, current.year, current.monthValue - 1, current.dayOfMonth).show()
            }.padding(horizontal = 14.dp, vertical = 10.dp)) {
            FText(if (date.isBlank()) "Pilih tanggal" else dateLabel(date), 13f, line = 20f)
        }
    }
}
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

@Composable internal fun FarmerProductScreen(product: JsonObject?, s: ConnectedState, back: () -> Unit,
    save: (String?, JsonObject, String?, (String) -> Unit, () -> Unit) -> Unit) {
    var id by rememberSaveable { mutableStateOf(product?.text("id")) }
    var name by rememberSaveable { mutableStateOf(product?.text("name").orEmpty()) }
    var commodity by rememberSaveable { mutableStateOf(product?.text("commodityId").orEmpty()) }
    var price by rememberSaveable { mutableStateOf(product?.text("pricePerKg").orEmpty()) }
    var stock by rememberSaveable { mutableStateOf(product?.qty("stockPhysical").orEmpty()) }
    var date by rememberSaveable { mutableStateOf(product?.text("readyDate").orEmpty()) }
    var description by rememberSaveable { mutableStateOf(product?.text("description").orEmpty()) }
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    var validation by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val resolver = context.contentResolver
            if (resolver.getType(uri) in listOf("image/jpeg", "image/png")) {
                runCatching { resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                image = uri.toString(); validation = null
            } else validation = "Pilih foto JPG atau PNG."
        }
    }
    val live = s.products.find { it.text("id") == id } ?: product
    val gate = listOf(
        "Identitas petani terverifikasi" to (s.profile?.text("ktpStatus") == "disetujui" || live?.flag("farmerVerified") == true),
        "Akun aktif, tidak disuspensi" to (s.user?.status == "active"),
        "Rekening / QRIS toko lengkap" to s.paymentMethods.any { it.text("accountHolderName").isNotBlank() && ((it.text("methodType") == "bank_transfer" && it.text("accountNumberLast4").isNotBlank()) || (it.text("methodType") == "qris" && it.flag("hasQrisImage"))) },
        "Foto, komoditas, harga & stok lengkap" to ((image != null || live?.text("photoAccessUrl")?.isNotBlank() == true || live?.text("photoUrl")?.isNotBlank() == true) && commodity.isNotBlank() && price.toLongOrNull()?.let { it > 0 } == true && stock.replace(',', '.').toBigDecimalOrNull()?.signum() == 1 && name.isNotBlank() && date.isNotBlank())
    )
    fun submit(status: String) {
        val amount = price.toLongOrNull(); val kg = stock.replace(',', '.').toBigDecimalOrNull()
        if (name.isBlank() || commodity.isBlank() || amount == null || amount <= 0 || kg == null || kg.signum() < 0 || date.isBlank()) {
            validation = "Lengkapi nama, komoditas, harga, stok, dan tanggal siap kirim."; return
        }
        if (status == "aktif" && gate.any { !it.second }) { validation = "Lengkapi syarat penerbitan sebelum produk diterbitkan."; return }
        validation = null
        save(id, json("commodityId" to commodity, "name" to name.trim(), "pricePerKg" to amount,
            "stockPhysical" to kg, "readyDate" to date, "publicationStatus" to status, "description" to description.trim()), image, { id = it }, back)
    }
    FPage(if (id == null) "Tambah produk" else "Edit & Upload produk",
        "${s.profile?.text("storeName").orEmpty()} · ${if (product?.text("publicationStatus") == "draft") "Draft " else ""}${name.ifBlank { "Hasil panen baru" }}", back) {
        FCard("Foto produk") {
            FPhoto(image ?: live?.text("photoAccessUrl").orEmpty(), name, Modifier.fillMaxWidth().height(180.dp))
            FButton("Ganti / tambah foto produk", { picker.launch(arrayOf("image/jpeg", "image/png")) }, primary = false, enabled = !s.busy)
        }
        FCard("Informasi panen") {
            FField("Nama produk", name, { name = it.take(255) })
            FSelectField("Komoditas", s.commodities.find { it.text("id") == commodity }?.text("name") ?: product?.text("commodity").orEmpty(),
                s.commodities.filter { it.flag("isActive") }.map { it.text("id") to it.text("name") }, commodity, { commodity = it })
            FField("Deskripsi & kualitas", description, { description = it.take(5000) }, multiline = true)
            FField("Harga per kg", price.toLongOrNull()?.let(::money).orEmpty(), { price = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                FField("Stok fisik (kg)", stock, { stock = it }, keyboard = KeyboardType.Decimal)
                val reserved = live?.qty("stockReserved") ?: "0"
                val available = (stock.replace(',', '.').toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) - (reserved.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO)
                FText("Dipesan $reserved kg · Tersedia setelah terbit ${quantity(available.toPlainString())} kg.", 10f, color = FMuted)
            }
            FDateField("Tanggal siap kirim", date) { date = it }
            FText("Gunakan harga Rp/kg agar mudah dibandingkan. Harga baru hanya berlaku untuk transaksi baru.", 10f, color = FMuted)
        }
        FCard("Syarat penerbitan") {
            FBadge(if (gate.all { it.second }) "Semua syarat terpenuhi" else "Lengkapi syarat penerbitan", if (gate.all { it.second }) "green" else "orange")
            FText(gate.joinToString("\n") { (if (it.second) "✓ " else "○ ") + it.first }, 12f, line = 15f)
            FRule()
            FText("Produk aktif hanya tersedia dari petani terverifikasi dan tidak disuspensi. Jika salah satu syarat tidak terpenuhi, penerbitan dinonaktifkan.", 12f, color = FMuted, line = 15f)
        }
        if (validation != null) FNotice("Periksa kembali", validation!!, true)
        FButton("Terbitkan produk", { submit("aktif") }, enabled = !s.busy)
        FButton("Simpan sebagai draft", { submit("draft") }, primary = false, enabled = !s.busy)
        if (product?.text("publicationStatus") == "aktif") FButton("Nonaktifkan produk", { submit("nonaktif") }, primary = false, enabled = !s.busy)
        FText("Pesanan lama tetap memakai harga dan rincian saat transaksi dibuat.", 10f, color = FMuted)
    }
}
