package id.panenku.app.features.f5_order_status

import id.panenku.app.ui.components.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
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
import id.panenku.app.ui.theme.PanenkuTheme


@Composable
fun AdminOrdersScreen(orders: List<Order>, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Semua") }
    var selected by rememberSaveable { mutableStateOf("") }
    val filtered = orders.filter { "${it.id} ${it.buyer} Darto Sobianto".contains(query, true) && when (filter) {
        "Pembayaran" -> it.status in listOf(OrderStatus.PAYMENT, OrderStatus.REVIEW)
        "Pemenuhan" -> it.status in listOf(OrderStatus.PROCESSING, OrderStatus.DELIVERY)
        "Refund" -> false
        else -> true
    } }
    val order = filtered.find { it.id == selected } ?: filtered.firstOrNull()
    Page("Pantau pesanan", "Pantauan transaksi lintas toko", onBack, true) {
        item { SearchField(query, { query = it }, "Cari ID pesanan / pembeli / toko") }
        item { FilterChips(listOf("Semua", "Pembayaran", "Pemenuhan", "Refund"), filter, { filter = it }) }
        items(filtered, key = { it.id }) { o -> Panel { MenuRow("#${o.id} · ${rupiah(o.total)}", "${o.status.label} · ${o.buyer}", Icons.AutoMirrored.Outlined.ReceiptLong) { selected = o.id } } }
        if (filtered.isEmpty()) item { EmptyState("Belum ada pesanan pada filter ini") }
        if (order != null) {
            item { Panel("Detail #${order.id}") {
                StatusBadge(order.status.label, Tone.WARNING); KeyValue("Pembeli", order.buyer); KeyValue("Petani / toko", "Darto Sobianto / Toko Sobianto"); KeyValue("Jadwal", order.schedule)
            } }
            item { OrderSummary(order) }
            item { Panel("Riwayat waktu & aktor · contoh") {
                Timeline(listOf("Pesanan dibuat" to "22 Juni, 10.00 · Rina · Stok ditahan", "Status saat ini: ${order.status.label}" to "Data mengikuti perubahan pada demo petani"))
            } }
        }
        item { Notice("Admin memantau, bukan menyetujui dana", "Pemeriksaan bukti pembayaran dilakukan petani. Admin tidak memindahkan dana.") }
    }
}
