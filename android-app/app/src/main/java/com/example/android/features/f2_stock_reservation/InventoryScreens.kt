package id.panenku.app.features.f2_stock_reservation

import id.panenku.app.ui.dashboard.*
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
fun ProductListScreen(products: List<Product>, onBack: () -> Unit, onCreate: () -> Unit, onEdit: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Semua") }
    val filtered = products.filter { it.name.contains(query, true) && (filter == "Semua" || it.status.label == filter) }
    Page("Stok & Produk", "Toko Sobianto · ${products.size} produk", onBack) {
        item { SearchField(query, { query = it }) }
        item { FilterChips(listOf("Semua", "Aktif", "Draf", "Nonaktif"), filter, { filter = it }) }
        item { PrimaryButton("+ Buat produk baru", onCreate) }
        item { Notice("Tersedia = fisik − dipesan", "Stok dipesan ditahan untuk pesanan aktif. Saat pesanan ditolak atau dibatalkan, stok ditahan dilepas satu kali.") }
        if (filtered.isEmpty()) item { EmptyState() }
        items(filtered, key = { it.id }) { product -> Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LocalPhoto(product.photoUri, product.imageKey, Modifier.size(72.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    Text("${rupiah(product.price)}/kg", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusBadge(product.status.label, if (product.status == ProductStatus.ACTIVE) Tone.SUCCESS else Tone.NEUTRAL)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Fisik" to product.physical, "Dipesan" to product.reserved, "Tersedia" to product.available).forEach { (label, amount) ->
                    Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(10.dp)) { Text(label, style = MaterialTheme.typography.bodySmall); Text("$amount kg", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary) }
                    }
                }
            }
            if (product.available == 0) StatusBadge("Tidak dapat dibeli · Stok habis", Tone.WARNING)
            SecondaryButton(if (product.status == ProductStatus.DRAFT) "Lanjutkan draf" else "Edit produk & stok", { onEdit(product.id) })
        } }
        item { Text("Produk draf dan nonaktif tidak tampil sebagai produk yang dapat dibeli.", style = MaterialTheme.typography.bodySmall) }
    }
}


@Preview(showBackground = true, widthDp = 375, heightDp = 956, fontScale = 1.5f)
@Composable private fun LargeTextStockPreview() { PanenkuTheme { ProductListScreen(SampleData.products, {}, {}, {}) } }
