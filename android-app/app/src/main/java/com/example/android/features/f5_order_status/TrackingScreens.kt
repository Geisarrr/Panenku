package id.panenku.app.features.f5_order_status

import id.panenku.app.ui.components.*

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.panenku.app.data.*
import id.panenku.app.data.buyer.*
import id.panenku.app.ui.theme.PanenkuTheme
import java.time.Instant


@Composable
private fun DeliveryTimeline(order: BuyerOrder) {
    val titles = if (order.fulfillment == Fulfillment.PICKUP) listOf("Pembayaran disetujui", "Pesanan disiapkan", "Siap Diambil di Kebun Darto", "Konfirmasi penerimaanmu", "Selesai")
    else listOf("Pembayaran disetujui", "Pesanan disiapkan", "Diantar oleh petani", "Tiba di alamat tujuan", "Konfirmasi penerimaanmu", "Selesai")
    val active = if (order.order.status == OrderStatus.COMPLETED) titles.lastIndex else when (order.tracking) {
        TrackingStage.PREPARING -> 1; TrackingStage.EN_ROUTE, TrackingStage.PICKUP_READY -> 2; TrackingStage.ARRIVED -> 3; TrackingStage.RECEIVED -> titles.lastIndex
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        titles.forEachIndexed { index, title -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (index <= active) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                Modifier.size(22.dp), tint = if (index <= active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column { Text(title, style = MaterialTheme.typography.labelLarge)
                Text(if (index > active) "Menunggu tahap sebelumnya" else if (index == active) "Status terakhir pada data demo" else "Tahap sebelumnya tercatat", style = MaterialTheme.typography.bodySmall) }
        } }
    }
}

@Composable
fun BuyerTrackingScreen(order: BuyerOrder, onBack: () -> Unit, onReceive: () -> Unit, onArriveDemo: () -> Unit,
                        onReport: (String, () -> Unit) -> Unit, onReview: () -> Unit, onRefund: () -> Unit, onSchedule: () -> Unit) {
    var confirming by rememberSaveable(order.id) { mutableStateOf(false) }
    var reporting by rememberSaveable(order.id) { mutableStateOf(false) }
    var issue by rememberSaveable(order.id) { mutableStateOf("") }
    val ready = canConfirmReceipt(order)
    Page("Lacak pesanan", "#${order.id} · ${order.fulfillment.label}", onBack) {
        item { Notice(if (order.order.status == OrderStatus.COMPLETED) "Pesanan selesai" else if (ready) "Pesanan tiba / siap diambil, periksa hasil panen" else "Pesanan sedang dipenuhi",
            if (order.order.status == OrderStatus.COMPLETED) "Penerimaan sudah dikonfirmasi. Ulasan dan pengajuan refund tersedia sesuai syarat."
            else "Belum selesai. Konfirmasi setelah jumlah dan kondisi barang kamu periksa.") }
        item { Panel("Tujuan & jadwal") {
            Text(order.address.recipient, style = MaterialTheme.typography.titleMedium)
            Text(if (order.fulfillment == Fulfillment.PICKUP) "Ambil di Kebun Darto, Lowokwaru, Kota Malang" else order.address.street)
            Text(order.address.courierNote, style = MaterialTheme.typography.bodySmall)
            Text(order.order.schedule, style = MaterialTheme.typography.labelLarge)
            StatusBadge("${order.fulfillment.label} · Ongkir ${rupiah(order.order.deliveryFee)}")
            if (order.proposal != null) SecondaryButton("Tanggapi / lihat perubahan jadwal", onSchedule)
        } }
        item { Panel("Perjalanan pesanan") { DeliveryTimeline(order) } }
        item { Panel("#${order.id}") {
            StatusBadge(if (ready) "Menunggu Penerimaan" else order.order.status.label)
            BuyerOrderItems(order); KeyValue("Subtotal produk", rupiah(order.order.subtotal)); KeyValue("Ongkir dibayar", rupiah(order.order.deliveryFee)); KeyValue("Total pesanan", rupiah(order.total), true)
        } }
        if (ready) item { PrimaryButton("Konfirmasi pesanan diterima", { confirming = true }) }
        if (!ready && order.order.status in listOf(OrderStatus.PROCESSING, OrderStatus.DELIVERY)) item { SecondaryButton("Simulasikan tiba / siap diambil", onArriveDemo) }
        item { SecondaryButton("Laporkan masalah pesanan", { reporting = !reporting }) }
        if (reporting) item { Panel("Masalah pesanan") {
            Field("Jelaskan masalahnya", issue, { issue = it.take(1000) }, "Minimal 10 karakter", multiline = true)
            PrimaryButton("Simpan laporan", { onReport(issue) { reporting = false; issue = "" } }, issue.trim().length >= 10)
            Text("Laporan tidak otomatis mengonfirmasi penerimaan atau memindahkan dana.", style = MaterialTheme.typography.bodySmall)
        } }
        if (order.deliveryIssue.isNotBlank()) item { Notice("Laporan pembeli tercatat", order.deliveryIssue, Tone.WARNING) }
        if (order.order.status == OrderStatus.COMPLETED) {
            item { PrimaryButton("Tulis Ulasan", onReview) }
            item { SecondaryButton("Ajukan / lihat refund", onRefund) }
        }
        item { Text("Konfirmasi mencatat waktu penerimaan. Refund normal dapat diajukan maksimal 24 jam sejak diterima.", style = MaterialTheme.typography.bodySmall) }
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("Pesanan sudah diterima?") },
        text = { Text("Periksa jumlah dan kondisi barang terlebih dahulu. Konfirmasi akan menyelesaikan pesanan dan memulai batas pengajuan refund 24 jam.") },
        confirmButton = { TextButton({ confirming = false; onReceive() }) { Text("Ya, pesanan diterima") } },
        dismissButton = { TextButton({ confirming = false }) { Text("Periksa lagi") } })
}



@Preview(showBackground = true, widthDp = 375, heightDp = 1000)
@Composable private fun TrackingPreview() { PanenkuTheme { BuyerTrackingScreen(BuyerSamples.initial.orders.first { it.id == "PES-2026-001" }, {}, {}, {}, { _, _ -> }, {}, {}, {}) } }
