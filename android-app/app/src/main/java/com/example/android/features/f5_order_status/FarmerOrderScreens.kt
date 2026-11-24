package id.panenku.app.features.f5_order_status

import id.panenku.app.features.f6_payment_verification.*
import id.panenku.app.ui.components.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.panenku.app.data.*
import id.panenku.app.ui.theme.PanenkuTheme


@Composable
fun OrderListScreen(orders: List<Order>, onBack: () -> Unit, onDetail: (String) -> Unit, onRefund: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("Semua") }
    val filtered = orders.filter { when (filter) {
        "Diproses" -> it.status in listOf(OrderStatus.PROCESSING, OrderStatus.DELIVERY)
        "Selesai" -> it.status == OrderStatus.COMPLETED
        "Dibatalkan" -> it.status == OrderStatus.CANCELLED
        else -> true
    } }
    Page("Pesanan Toko", "Kelola Permintaan", onBack) {
        item { FilterChips(listOf("Semua", "Diproses", "Selesai", "Dibatalkan"), filter, { filter = it }) }
        items(filtered, key = { it.id }) { order -> Panel {
            Text("#${order.id}", style = MaterialTheme.typography.bodySmall)
            StatusBadge(order.status.label, if (order.status == OrderStatus.PENDING || order.status == OrderStatus.REVIEW) Tone.WARNING else Tone.SUCCESS)
            Text(order.buyer, style = MaterialTheme.typography.titleMedium)
            KeyValue("Produk", order.lines.joinToString(" + ") { "${it.name} ${it.quantity} kg" })
            KeyValue("Total", rupiah(order.total), true)
            KeyValue("Pengiriman", order.schedule)
            PrimaryButton(when (order.status) { OrderStatus.REVIEW -> "Periksa bukti"; OrderStatus.PROCESSING -> "Siapkan pesanan"; else -> "Lihat detail" }, { onDetail(order.id) })
        } }
        if (filtered.isEmpty()) item { EmptyState("Belum ada pesanan pada filter ini") }
        item { SecondaryButton("Tinjau pengajuan refund contoh", onRefund) }
    }
}

