package id.panenku.app.ui.connected

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.gson.JsonObject
import id.panenku.app.data.remote.*
import id.panenku.app.ui.components.*

@Composable internal fun ConnectedProduct(product: JsonObject?, s: ConnectedState, vm: ConnectedViewModel, back: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(product?.text("name").orEmpty()) }
    var commodity by rememberSaveable { mutableStateOf(product?.text("commodityId").orEmpty()) }
    var price by rememberSaveable { mutableStateOf(product?.text("pricePerKg").orEmpty()) }
    var stock by rememberSaveable { mutableStateOf(product?.text("stockPhysical").orEmpty()) }
    var date by rememberSaveable { mutableStateOf(product?.text("readyDate").orEmpty()) }
    var description by rememberSaveable { mutableStateOf(product?.text("description").orEmpty()) }
    var status by rememberSaveable { mutableStateOf(product?.text("publicationStatus") ?: "draft") }
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    Page(if (product == null) "Tambah produk" else "Edit produk", onBack = back) {
        item { Panel {
            Field("Nama produk", name, { name = it })
            Text("Komoditas")
            s.commodities.forEach { c -> FilterChip(commodity == c.text("id"), { commodity = c.text("id") }, { Text(c.text("name")) }) }
            Field("Harga per kg (rupiah)", price, { price = it })
            Field("Stok fisik (kg)", stock, { stock = it })
            Field("Tanggal siap (YYYY-MM-DD)", date, { date = it })
            Field("Deskripsi", description, { description = it }, multiline = true)
            (if (product == null) listOf("draft" to "Draf") else listOf("draft" to "Draf", "aktif" to "Aktif", "nonaktif" to "Nonaktif")).forEach { (value, label) ->
                FilterChip(status == value, { status = value }, { Text(label) })
            }
            Text("Produk aktif memerlukan foto, toko lengkap, metode pembayaran, dan verifikasi sesuai aturan server.")
            s.profile?.let { profile ->
                if (profile.text("pickupLocation").isBlank()) Text("Belum bisa terbit: lengkapi lokasi pengambilan di Akun.")
                if (s.paymentMethods.isEmpty()) Text("Belum bisa terbit: tambahkan metode pembayaran toko di Akun.")
            }
            if (product != null) {
                if (product.text("photoAccessUrl").isNotBlank()) AsyncImage(product.text("photoAccessUrl"),
                    "Foto produk tersimpan", Modifier.fillMaxWidth().height(180.dp))
                if (!product.flag("farmerVerified")) Text("Belum bisa terbit: KTP petani belum disetujui.")
                if (product.text("photoAccessUrl").isBlank()) Text("Belum bisa terbit: unggah foto produk terlebih dahulu.")
                if (stock.replace(',', '.').toBigDecimalOrNull()?.signum() != 1) Text("Belum bisa terbit: stok harus lebih dari nol.")
                Text("Jika publikasi ditolak, alasan dari server ditampilkan agar data dapat diperbaiki.")
                MediaPicker("Foto produk (JPEG/PNG, maks. 5 MB)", image, { image = it })
                SecondaryButton("Unggah foto produk", { image?.let { vm.productPhoto(product.text("id"), it) } }, enabled = image != null)
            } else Text("Simpan sebagai draf dahulu, lalu buka edit untuk memilih dan mengunggah foto. Setelah foto tersimpan, pilih Aktif untuk publikasi.")
            val amount = price.toLongOrNull()
            val quantity = stock.replace(',', '.').toBigDecimalOrNull()
            PrimaryButton("Simpan produk", {
                vm.saveProduct(product?.text("id"), json("commodityId" to commodity, "name" to name, "pricePerKg" to amount,
                    "stockPhysical" to quantity, "readyDate" to date, "publicationStatus" to status,
                    "description" to description, "photoUrl" to product?.text("photoUrl")?.takeIf { it.isNotBlank() }), back)
            }, commodity.isNotBlank() && name.isNotBlank() && amount != null && quantity != null && date.isNotBlank())
        } }
    }
}
@Composable internal fun ConnectedAccount(s: ConnectedState, vm: ConnectedViewModel, onNotifications: () -> Unit = {}) {
    val farmer = s.user?.role == "petani"
    val admin = s.user?.role == "admin"
    Page("Akun", s.user?.email.orEmpty()) {
        item {
            Text("Peran: ${s.user?.role}")
            SecondaryButton(if (s.unreadNotificationCount > 0) "Notifikasi (${s.unreadNotificationCount} belum dibaca)" else "Notifikasi", onNotifications)
            SecondaryButton("Perbarui profil", vm::profile)
        }
        s.profile?.let { p -> item { key(p.toString()) { ProfileForm(p, farmer, admin, vm) } } }
        if (farmer) { item { PaymentMethods(s, vm) }; item { KtpForm(vm) } }
        item { SecondaryButton("Keluar", vm::logout) }
    }
}
@Composable private fun ProfileForm(p: JsonObject, farmer: Boolean, admin: Boolean, vm: ConnectedViewModel) {
    var name by rememberSaveable { mutableStateOf(p.text(if (farmer) "storeName" else "fullName")) }
    var phone by rememberSaveable { mutableStateOf(p.text("contactPhone")) }
    var location by rememberSaveable { mutableStateOf(p.text(if (farmer) "pickupLocation" else "deliveryAddress")) }
    var district by rememberSaveable { mutableStateOf(p.getAsJsonArray("serviceArea")?.joinToString(",") { it.asString }.orEmpty()) }
    var slots by rememberSaveable { mutableStateOf(p.getAsJsonArray("timeSlots")?.joinToString(",") { it.asString }.orEmpty()) }
    var fee by rememberSaveable { mutableStateOf(p.text("deliveryFee")) }
    var shipping by rememberSaveable { mutableStateOf(p.flag("shippingEnabled")) }
    Panel("Profil") {
        Field(if (farmer) "Nama toko" else "Nama lengkap", name, { name = it })
        if (!admin && !farmer) Field("Nomor HP", phone, { phone = it })
        if (!admin) Field(if (farmer) "Lokasi pengambilan" else "Alamat utama", location, { location = it }, multiline = true)
        if (farmer) {
            Text("Status verifikasi: ${p.text("ktpStatus")}")
            Row { Checkbox(shipping, { shipping = it }); Text("Aktifkan pengantaran") }
            Field("Kecamatan layanan, pisahkan dengan koma", district, { district = it })
            Field("Ongkir (rupiah)", fee, { fee = it })
            Field("Slot waktu, pisahkan dengan koma", slots, { slots = it }, "09:00-12:00,13:00-16:00")
        }
        PrimaryButton("Simpan profil", {
            val body = if (farmer) json("storeName" to name, "pickupLocation" to location,
                "serviceArea" to district.split(',').map(String::trim).filter(String::isNotBlank), "deliveryFee" to fee.toLongOrNull(),
                "shippingEnabled" to shipping, "timeSlots" to slots.split(',').map(String::trim).filter(String::isNotBlank))
            else json("fullName" to name, "contactPhone" to phone.takeIf { !admin }, "deliveryAddress" to location.takeIf { !admin })
            vm.saveProfile(body)
        }, name.isNotBlank() && (!farmer || fee.toLongOrNull() != null))
    }
}
@Composable private fun PaymentMethods(s: ConnectedState, vm: ConnectedViewModel) {
    var bankId by rememberSaveable { mutableStateOf<String?>(null) }
    var bank by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    var bankHolder by rememberSaveable { mutableStateOf("") }
    var qrisId by rememberSaveable { mutableStateOf<String?>(null) }
    var qrisHolder by rememberSaveable { mutableStateOf("") }
    var qrisImage by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(bankId, s.paymentMethodDetail?.toString()) {
        val detail = s.paymentMethodDetail
        if (bankId != null && detail?.text("id") == bankId) detail?.let {
            bank = it.text("bankName")
            account = it.text("accountNumber")
            bankHolder = it.text("accountHolderName")
        }
    }
    fun clearBank() { bankId = null; bank = ""; account = ""; bankHolder = ""; vm.clearPaymentMethodDetail() }
    fun clearQris() { qrisId = null; qrisHolder = ""; qrisImage = null }

    Panel("Metode pembayaran toko") {
        SecondaryButton("Muat ulang metode pembayaran", vm::paymentMethods)
        if (s.paymentMethods.isEmpty() && !s.busy) Text("Belum ada rekening atau QRIS.")
        s.paymentMethods.forEach { method ->
            val id = method.text("id")
            if (method.text("methodType") == "bank_transfer") {
                Text("${method.text("bankName")} · ••••${method.text("accountNumberLast4")} · ${method.text("accountHolderName")}")
                Row {
                    TextButton(onClick = { bankId = id; vm.bankDetail(id) }) { Text("Edit rekening") }
                    TextButton(onClick = { deleteTarget = "bank" to id }) { Text("Hapus") }
                }
            } else {
                Text("QRIS · ${method.text("accountHolderName")}")
                SecondaryButton("Lihat QRIS", { vm.image("petani/payment-methods/$id/qris") })
                PrimaryButton("Ganti QRIS", {
                    qrisId = id
                    qrisHolder = method.text("accountHolderName")
                    qrisImage = null
                })
                TextButton(onClick = { deleteTarget = "qris" to id }) { Text("Hapus QRIS") }
            }
        }

        Text(if (bankId == null) "Tambah rekening" else "Edit rekening")
        Field("Nama bank", bank, { bank = it.take(100) })
        Field("Nomor rekening", account, { account = it.filter(Char::isDigit).take(50) })
        Field("Pemilik rekening", bankHolder, { bankHolder = it.take(255) })
        PrimaryButton("Simpan rekening", { vm.saveBank(bankId, bank, account, bankHolder, ::clearBank) },
            bank.isNotBlank() && account.isNotBlank() && bankHolder.isNotBlank() && !s.busy)
        if (bankId != null) SecondaryButton("Batal edit rekening", ::clearBank)

        Text(if (qrisId == null) "Tambah QRIS" else "Ganti QRIS terpilih")
        Field("Nama penerima QRIS", qrisHolder, { qrisHolder = it.take(255) })
        MediaPicker("Gambar QRIS (JPEG/PNG, maks. 5 MB)", qrisImage, { qrisImage = it })
        PrimaryButton("Simpan QRIS", { qrisImage?.let { vm.saveQris(qrisId, qrisHolder, it, ::clearQris) } },
            qrisHolder.isNotBlank() && qrisImage != null && !s.busy)
        if (qrisId != null) SecondaryButton("Batal ganti QRIS", ::clearQris)
        Text("Perubahan hanya berlaku untuk checkout baru. Snapshot pesanan lama tidak berubah.")
    }
    deleteTarget?.let { (type, id) -> AlertDialog(onDismissRequest = { deleteTarget = null },
        title = { Text("Hapus metode pembayaran?") },
        text = { Text("Metode tidak tersedia untuk checkout baru. Pesanan lama tetap memakai snapshot sebelumnya.") },
        confirmButton = { TextButton(onClick = { deleteTarget = null; if (type == "bank") vm.deleteBank(id) else vm.deleteQris(id) }) { Text("Hapus") } },
        dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } }) }
}
@Composable internal fun ConnectedAdmin(s: ConnectedState, vm: ConnectedViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("menunggu") }
    Page("Verifikasi petani", admin = true) {
        item { Panel("Cari pengajuan") {
            Field("Cari toko, email, atau ID", search, { search = it.take(100) })
            Row { listOf("menunggu" to "Menunggu", "disetujui" to "Disetujui", "ditolak" to "Ditolak").forEach { (value, label) ->
                FilterChip(status == value, { status = value }, { Text(label) })
            } }
            PrimaryButton("Terapkan filter", { vm.adminQueue(status, search) })
        } }
        if (s.products.isEmpty() && !s.busy) item { Text("Tidak ada pengajuan yang sesuai filter.") }
        items(s.products) { farmer ->
            var reason by rememberSaveable(farmer.text("farmerId")) { mutableStateOf("") }
            Panel(farmer.text("storeName")) {
                Text(farmer.text("email"))
                Text("KTP: ${farmer.text("ktpStatus")} · akun ${farmer.text("accountStatus")}")
                val id = farmer.text("farmerId").ifBlank { farmer.text("id") }
                if (farmer.flag("hasDocument")) SecondaryButton("Lihat dokumen pengembangan", { vm.image("admin/farmers/$id/ktp-document") })
                if (farmer.text("ktpStatus") == "menunggu") {
                    Field("Alasan penolakan", reason, { reason = it.take(1000) })
                    PrimaryButton("Setujui petani", { vm.verifyFarmer(id, true, reason) })
                    SecondaryButton("Tolak pengajuan", { vm.verifyFarmer(id, false, reason) }, enabled = reason.isNotBlank())
                }
            }
        }
    }
}

