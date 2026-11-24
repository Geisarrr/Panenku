package id.panenku.app.features.f6_payment_verification

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
fun BuyerPaymentScreen(order: BuyerOrder, now: Instant, onBack: () -> Unit, onSubmit: (String) -> Unit,
                       onReviewDemo: (Boolean) -> Unit, onTracking: () -> Unit, onMessage: (String) -> Unit) {
    var photo by rememberSaveable(order.id, order.proofs.size) { mutableStateOf<String?>(null) }
    val last = order.proofs.lastOrNull(); val rejected = last?.status == ProofStatus.REJECTED
    val deadline = order.paymentDeadline
    val payable = order.order.status == OrderStatus.PAYMENT && deadline != null && now.isBefore(deadline)
    val context = LocalContext.current
    fun copy(value: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Panenku", value))
        onMessage("Data contoh disalin.")
    }
    Page(if (rejected) "Bukti pembayaran ditolak" else "Pembayaran ke Petani", "#${order.id} · ${order.storeName}", onBack) {
        item { Notice(when {
            rejected -> "Belum terverifikasi / belum lunas"
            order.order.status == OrderStatus.REVIEW -> "Menunggu pemeriksaan petani"
            order.order.status == OrderStatus.PAYMENT -> "Petani sudah menerima pesanan"
            order.order.status == OrderStatus.PENDING -> "Menunggu konfirmasi petani"
            else -> "Pembayaran telah disetujui"
        }, when {
            rejected -> "Petani belum dapat mencocokkan dana masuk dengan bukti. Pesanan belum diproses."
            order.order.status == OrderStatus.REVIEW -> "Unggahan tidak berarti pembayaran terverifikasi. Bukti diperiksa secara manual; tahap ini tidak otomatis kedaluwarsa."
            order.order.status == OrderStatus.PENDING -> "Pembayaran baru tersedia setelah petani menerima pesanan."
            else -> "Bayar langsung ke petani sesuai nominal yang tersimpan pada pesanan."
        }, if (rejected) Tone.ERROR else Tone.SUCCESS) }
        if (rejected && last != null) item { Panel("Catatan dari petani") {
            StatusBadge("Ditolak · ${last.reviewedAt?.let(::buyerDate).orEmpty()}", Tone.ERROR)
            Text(last.reason, style = MaterialTheme.typography.bodyMedium)
            KeyValue("Nominal seharusnya", rupiah(order.total), true)
        } }
        item { Panel("Bayar langsung ke ${order.farmerName}") {
            Text(rupiah(order.total), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary)
            KeyValue("Produk", rupiah(order.order.subtotal)); KeyValue("Ongkir", rupiah(order.order.deliveryFee))
            SecondaryButton("Salin nominal", { copy(order.total.toString()) })
        } }
        if (deadline != null && order.order.status == OrderStatus.PAYMENT) item { Notice(
            if (rejected) "Batas unggah baru · 24 jam" else "Batas unggah bukti · 24 jam",
            "Unggah paling lambat ${buyerDate(deadline)}. Waktu demo ${buyerDate(now)}." + if (rejected) " Jangan transfer lagi jika dana sudah terkirim; perbaiki buktinya." else "",
            if (payable) Tone.WARNING else Tone.ERROR) }
        if (!rejected && order.order.status in listOf(OrderStatus.PAYMENT, OrderStatus.REVIEW)) {
            item { Panel("Transfer ke rekening petani") {
                Text("BRI · Rekening petani", style = MaterialTheme.typography.titleMedium)
                Text("Darto Sobianto (contoh)"); Text("0000 0000 0000")
                SecondaryButton("Salin nomor rekening contoh", { copy("000000000000") })
                Text("Data rekening dummy. Jangan gunakan untuk transfer sungguhan.", style = MaterialTheme.typography.bodySmall)
            } }
            item { Panel("Atau gunakan QRIS petani") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Outlined.QrCode2, "QRIS demo tidak dapat dipindai", Modifier.size(64.dp))
                    Column { Text("QRIS DEMO", style = MaterialTheme.typography.titleMedium); Text("Tidak dapat dipindai. Bukan kode pembayaran aktif.", style = MaterialTheme.typography.bodySmall) }
                }
            } }
            item { Notice("Bayar di luar aplikasi Panenku", "Panenku tidak menampung dana dan tidak memotong saldo. Gunakan data contoh untuk mencoba tampilan ini.") }
        }
        if (order.proofs.isNotEmpty()) item { Panel("Riwayat bukti") {
            Timeline(order.proofs.mapIndexed { index, proof -> "Bukti #${index + 1} · ${proof.status.label}" to
                    "${buyerDate(proof.submittedAt)}${if (proof.reason.isNotBlank()) " · ${proof.reason}" else ""}" })
            if (last?.uri?.startsWith("content://") == true) LocalPhoto(last.uri, null, Modifier.fillMaxWidth().height(180.dp))
            else Notice("Bukti demo", "Ilustrasi bukti pembayaran; bukan transaksi bank nyata.", Tone.INFO)
            Text("Riwayat tetap tersimpan setelah unggah ulang.", style = MaterialTheme.typography.bodySmall)
        } }
        if (order.order.status == OrderStatus.PAYMENT) item { Panel(if (rejected) "Perbaiki bukti" else "Unggah bukti pembayaran") {
            MediaPicker(if (rejected) "Pilih bukti pengganti" else "Pilih foto bukti pembayaran", photo, { photo = it })
            if (photo != null) LocalPhoto(photo, null, Modifier.fillMaxWidth().height(160.dp))
            PrimaryButton(if (rejected) "Unggah ulang bukti" else "Kirim Bukti Pembayaran", { onSubmit(photo.orEmpty()) }, photo != null && payable)
            Text("JPG/PNG maksimal 5 MB. Bukti diperiksa petani sebelum pesanan diproses.", style = MaterialTheme.typography.bodySmall)
        } }
        if (order.order.status == OrderStatus.REVIEW) item { Panel("Simulasi pemeriksaan petani") {
            PrimaryButton("Simulasikan pembayaran disetujui", { onReviewDemo(true) })
            SecondaryButton("Simulasikan bukti ditolak", { onReviewDemo(false) })
        } }
        if (order.order.status in listOf(OrderStatus.PROCESSING, OrderStatus.DELIVERY, OrderStatus.COMPLETED)) item { PrimaryButton("Lacak pesanan", onTracking) }
    }
}



@Preview(showBackground = true, widthDp = 375, heightDp = 1000)
@Composable private fun PaymentPreview() { PanenkuTheme { BuyerPaymentScreen(BuyerSamples.initial.orders.first { it.id == "PES-2026-003" }, BuyerSamples.now, {}, {}, {}, {}, {}) } }
