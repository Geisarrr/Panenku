package id.panenku.app.ui.dashboard

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
fun FarmerHomeScreen(data: DashboardData, onProducts: () -> Unit, onOrders: () -> Unit, onEdit: (String) -> Unit, onProfile: () -> Unit) {
    Page("Pantau hasil penjualan tani Anda", "Selamat pagi, Pak Darto", subtitleFirst = true) {
        item { AdaptiveMetrics(listOf(
            "Pesanan Baru" to data.orders.count { it.status == OrderStatus.PENDING }.toString(),
            "Jadwal Kirim" to data.orders.count { it.status == OrderStatus.PROCESSING }.toString(),
            "Stok Panen" to data.products.count { it.available < 15 }.toString(), "Pembayaran" to "Tinjau bukti"
        )) { when (it) { 2 -> onProducts(); else -> onOrders() } } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Stok panen aktif", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onProducts) { Text("Lihat Semua") }
        } }
        items(data.products.filter { it.status == ProductStatus.ACTIVE }, key = { it.id }) { product ->
            Panel { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LocalPhoto(product.photoUri, product.imageKey, Modifier.size(90.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    StatusBadge(if (product.available <= 0) "Habis" else if (product.available < 15) "Hampir Habis" else "Tersedia",
                        if (product.available < 15) Tone.WARNING else Tone.INFO)
                    Text("Stok: ${product.available} kg", style = MaterialTheme.typography.bodySmall)
                    Text("${rupiah(product.price)}/kg", color = MaterialTheme.colorScheme.secondary)
                }
            }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Edit stok", { onEdit(product.id) }, modifier = Modifier.weight(1f))
                    SecondaryButton("Detail", { onEdit(product.id) }, Modifier.weight(1f))
                } }
        }
        if (data.products.none { it.status == ProductStatus.ACTIVE }) item { EmptyState("Belum ada produk aktif", "Terbitkan produk dari menu Stok.") }
    }
}





@Preview(showBackground = true, widthDp = 375, heightDp = 956)
@Composable private fun HomePreview() { PanenkuTheme { FarmerHomeScreen(SampleData.data, {}, {}, {}, {}) } }

@Preview(showBackground = true, widthDp = 800, heightDp = 1000)
@Composable private fun HomeTabletPreview() { PanenkuTheme { FarmerHomeScreen(SampleData.data, {}, {}, {}, {}) } }