@Composable
fun OrderDetailScreen(order: Order, onBack: () -> Unit, onChange: (OrderStatus, String) -> Unit, onSchedule: () -> Unit) {
    var reason by rememberSaveable(order.id) { mutableStateOf("") }
    val title = when (order.status) { OrderStatus.REVIEW -> "Tinjau bukti pembayaran"; OrderStatus.PROCESSING -> "Penyiapan Pesanan"; else -> "Detail pesanan masuk" }
    Page(title, "#${order.id} · ${order.buyer}", onBack) {
        item { Notice(order.status.label, when (order.status) {
            OrderStatus.PENDING -> "Belum dibayar. Pembeli membayar setelah pesanan diterima petani."
            OrderStatus.PAYMENT -> "Pesanan diterima. Menunggu pembeli mengunggah bukti pembayaran."
            OrderStatus.REVIEW -> "Cocokkan bukti dan dana masuk ke rekening Anda sebelum menyetujui."
            OrderStatus.PROCESSING -> "Pembayaran disetujui. Siapkan pesanan sesuai jadwal."
            OrderStatus.DELIVERY -> "Pesanan sedang diantar. Menunggu konfirmasi penerimaan pembeli."
            else -> order.note.ifBlank { "Status terakhir pesanan." }
        }, if (order.status in listOf(OrderStatus.PENDING, OrderStatus.REVIEW)) Tone.WARNING else Tone.SUCCESS) }
        item { Panel("Pembeli & pemenuhan") {
            Text(order.buyer, style = MaterialTheme.typography.titleMedium)
            StatusBadge("Antar oleh petani")
            Text(order.schedule, style = MaterialTheme.typography.labelLarge)
            Text("Alamat pengiriman", style = MaterialTheme.typography.labelLarge)
            Text("Gang H. Gumat 1 no. 14, Lowokwaru, Kota Malang, Jawa Timur. Catatan: dekat masjid Al Hidayah.", style = MaterialTheme.typography.bodyMedium)
            if (order.note.isNotEmpty()) Notice("Catatan terbaru", order.note, Tone.INFO)
        } }
        item { OrderSummary(order) }
        when (order.status) {
            OrderStatus.PENDING -> item { Panel("Keputusan petani") {
                PrimaryButton("Terima pesanan", { onChange(OrderStatus.PAYMENT, "") })
                Text("Setelah diterima, pembeli memiliki waktu 24 jam untuk mengunggah bukti pembayaran.", style = MaterialTheme.typography.bodySmall)
                Field("Alasan penolakan (wajib jika menolak)", reason, { reason = it }, "Tulis alasan penolakan", multiline = true)
                SecondaryButton("Tolak dengan alasan", { onChange(OrderStatus.CANCELLED, reason) })
            } }
            OrderStatus.PAYMENT -> item { Panel("Pembayaran demo") {
                Text("Untuk mencoba alur pemeriksaan, simulasikan bukti pembayaran pembeli.", style = MaterialTheme.typography.bodyMedium)
                PrimaryButton("Simulasikan bukti masuk", { onChange(OrderStatus.REVIEW, "") })
            } }
            OrderStatus.REVIEW -> {
                item { EvidenceCard(amount = order.total) }
                item { Panel("Nominal yang diharapkan") { KeyValue("Produk", rupiah(order.subtotal)); KeyValue("Ongkir", rupiah(order.deliveryFee)); KeyValue("Total harus diterima", rupiah(order.total), true) } }
                item { Panel("Keputusan pemeriksaan") {
                    PrimaryButton("Setujui pembayaran", { onChange(OrderStatus.PROCESSING, "") })
                    Field("Alasan penolakan (wajib)", reason, { reason = it }, "Contoh: nominal penerima tidak jelas", multiline = true)
                    SecondaryButton("Tolak bukti dengan alasan", { onChange(OrderStatus.PAYMENT, reason) })
                    Text("Penolakan meminta pembeli mengunggah bukti baru. Pesanan tetap menunggu pembayaran.", style = MaterialTheme.typography.bodySmall)
                } }
            }
            OrderStatus.PROCESSING -> {
                item { Panel("Tahap yang tepat untuk setiap layanan") {
                    Timeline(listOf("Diproses → Diantar" to "Tandai saat pesanan mulai dikirim ke pembeli.", "Diantar → Menunggu Penerimaan" to "Pembeli mengonfirmasi barang telah diterima.", "Ambil sendiri → Siap Diambil" to "Gunakan tahap sesuai jenis layanan pesanan."))
                    PrimaryButton("Tandai mulai diantar", { onChange(OrderStatus.DELIVERY, "") })
                    SecondaryButton("Usulkan perubahan jadwal", onSchedule)
                } }
                item { Notice("Bukan selesai saat diserahkan", "Status selesai mengikuti konfirmasi penerimaan pembeli.") }
                item { Panel("Jika gagal memenuhi pesanan") {
                    Field("Alasan gagal pemenuhan (wajib)", reason, { reason = it }, "Tulis kendala dan upaya yang dilakukan", multiline = true)
                    SecondaryButton("Laporkan gagal pemenuhan", { onChange(OrderStatus.CANCELLED, reason) })
                    Text("Jika sudah dibayar, pengembalian dana ditangani terpisah. Demo tidak memindahkan dana.", style = MaterialTheme.typography.bodySmall)
                } }
            }
            else -> item { Notice("Status pesanan", "${order.status.label}. Tahap berikutnya memerlukan interaksi pembeli atau backend.") }
        }
    }
}




@Preview(showBackground = true, widthDp = 375, heightDp = 1100)
@Composable private fun OrderPreview() { PanenkuTheme { OrderDetailScreen(SampleData.order, {}, { _, _ -> }, {}) } }

@Preview(showBackground = true, widthDp = 375, heightDp = 1100)
@Composable private fun ProcessingPreview() { PanenkuTheme { OrderDetailScreen(SampleData.order.copy(status = OrderStatus.PROCESSING), {}, { _, _ -> }, {}) } }
