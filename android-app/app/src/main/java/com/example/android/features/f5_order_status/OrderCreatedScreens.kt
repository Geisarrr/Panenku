package id.panenku.app.features.f5_order_status

import id.panenku.app.features.f3_delivery_schedule.*
import id.panenku.app.features.f4_checkout.*
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
fun BuyerOrderCreatedScreen(order: BuyerOrder, onBack: () -> Unit, onDetail: () -> Unit,
                            onStore: () -> Unit, onAcceptDemo: () -> Unit) {
    Page("Pesanan baru", onBack = onBack) {
        item { Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(painterResource(R.drawable.order_success_illustration), "Ilustrasi panen berhasil", Modifier.fillMaxWidth().height(180.dp))
            Text("Pesanan berhasil dibuat", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary)
            StatusBadge(order.order.status.label)
            Text("Pesananmu sudah terkirim ke petani. Pembayaran dilakukan setelah petani menerima.", style = MaterialTheme.typography.bodyMedium)
        } }
        item { Panel {
            KeyValue("Nomor pesanan", "#${order.id}"); KeyValue("Petani", order.farmerName)
            KeyValue("Isi pesanan", "${order.order.lines.size} produk · ${order.order.lines.sumOf { it.quantity }} kg")
            KeyValue(order.fulfillment.label, order.order.schedule); HorizontalDivider(); KeyValue("Total pesanan", rupiah(order.total), true)
        } }
        item { Panel("Apa selanjutnya?") {
            Timeline(listOf("Tunggu petani menerima" to "Batas ${buyerDate(order.createdAt.plus(Duration.ofHours(24)))} (24 jam).",
                "Bayar langsung ke petani" to "Setelah diterima, QRIS atau rekening petani tersedia pada pesanan.",
                "Unggah bukti pembayaran" to "Bukti diperiksa manual oleh petani. Pesanan diproses setelah disetujui."))
        } }
        if (order.order.status == OrderStatus.PENDING) item { SecondaryButton("Simulasikan petani menerima", onAcceptDemo) }
        item { PrimaryButton(if (order.order.status == OrderStatus.PAYMENT) "Lanjut pembayaran" else "Lihat Pesanan", onDetail) }
        item { SecondaryButton("Kembali ke Toko", onStore) }
    }
}


@Preview(showBackground = true, widthDp = 375, heightDp = 1000)
@Composable private fun CreatedPreview() { PanenkuTheme { BuyerOrderCreatedScreen(BuyerSamples.initial.orders.first(), {}, {}, {}, {}) } }
