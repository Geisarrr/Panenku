package id.panenku.app.ui.connected

import androidx.compose.foundation.layout.Row
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
            listOf("draft" to "Draf", "aktif" to "Aktif", "nonaktif" to "Nonaktif").forEach { (value, label) ->
                FilterChip(status == value, { status = value }, { Text(label) })
            }
            Text("Produk aktif memerlukan foto, toko lengkap, metode pembayaran, dan verifikasi sesuai aturan server.")
            if (product != null) {
                MediaPicker("Foto produk (JPEG/PNG, maks. 5 MB)", image, { image = it })
                SecondaryButton("Unggah foto produk", { image?.let { vm.productPhoto(product.text("id"), it) } }, enabled = image != null)
            } else Text("Simpan sebagai draf dahulu, lalu buka edit untuk mengunggah foto.")
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
@Composable internal fun ConnectedAccount(s: ConnectedState, vm: ConnectedViewModel) {
    val farmer = s.user?.role == "petani"
    val admin = s.user?.role == "admin"
    Page("Akun", s.user?.email.orEmpty()) {
        item { Text("Peran: ${s.user?.role}"); SecondaryButton("Perbarui profil", vm::profile) }
        s.profile?.let { p -> item { key(p.toString()) { ProfileForm(p, farmer, admin, vm) } } }
        if (farmer) { item { BankForm(vm) }; item { KtpForm(vm) } }
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
@Composable private fun BankForm(vm: ConnectedViewModel) {
    var bank by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    Panel("Tambah rekening toko") {
        Field("Nama bank", bank, { bank = it }); Field("Nomor rekening", account, { account = it }); Field("Pemilik rekening", name, { name = it })
        PrimaryButton("Simpan rekening", { vm.bank(bank, account, name) }, bank.isNotBlank() && account.isNotBlank() && name.isNotBlank())
    }
}
@Composable internal fun ConnectedAdmin(s: ConnectedState, vm: ConnectedViewModel) {
    Page("Verifikasi petani", admin = true) {
        item { SecondaryButton("Perbarui antrean", vm::adminQueue) }
        if (s.products.isEmpty() && !s.busy) item { Text("Tidak ada pengajuan menunggu pada halaman ini.") }
        items(s.products) { farmer ->
            var reason by rememberSaveable(farmer.text("farmerId")) { mutableStateOf("") }
            Panel(farmer.text("storeName")) {
                Text(farmer.text("email"))
                val id = farmer.text("farmerId").ifBlank { farmer.text("id") }
                SecondaryButton("Lihat dokumen pengembangan", { vm.image("admin/farmers/$id/ktp-document") })
                Field("Alasan penolakan", reason, { reason = it })
                PrimaryButton("Setujui petani", { vm.verifyFarmer(id, true, reason) })
                SecondaryButton("Tolak pengajuan", { vm.verifyFarmer(id, false, reason) }, enabled = reason.isNotBlank())
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
