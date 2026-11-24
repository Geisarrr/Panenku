package id.panenku.app.features.f1_catalog

import id.panenku.app.ui.components.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.panenku.app.data.*
import id.panenku.app.ui.theme.*


@Composable
fun ProductFormScreen(product: Product?, onBack: () -> Unit, onSave: (Product) -> Unit) {
    var name by rememberSaveable(product?.id) { mutableStateOf(product?.name.orEmpty()) }
    var category by rememberSaveable(product?.id) { mutableStateOf(product?.commodity ?: "Sayur") }
    var description by rememberSaveable(product?.id) { mutableStateOf(product?.description.orEmpty()) }
    var price by rememberSaveable(product?.id) { mutableStateOf(product?.price?.toString().orEmpty()) }
    var stock by rememberSaveable(product?.id) { mutableStateOf(product?.physical?.toString().orEmpty()) }
    var date by rememberSaveable(product?.id) { mutableStateOf(product?.readyDate ?: "23 Juni 2026") }
    var photo by rememberSaveable(product?.id) { mutableStateOf(product?.photoUri) }
    val id = rememberSaveable { product?.id ?: "produk-${java.util.UUID.randomUUID()}" }
    val reserved = product?.reserved ?: 0
    val valid = name.isNotBlank() && (price.toLongOrNull() ?: 0) > 0 && (stock.toIntOrNull() ?: -1) >= reserved && date.isNotBlank()
    val publishable = valid && ((stock.toIntOrNull() ?: 0) > reserved) && (product != null || photo != null)
    fun save(status: ProductStatus) {
        onSave(Product(id, name, category, description, price.toLongOrNull() ?: 0, stock.toIntOrNull() ?: 0,
            reserved, status, product?.imageKey ?: "shallot", date, photo))
    }
    Page(if (product == null) "Buat produk baru" else "Edit & Upload produk", "Toko Sobianto · Informasi panen", onBack) {
        item { Panel("Foto produk") {
            LocalPhoto(photo, product?.imageKey ?: "shallot", Modifier.fillMaxWidth().height(190.dp))
            MediaPicker("Ganti / tambah foto produk", photo, { photo = it })
        } }
        item { Panel("Informasi panen") {
            Field("Nama produk", name, { name = it }, "Contoh: Bawang Merah")
            Text("Komoditas", style = MaterialTheme.typography.labelLarge)
            FilterChips(listOf("Sayur", "Buah", "Cabai", "Bawang"), category, { category = it })
            Field("Deskripsi & kualitas", description, { description = it }, multiline = true)
            Field("Harga per kg (Rp)", price, { price = it.filter(Char::isDigit).take(10) }, keyboard = KeyboardType.Number)
            Field("Stok fisik (kg)", stock, { stock = it.filter(Char::isDigit).take(7) }, keyboard = KeyboardType.Number,
                error = if (stock.isNotBlank() && (stock.toIntOrNull() ?: -1) < reserved) "Stok minimal $reserved kg karena sudah dipesan." else null)
            Text("Dipesan $reserved kg · Tersedia ${((stock.toIntOrNull() ?: 0) - reserved).coerceAtLeast(0)} kg", style = MaterialTheme.typography.bodySmall)
            Field("Tanggal siap kirim", date, { date = it }, "23 Juni 2026")
        } }
        item { Panel("Syarat penerbitan") {
            Notice("Toko demo terverifikasi", "Identitas disetujui, akun aktif, dan rekening/QRIS lengkap pada data contoh.")
            Text("Foto, nama, komoditas, harga, dan stok tersedia wajib lengkap untuk diterbitkan.", style = MaterialTheme.typography.bodySmall)
            PrimaryButton("Terbitkan produk", { save(ProductStatus.ACTIVE) }, publishable)
            SecondaryButton("Simpan sebagai draf", { save(ProductStatus.DRAFT) }, enabled = valid)
            if (product?.status == ProductStatus.ACTIVE) SecondaryButton("Nonaktifkan produk", { save(ProductStatus.INACTIVE) }, enabled = valid)
            if (!valid) Text("Lengkapi nama, harga, stok, dan tanggal untuk menyimpan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } }
        item { Text("Harga baru hanya berlaku untuk transaksi baru. Pesanan lama tetap memakai rincian saat dibuat.", style = MaterialTheme.typography.bodySmall) }
    }
}
