package id.panenku.app.features.f5_order_status

import id.panenku.app.features.f1_catalog.*
import id.panenku.app.ui.components.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.tooling.preview.Preview
import id.panenku.app.data.*
import id.panenku.app.data.buyer.*
import id.panenku.app.ui.theme.PanenkuTheme
import java.time.Instant


@Composable
fun BuyerOrdersScreen(data: BuyerData, onDetail: (String) -> Unit, onReview: (String) -> Unit,
                      onReorder: (String) -> Unit, onUnavailable: (String) -> Unit, onSchedule: ((String) -> Unit)? = null) {
    var selected by rememberSaveable { mutableStateOf("Semua") }
    val orders = data.orders.filter { when (selected) {
        "Aktif" -> it.order.status !in listOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED)
        "Selesai" -> it.order.status == OrderStatus.COMPLETED
        else -> true
    } }
    Page("Pesanan", "Pantau pesanan hasil panenmu") {
        item { FilterChips(listOf("Semua", "Aktif", "Selesai"), selected, { selected = it }) }
        if (orders.isEmpty()) item { EmptyState("Belum ada pesanan pada filter ini") }
        items(orders, key = { it.id }) { order -> Panel {
            Text("#${order.id}", style = MaterialTheme.typography.labelLarge)
            StatusBadge(order.order.status.label, when (order.order.status) {
                OrderStatus.PENDING, OrderStatus.REVIEW -> Tone.WARNING
                OrderStatus.PAYMENT -> Tone.INFO
                else -> Tone.SUCCESS
            })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProductImage("cabbage", Modifier.size(42.dp)); ProductImage("tomato", Modifier.size(42.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sawi Putih & Tomat Sayur", style = MaterialTheme.typography.labelLarge)
                    Text("2 produk · 30 kg · ${order.farmerName}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(when (order.order.status) {
                OrderStatus.PENDING -> "Menunggu petani mengonfirmasi pesanan."
                OrderStatus.PAYMENT -> "Petani menyetujui. Lanjutkan pembayaran dan unggah bukti."
                OrderStatus.REVIEW -> "Bukti diterima, menunggu pemeriksaan petani."
                OrderStatus.PROCESSING -> "Pembayaran disetujui petani. Pesanan sedang disiapkan."
                OrderStatus.COMPLETED -> "Pesanan diterima. Bagaimana hasil panennya?"
                else -> order.order.status.label
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            KeyValue("Total pesanan", rupiah(order.total), true)
            SecondaryButton("Lihat Pesanan", { onDetail(order.id) })
            if (order.proposal?.status == ProposalStatus.PENDING) PrimaryButton("Tanggapi perubahan jadwal", { if (onSchedule != null) onSchedule(order.id) else onDetail(order.id) })
            if (order.order.status == OrderStatus.COMPLETED) {
                PrimaryButton(if (data.reviewFor(order.id) == null) "Beri Ulasan" else "Lihat Ulasan", { onReview(order.id) })
                SecondaryButton("Beli Lagi", { onReorder(order.id) })
            } else if (order.order.status == OrderStatus.PAYMENT) PrimaryButton("Bayar & Unggah Bukti", { onDetail(order.id) })
        } }
    }
}

@Composable
fun BuyerOrderDetailScreen(order: BuyerOrder, refund: BuyerRefund?, review: BuyerReview?, now: Instant,
                           onBack: () -> Unit, onReview: () -> Unit, onReorder: () -> Unit, onRefund: () -> Unit, onStore: () -> Unit,
                           onAcceptDemo: (() -> Unit)? = null, onActiveFlow: (() -> Unit)? = null) {
    var track by rememberSaveable { mutableStateOf(false) }
    Page("Detail Pesanan", "#${order.id}", onBack) {
        item { Panel("Alamat Pengiriman") {
            Text(order.address.recipient, style = MaterialTheme.typography.titleMedium)
            Text(order.address.street, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(); Text("Catatan untuk Kurir", style = MaterialTheme.typography.labelLarge)
            Text(order.address.courierNote, style = MaterialTheme.typography.bodyMedium)
        } }
        item { Panel {
            StatusBadge(order.order.status.label)
            Text("2 Produk · ${order.storeName}", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val step = when (order.order.status) {
                    OrderStatus.PENDING, OrderStatus.PAYMENT -> 0
                    OrderStatus.REVIEW -> 1
                    OrderStatus.PROCESSING -> 2
                    OrderStatus.DELIVERY -> 3
                    OrderStatus.COMPLETED -> 4
                    else -> -1
                }
                listOf(Icons.Outlined.Payment, Icons.Outlined.Inventory2, Icons.Outlined.LocalShipping, Icons.Outlined.Home, Icons.Outlined.Verified).forEachIndexed { index, icon ->
                    Surface(color = if (index <= step) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraLarge) {
                        Icon(icon, null, Modifier.padding(9.dp).size(24.dp), tint = if (index <= step) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SecondaryButton("Lacak Pengiriman", { track = true })
            SecondaryButton("Lihat profil toko & ulasan", onStore)
        } }
        item { Panel("Ringkasan Pesanan · 2 produk") { BuyerOrderItems(order); PrimaryButton("Beli Lagi", onReorder) } }
        item { Panel("Ringkasan Pembayaran") {
            KeyValue("Subtotal (2 Produk)", rupiah(order.order.subtotal))
            if (order.serviceFee > 0) KeyValue("Biaya Pemesanan", rupiah(order.serviceFee))
            if (order.packagingFee > 0) KeyValue("Biaya Kemasan", rupiah(order.packagingFee))
            KeyValue("Ongkos Kirim", rupiah(order.order.deliveryFee)); KeyValue("Total Pembayaran", rupiah(order.total), true)
            HorizontalDivider(); KeyValue("Metode Pembayaran", order.paymentMethod)
            if (order.receivedAt != null) KeyValue("Diterima", buyerDate(order.receivedAt))
        } }
        if (order.order.status == OrderStatus.PENDING && onAcceptDemo != null) item { PrimaryButton("Simulasikan petani menerima", onAcceptDemo) }
        if (order.order.status in listOf(OrderStatus.PAYMENT, OrderStatus.REVIEW, OrderStatus.PROCESSING, OrderStatus.DELIVERY) && onActiveFlow != null) item { PrimaryButton("Lanjut pembayaran / pemenuhan", onActiveFlow) }
        if (order.order.status == OrderStatus.COMPLETED) {
            item { PrimaryButton(if (review == null) "Tulis Ulasan" else "Lihat Ulasan", onReview) }
            item { SecondaryButton(if (refund != null) "Lihat status refund" else "Ajukan refund", onRefund, enabled = refund != null || isRefundEligible(order, now)) }
            if (refund == null && !isRefundEligible(order, now)) item { Notice("Batas pengajuan refund berakhir", "Pengajuan normal tersedia selama 24 jam setelah pesanan diterima.", Tone.NEUTRAL) }
        }
    }
    if (track) AlertDialog(onDismissRequest = { track = false }, title = { Text("Riwayat pengiriman") },
        text = { Timeline(listOf("Pesanan dibuat" to "${order.storeName} · data contoh", "Status terakhir" to order.order.status.label,
            "Jadwal tersimpan" to order.order.schedule)) }, confirmButton = { TextButton({ track = false }) { Text("Tutup") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuyerReviewScreen(order: BuyerOrder, existing: BuyerReview?, onBack: () -> Unit,
                      onSubmit: (Int, Set<String>, String) -> Unit) {
    var rating by rememberSaveable(order.id) { mutableIntStateOf(0) }
    var tags by rememberSaveable(order.id) { mutableStateOf(listOf<String>()) }
    var comment by rememberSaveable(order.id) { mutableStateOf("") }
    Page("Ulasan Pesanan", "#${order.id} · Berikan ulasan singkat untuk pesanan ini", onBack) {
        item { Panel("Pesanan #${order.id}") { StatusBadge(order.order.status.label); BuyerOrderItems(order) } }
        if (existing != null) item { Panel("Ulasan sudah dikirim") {
            RatingStars(existing.rating); Text(existing.comment.ifBlank { "Tanpa komentar tambahan." })
            if (existing.improvements.isNotEmpty()) Text(existing.improvements.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            Text(buyerDate(existing.submittedAt), style = MaterialTheme.typography.bodySmall)
        } }
        else if (order.order.status != OrderStatus.COMPLETED) item { Notice("Pesanan belum selesai", "Ulasan dapat diberikan setelah pesanan selesai.", Tone.WARNING) }
        else {
            item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Bagaimana pengalaman belanja Anda?", style = MaterialTheme.typography.titleMedium)
                RatingStars(rating, { rating = it })
            } }
            item { Panel("Hal apa yang perlu ditingkatkan?") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Lainnya", "Aplikasi", "Customer Service", "Pengiriman", "Kelengkapan Produk", "Kemasan", "Pengalaman Aplikasi").forEach { tag ->
                        FilterChip(tag in tags, { tags = if (tag in tags) tags - tag else tags + tag }, label = { Text(tag) })
                    }
                }
                Field("Berikan Komentar", comment, { comment = it.take(1000) }, "Ceritakan pengalaman", multiline = true)
                Text("${comment.length}/1000 karakter", style = MaterialTheme.typography.bodySmall)
            } }
            item { PrimaryButton("Kirim", { onSubmit(rating, tags.toSet(), comment) }, rating in 1..5) }
        }
    }
}




@Preview(showBackground = true, widthDp = 375, heightDp = 1000)
@Composable private fun BuyerOrdersPreview() { PanenkuTheme { BuyerOrdersScreen(BuyerSamples.initial, {}, {}, {}, {}) } }

@Preview(showBackground = true, widthDp = 375, heightDp = 1000)
@Composable private fun BuyerReviewPreview() { PanenkuTheme { BuyerReviewScreen(BuyerSamples.historical, null, {}, { _, _, _ -> }) } }
