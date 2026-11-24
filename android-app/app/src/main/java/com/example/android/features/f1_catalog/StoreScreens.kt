package id.panenku.app.features.f1_catalog

import id.panenku.app.ui.account.*
import id.panenku.app.ui.components.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.panenku.app.data.*
import id.panenku.app.data.buyer.*
import id.panenku.app.ui.theme.PanenkuTheme
import java.util.Locale


@Composable
fun BuyerStoreScreen(data: BuyerData, onBack: () -> Unit, onAdd: (String) -> Unit, onCart: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf("Produk") }
    var location by rememberSaveable { mutableStateOf(false) }
    val rating = (4.9 * 48 + data.reviews.sumOf { it.rating }) / (48 + data.reviews.size)
    Page("Profil toko", "PEMBELI · PANENKU · Segar langsung dari kebun", onBack) {
        item { Panel {
            Text("Toko Sobianto", style = MaterialTheme.typography.titleLarge)
            Text("Darto Sobianto", style = MaterialTheme.typography.bodySmall)
            StatusBadge("Petani terverifikasi")
            MenuRow("Lowokwaru, Kota Malang", "Lokasi ambil: Kebun Darto", Icons.Outlined.LocationOn) { location = true }
            KeyValue("Jam buka", "Sen–Sab · 08.00–16.00")
            Text("Antar area Lowokwaru · Rp2.500/pesanan. Slot antar 09.00–12.00. Ambil sendiri Rp0.", style = MaterialTheme.typography.bodySmall)
            KeyValue("Penilaian pembeli", "★ ${String.format(Locale.forLanguageTag("id-ID"), "%.1f", rating)} · ${48 + data.reviews.size} ulasan", true)
        } }
        item { FilterChips(listOf("Produk", "Ulasan"), tab, { tab = it }) }
        if (tab == "Produk") items(data.products, key = { it.id }) { product -> Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LocalPhoto(product.photoUri, product.imageKey, Modifier.size(80.dp))
                Column(Modifier.weight(1f)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    Text("${rupiah(product.price)}/kg", color = MaterialTheme.colorScheme.secondary)
                    Text("Tersedia ${product.available} kg · ${product.readyDate}", style = MaterialTheme.typography.bodySmall)
                }
            }
            PrimaryButton(if (product.available == 0) "Stok tidak tersedia" else "Tambah ke keranjang · 1 kg",
                { onAdd(product.id) }, product.available > 0 && product.status == ProductStatus.ACTIVE)
        } }
        if (tab == "Ulasan") {
            items(data.reviews.reversed(), key = { it.orderId }) { review -> Panel {
                Text(review.author, style = MaterialTheme.typography.titleMedium); RatingStars(review.rating)
                Text(review.comment.ifBlank { "Tanpa komentar tambahan." }); Text(buyerDate(review.submittedAt), style = MaterialTheme.typography.bodySmall)
            } }
            item { Panel("Contoh ulasan pembeli") {
                Text("Dapur Melati · 19 Juni 2026", style = MaterialTheme.typography.labelLarge); RatingStars(5)
                Text("Sayurnya segar dan ditimbang sesuai pesanan. Pengiriman tepat di jam layanan.")
                HorizontalDivider(); Text("Warung Bu Ani · 18 Juni 2026", style = MaterialTheme.typography.labelLarge); RatingStars(5)
                Text("Sawi renyah, kemasan rapi. Akan pesan lagi untuk stok warung.")
                Text("Menampilkan 2 contoh dari 48 ulasan awal. Ulasan baru berasal dari pesanan demo yang selesai.", style = MaterialTheme.typography.bodySmall)
            } }
        }
        if (data.cart.isNotEmpty()) item { PrimaryButton("Lihat keranjang · ${data.cart.values.sum()} kg", onCart) }
    }
    if (location) AlertDialog(onDismissRequest = { location = false }, title = { Text("Lokasi Kebun Darto") },
        text = { Text("Lowokwaru, Kota Malang. Pengambilan di gerbang kebun, sebelah pos warga. Jadwal mengikuti layanan toko.") },
        confirmButton = { TextButton({ location = false }) { Text("Tutup") } })
}


@Preview(showBackground = true, widthDp = 800, heightDp = 1000)
@Composable private fun BuyerStoreTabletPreview() { PanenkuTheme { BuyerStoreScreen(BuyerSamples.initial, {}, {}, {}) } }
