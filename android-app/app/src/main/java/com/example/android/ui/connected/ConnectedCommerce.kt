package id.panenku.app.ui.connected

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.gson.JsonObject
import id.panenku.app.data.remote.*
import id.panenku.app.data.rupiah
import id.panenku.app.ui.components.*

@Composable internal fun ConnectedCatalog(s: ConnectedState, vm: ConnectedViewModel, buyer: Boolean, cart: () -> Unit, edit: (JsonObject?) -> Unit) {
    Page(if (buyer) "Hasil panen segar" else "Stok & produk") {
        item { SecondaryButton("Muat ulang", { vm.catalog() }) }
        item { if (buyer) PrimaryButton("Keranjang", cart) else PrimaryButton("Tambah produk", { edit(null) }) }
        if (s.products.isEmpty() && !s.busy) item { Text("Belum ada produk yang tersedia.") }
        items(s.products, key = { it.text("id") }) { p ->
            var quantity by rememberSaveable(p.text("id")) { mutableStateOf("1") }
            Panel(p.text("name")) {
                if (p.text("photoAccessUrl").isNotBlank()) AsyncImage(p.text("photoAccessUrl"), p.text("name"), Modifier.fillMaxWidth().height(160.dp))
                Text(p.text("storeName"))
                Text("${rupiah(p.long("pricePerKg"))} / kg")
                Text("Stok tersedia: ${p.text("stockAvailable")} kg · Siap ${p.text("readyDate")}")
                if (buyer) {
                    Field("Jumlah (kg)", quantity, { quantity = it }, "Contoh: 0,25", keyboard = KeyboardType.Decimal)
                    PrimaryButton("Tambah ke keranjang", { vm.add(p.text("id"), quantity) }, p.flag("availableForPurchase"))
                } else {
                    Text("Status: ${p.text("publicationStatus")}")
                    SecondaryButton("Edit produk", { edit(p) })
                }
            }
        }
        if (s.more) item { SecondaryButton("Muat produk berikutnya", { vm.catalog(s.page + 1) }) }
    }
}
@Composable internal fun ConnectedCart(s: ConnectedState, vm: ConnectedViewModel, back: () -> Unit, checkout: () -> Unit) {
    Page("Keranjang", onBack = back) {
        item { SecondaryButton("Muat ulang keranjang", vm::cart) }
        if (s.busy) item { Text("Memuat keranjang…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        s.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (s.cart.list("items").isEmpty() && !s.busy && s.error == null) item { Text("Keranjangmu masih kosong.") }
        items(s.cart.list("items"), key = { it.text("id") }) { p ->
            var quantity by rememberSaveable(p.text("id"), p.text("quantityKg")) { mutableStateOf(p.text("quantityKg")) }
            Panel(p.text("name")) {
                Text(p.text("storeName")); Text(rupiah(p.long("itemSubtotal")))
                if (!p.flag("available")) Text(p.text("unavailableReason"), color = MaterialTheme.colorScheme.error)
                Field("Jumlah (kg)", quantity, { quantity = it }, keyboard = KeyboardType.Decimal)
                SecondaryButton("Simpan jumlah", { vm.quantity(p.text("id"), quantity) })
                TextButton(onClick = { vm.remove(p.text("id")) }) { Text("Hapus") }
            }
        }
        if (s.cart.list("items").isNotEmpty()) item {
            PrimaryButton("Lanjut checkout", checkout, !s.busy && !s.cart.flag("hasUnavailableItems"))
        }
    }
}
@Composable internal fun ConnectedCheckout(s: ConnectedState, vm: ConnectedViewModel, back: () -> Unit, done: () -> Unit) {
    var method by rememberSaveable { mutableStateOf("pickup") }
    var district by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var slot by rememberSaveable { mutableStateOf("") }
    Page("Checkout", "Pilih layanan dan periksa total pesanan.", back) {
        item { Panel("Metode penerimaan") {
            Row {
                FilterChip(method == "pickup", { method = "pickup"; date = ""; slot = ""; vm.invalidateSchedules() }, { Text("Ambil sendiri") })
                Spacer(Modifier.width(8.dp))
                FilterChip(method == "delivery", { method = "delivery"; date = ""; slot = ""; vm.invalidateSchedules() }, { Text("Antar") }, enabled = s.shippingEnabled && s.deliveryAreas.isNotEmpty())
            }
            if (method == "delivery") {
                Text("Kecamatan layanan toko")
                s.deliveryAreas.forEach { area ->
                    FilterChip(district == area, { district = area; date = ""; slot = ""; vm.invalidateSchedules() }, { Text(area) })
                }
                Field("Alamat lengkap", address, { address = it; vm.clearQuote() }, multiline = true)
            }
            PrimaryButton("Cari jadwal tersedia", { date = ""; slot = ""; vm.schedules(method, district) }, method == "pickup" || district.isNotBlank())
        } }
        s.fulfillment?.let { options ->
            item {
                Text("Zona waktu: ${options.text("timeZone")}")
                Text("Lokasi pengambilan: ${options.text("pickupLocation")}")
                if (options.list("dates").all { it.getAsJsonArray("timeSlots")?.size() == 0 }) Text("Belum ada jadwal tersedia. Muat ulang pilihan dari toko.")
            }
            items(options.list("dates")) { day ->
                Panel(day.text("date")) {
                    day.getAsJsonArray("timeSlots")?.forEach { value ->
                        val label = value.asString
                        FilterChip(date == day.text("date") && slot == label, { date = day.text("date"); slot = label; vm.clearQuote() }, { Text(label) })
                    }
                }
            }
        }
        if (date.isNotBlank()) item { PrimaryButton("Periksa total", { vm.preview(method, district, address, date, slot) }, method == "pickup" || address.isNotBlank()) }
        s.quote?.let { quote -> item { CheckoutSummary(quote, s.busy) { vm.checkout(done) } } }
    }
}

@Composable internal fun CheckoutSummary(quote: JsonObject, busy: Boolean, confirm: () -> Unit) {
    var agreed by rememberSaveable(quote.toString()) { mutableStateOf(false) }
    Panel("Ringkasan pembayaran") {
        quote.list("items").forEach { item ->
            Text("${item.text("name")} · ${item.text("quantityKg")} kg · ${rupiah(item.long("pricePerKg"))}/kg")
            Text("Subtotal produk: ${rupiah(item.long("itemSubtotal"))}")
        }
        Text("Produk: ${rupiah(quote.long("subtotalItems"))}")
        Text("Ongkir: ${rupiah(quote.long("deliveryFee"))}")
        Text("Total: ${rupiah(quote.long("totalPayment"))}")
        Text("Limit transaksi: ${rupiah(quote.long("transactionLimitApplied"))}")
        Text("Jadwal: ${quote.text("scheduledDate")} · ${quote.text("scheduledTimeSlot")} (${quote.text("timeZone")})")
        Text(if (quote.text("fulfillmentMethod") == "delivery")
            "Antar: ${quote.text("deliveryDistrict")} · ${quote.text("deliveryAddress")}" else "Ambil di: ${quote.text("pickupLocation")}")
        if (!quote.flag("canCheckout")) Text("Checkout belum tersedia: ${quote.text("validationCode")}", color = MaterialTheme.colorScheme.error)
        Row {
            Checkbox(agreed, { agreed = it }, enabled = !busy && quote.flag("canCheckout"))
            Text("Saya menyetujui produk, total pembayaran, dan jadwal di atas.")
        }
        PrimaryButton("Buat pesanan", confirm, !busy && agreed && quote.flag("canCheckout"))
    }
}
