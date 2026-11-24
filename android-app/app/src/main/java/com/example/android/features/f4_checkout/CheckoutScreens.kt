package id.panenku.app.features.f4_checkout

import id.panenku.app.ui.components.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.panenku.app.R
import id.panenku.app.data.*
import id.panenku.app.data.buyer.*
import id.panenku.app.ui.theme.PanenkuTheme
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID


@Composable
fun BuyerCheckoutScreen(data: BuyerData, now: Instant, limit: Long, onBack: () -> Unit,
                        onAddress: () -> Unit, onCreate: (CheckoutRequest) -> Unit) {
    var fulfillment by rememberSaveable { mutableStateOf(Fulfillment.DELIVERY) }
    val earliest = earliestCheckoutDate(data, now)
    val dates = generateSequence(earliest) { it.plusDays(1) }.filter { it.dayOfWeek != DayOfWeek.SUNDAY }.take(4).toList()
    var selectedDate by rememberSaveable { mutableStateOf(dates.getOrElse(1) { dates.first() }.toString()) }
    var slot by rememberSaveable { mutableStateOf(deliverySlots.first()) }
    val requestId = rememberSaveable { UUID.randomUUID().toString() }
    val address = data.addresses.getOrNull(data.profile.selectedAddress)
    val subtotal = cartSubtotal(data); val total = subtotal + fulfillment.fee
    val inArea = fulfillment == Fulfillment.PICKUP || (address != null && canDeliverTo(address))
    val valid = data.cart.isNotEmpty() && address != null && inArea && total <= limit && limit > 0 && LocalDate.parse(selectedDate) in dates
    Page("Checkout", "Periksa pesanan sebelum dibuat", onBack) {
        item { Text("1 Buat pesanan  →  2 Konfirmasi petani  →  3 Bayar langsung", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
        if (data.cart.isEmpty()) item { EmptyState("Keranjang masih kosong", "Tambahkan produk dari profil toko sebelum checkout.") }
        else {
            item { Panel("Cara terima pesanan") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Fulfillment.entries.forEach { option -> OutlinedCard(onClick = {
                        fulfillment = option; slot = if (option == Fulfillment.DELIVERY) deliverySlots.first() else pickupSlots.first()
                    }, modifier = Modifier.weight(1f), colors = CardDefaults.outlinedCardColors(containerColor =
                        if (option == fulfillment) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(12.dp)) {
                            RadioButton(option == fulfillment, onClick = null)
                            Text(option.label, style = MaterialTheme.typography.titleMedium)
                            Text("Ongkir ${rupiah(option.fee)}", style = MaterialTheme.typography.bodySmall)
                        }
                    } }
                }
                Text("Lokasi ambil: Kebun Darto, Lowokwaru, Malang.", style = MaterialTheme.typography.bodySmall)
            } }
            item { Panel(if (fulfillment == Fulfillment.DELIVERY) "Alamat pengiriman" else "Kontak pengambilan") {
                if (address != null) { Text(address.recipient, style = MaterialTheme.typography.titleMedium); Text(address.street); Text("Catatan: ${address.courierNote}", style = MaterialTheme.typography.bodySmall) }
                SecondaryButton("Ubah alamat", onAddress)
                Notice(if (inArea) "Layanan tersedia" else "Alamat di luar area antar", if (inArea) "Pilihan layanan dapat digunakan." else "Pilih alamat Lowokwaru atau ambil sendiri.", if (inArea) Tone.SUCCESS else Tone.WARNING)
            } }
            item { Panel(if (fulfillment == Fulfillment.DELIVERY) "Jadwal pengiriman" else "Jadwal pengambilan") {
                Text("Produk siap mulai ${scheduleLabel(earliest, "").trim(' ', '·')}", style = MaterialTheme.typography.bodySmall)
                FilterChips(dates.map { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("id-ID"))) },
                    LocalDate.parse(selectedDate).format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("id-ID"))),
                    { label -> selectedDate = dates.first { it.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("id-ID"))) == label }.toString() })
                FilterChips(if (fulfillment == Fulfillment.DELIVERY) deliverySlots else pickupSlots, slot, { slot = it })
                Text("Toko melayani Senin–Sabtu. Jadwal mengikuti tanggal siap produk dan slot layanan.", style = MaterialTheme.typography.bodySmall)
            } }
            item { Panel("Ringkasan pesanan · ${data.cart.size} produk") {
                data.cart.forEach { (id, quantity) ->
                    val product = data.products.first { it.id == id }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProductImage(product.imageKey, Modifier.size(52.dp))
                        Column(Modifier.weight(1f)) { Text(product.name, style = MaterialTheme.typography.labelLarge); Text("$quantity kg × ${rupiah(product.price)}/kg", style = MaterialTheme.typography.bodySmall) }
                        Text(rupiah(product.price * quantity), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } }
            item { Panel("Rincian total") {
                KeyValue("Subtotal produk", rupiah(subtotal)); KeyValue("Ongkos kirim petani", rupiah(fulfillment.fee)); KeyValue("Total pesanan", rupiah(total), true)
                Text("Batas checkout ${rupiah(limit)}, termasuk ongkir.", style = MaterialTheme.typography.bodySmall)
                if (total > limit) Notice("Total melebihi batas", "Kurangi jumlah produk pada keranjang.", Tone.ERROR)
            } }
            item { Notice("Belum perlu membayar", "Pesanan menunggu penerimaan petani maksimal 24 jam. Setelah diterima, bayar langsung ke QRIS atau rekening petani, lalu unggah bukti.") }
            item { PrimaryButton("Buat Pesanan", { address?.let { onCreate(CheckoutRequest(requestId, it.id, fulfillment, LocalDate.parse(selectedDate), slot)) } }, valid) }
        }
    }
}



@Preview(showBackground = true, widthDp = 375, heightDp = 1000)
@Composable private fun CheckoutPreview() { PanenkuTheme { BuyerCheckoutScreen(BuyerSamples.initial.copy(cart = mapOf("sawi" to 20, "tomat" to 10)), BuyerSamples.now, 10000000, {}, {}, {}) } }
