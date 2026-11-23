package id.panenku.app.ui.connected.farmer

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.ConnectedState

@Composable internal fun FarmerBankScreen(s: ConnectedState, back: () -> Unit, load: (String) -> Unit,
    save: (String?, String, String, String) -> Unit) {
    val methods = s.paymentMethods.filter { it.text("methodType") == "bank_transfer" }
    var id by rememberSaveable { mutableStateOf(methods.firstOrNull()?.text("id")) }
    val method = methods.find { it.text("id") == id }
    var bank by rememberSaveable(id) { mutableStateOf(method?.text("bankName").orEmpty()) }
    var holder by rememberSaveable(id) { mutableStateOf(method?.text("accountHolderName") ?: s.profile?.text("ownerName").orEmpty()) }
    var account by rememberSaveable(id) { mutableStateOf("") }
    LaunchedEffect(id) { id?.let(load) }
    LaunchedEffect(s.paymentMethodDetail?.toString(), id) {
        s.paymentMethodDetail?.takeIf { it.text("id") == id }?.let { bank = it.text("bankName"); holder = it.text("accountHolderName"); account = it.text("accountNumber") }
    }
    val ready = bank.isNotBlank() && holder.isNotBlank() && account.isNotBlank() && !s.busy
    FSettingsPage("Rekening bank petani", "Pembayaran langsung ke ${s.profile?.text("storeName").orEmpty()}", back, footer = {
        FButton("Simpan Rekening", { save(id, bank, account, holder) }, enabled = ready)
        FText("Hanya menyimpan pengaturan rekening bank.", 10f, color = FMuted, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
    }) {
        FSettingsNotice("Rekening penerima pembayaran", "Pembeli mentransfer langsung ke rekening petani setelah pesanan diterima.")
        FCard("Data rekening bank") {
            if (methods.size > 1) FSelectField("Pilih rekening", method?.text("bankName").orEmpty(), methods.map { it.text("id") to "${it.text("bankName")} · ••••${it.text("accountNumberLast4")}" }, id.orEmpty(), { id = it })
            val banks = (listOf("BRI", "BCA", "BNI", "Mandiri", "BSI", "BTN", "CIMB Niaga", "Permata", "Danamon") + bank.takeIf(String::isNotBlank)).filterNotNull().distinct()
            FSelectField("Bank", bank, banks.map { it to it }, bank, { bank = it }, inlineCaret = true)
            FField("Nama pemilik rekening", holder, { holder = it.take(255) })
            FField("Nomor rekening", account, { account = it.filter(Char::isDigit).take(50) }, keyboard = KeyboardType.Number)
            FText("Nama pemilik harus sesuai dengan rekening bank. Perubahan hanya digunakan untuk checkout baru.", 12f, color = FMuted)
        }
        FSettingsNotice("Dana masuk langsung ke petani", "Pembayaran dilakukan di luar Panenku. Panenku tidak menyimpan saldo. Periksa dana masuk dan tinjau bukti pembayaran di pesanan secara manual.")
    }
}

@Composable internal fun FarmerQrisScreen(s: ConnectedState, back: () -> Unit, load: (String) -> Unit,
    save: (String?, String, String) -> Unit) {
    val methods = s.paymentMethods.filter { it.text("methodType") == "qris" }
    var id by rememberSaveable { mutableStateOf(methods.firstOrNull()?.text("id")) }
    val method = methods.find { it.text("id") == id }
    var holder by rememberSaveable(id) { mutableStateOf(method?.text("accountHolderName") ?: s.profile?.text("ownerName").orEmpty()) }
    var uri by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var file by rememberSaveable(id) { mutableStateOf("") }
    var size by rememberSaveable(id) { mutableLongStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingHolder by remember { mutableStateOf(false) }
    LaunchedEffect(id) { id?.let(load) }
    val choose = rememberFarmerImagePicker({ value, name, bytes -> uri = value; file = name; size = bytes; error = null }, { error = it })
    val bitmap = remember(s.farmerQrisImage, id) { s.farmerQrisImage?.takeIf { s.farmerQrisId == id }?.let { BitmapFactory.decodeByteArray(it,0,it.size) } }
    FSettingsPage("QRIS toko", "Pembayaran langsung ke ${s.profile?.text("storeName").orEmpty()}", back, footer = {
        FButton("Simpan QRIS", { uri?.let { save(id, holder, it) } }, enabled = uri != null && holder.isNotBlank() && !s.busy)
        FText("Hanya menyimpan pengaturan QRIS toko.", 10f, color = FMuted, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
    }) {
        FSettingsNotice(if (method?.flag("hasQrisImage") == true) "QRIS toko dilampirkan" else "Tambahkan QRIS toko", "Gunakan QRIS milik toko untuk menerima pembayaran langsung dari pembeli.")
        FCard("Pratinjau QRIS") {
            if (methods.size > 1) FSelectField("Pilih QRIS", holder, methods.map { it.text("id") to it.text("accountHolderName") }, id.orEmpty(), { id = it; holder = methods.find { m -> m.text("id") == it }?.text("accountHolderName").orEmpty(); uri = null })
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F5F5)), contentAlignment = Alignment.Center) {
                when {
                    uri != null -> coil.compose.AsyncImage(uri, "Pratinjau QRIS dipilih", Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                    bitmap != null -> Image(bitmap.asImageBitmap(), "QRIS toko tersimpan", Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Image(androidx.compose.ui.res.painterResource(R.drawable.farmer_qris_placeholder), null, Modifier.requiredSize(41.5.dp)) }
                        FText("QRIS", 14f, 700, Color(0xFF535353)); FText(if (s.busy) "Memuat gambar…" else "Belum ada pratinjau gambar", 12f, color = FMuted)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                FText(file.ifBlank { if (method?.flag("hasQrisImage") == true) "QRIS toko tersimpan" else "Belum ada berkas" }, 14f, 600)
                FText(if (size > 0) "JPG/PNG · ${shortFileSize(size)}" else "JPG/PNG · Maksimal 5 MB", 12f, color = FMuted)
                FText(holder.ifBlank { "Isi nama penerima QRIS" }, 12f, color = FMuted, modifier = Modifier.clickable(role = Role.Button) { editingHolder = true })
            }
            FButton("Ganti unggahan QRIS", choose, primary = false, enabled = !s.busy)
            FText("JPEG/PNG maksimal 5 MB. Gunakan QRIS milik tokomu. Ketuk nama penerima untuk mengubahnya.", 10f, color = FMuted)
        }
        FSettingsNotice("Periksa dana masuk secara manual", "Pembayaran QRIS langsung ke petani, di luar Panenku. Panenku tidak menyimpan dana. Tinjau bukti pembayaran pembeli di pesanan.")
        if (error != null) FSettingsNotice("Gambar belum dapat digunakan", error!!, "red")
    }
    if (editingHolder) AlertDialog(onDismissRequest = { editingHolder = false }, title = { Text("Nama penerima QRIS") }, text = { FField("Nama penerima", holder, { holder = it.take(255) }) }, confirmButton = { TextButton(onClick = { editingHolder = false }, enabled = holder.isNotBlank()) { Text("Selesai") } })
}