@Composable private fun KtpForm(vm: ConnectedViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var nik by rememberSaveable { mutableStateOf("") }
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    Panel("Verifikasi petani · Pengembangan") {
        Text("Gunakan dokumen contoh. Backend saat ini menyediakan verifikasi dokumen dummy untuk pengembangan.")
        Field("Nama contoh", name, { name = it })
        Field("NIK dummy", nik, { nik = it }, "DUMMY-0001")
        MediaPicker("Gambar dokumen contoh", image, { image = it })
        PrimaryButton("Kirim dokumen contoh", { image?.let { vm.ktp(name, nik, it) } }, name.isNotBlank() && nik.isNotBlank() && image != null)
    }
}

@Composable internal fun ConnectedCommodities(s: ConnectedState, vm: ConnectedViewModel) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(true) }
    Page("Kelola komoditas", admin = true) {
        item { SecondaryButton("Muat ulang komoditas", vm::adminCommodities) }
        item { Panel(if (selected == null) "Tambah komoditas" else "Edit komoditas") {
            Field("Nama komoditas", name, { name = it.take(100) })
            Row { Checkbox(active, { active = it }); Text("Komoditas aktif") }
            PrimaryButton("Simpan komoditas", {
                vm.saveCommodity(selected, name, active) { selected = null; name = ""; active = true }
            }, name.isNotBlank() && !s.busy)
            if (selected != null) SecondaryButton("Batal edit", { selected = null; name = ""; active = true })
        } }
        if (s.commodities.isEmpty() && !s.busy) item { Text("Belum ada komoditas.") }
        items(s.commodities, key = { it.text("id") }) { commodity ->
            Panel(commodity.text("name")) {
                Text(if (commodity.flag("isActive")) "Aktif" else "Nonaktif")
                SecondaryButton("Edit ${commodity.text("name")}", {
                    selected = commodity.text("id"); name = commodity.text("name"); active = commodity.flag("isActive")
                })
            }
        }
    }
}
