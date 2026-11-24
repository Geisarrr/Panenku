package id.panenku.app.features.f7_refund

import id.panenku.app.ui.components.*

import android.content.*
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.panenku.app.data.*
import id.panenku.app.data.buyer.*
import id.panenku.app.ui.theme.PanenkuTheme
import java.time.Instant


@Composable
fun BuyerRefundFormScreen(order: BuyerOrder, current: BuyerRefund?, now: Instant,
                          onBack: () -> Unit, onSubmit: (RefundRequest) -> Unit, onStatus: () -> Unit) {
    var reason by rememberSaveable(order.id) { mutableStateOf("Kondisi sayuran tidak sesuai") }
    var description by rememberSaveable(order.id) { mutableStateOf("") }
    var photo by rememberSaveable(order.id) { mutableStateOf<String?>(null) }
    var bank by rememberSaveable(order.id) { mutableStateOf("BCA") }
    var account by rememberSaveable(order.id) { mutableStateOf("") }
    var recipient by rememberSaveable(order.id) { mutableStateOf(order.order.buyer) }
    var confirmed by rememberSaveable(order.id) { mutableStateOf(false) }
    val existing = current != null && current.status != BuyerRefundStatus.REJECTED
    val eligible = isRefundEligible(order, now)
    val valid = eligible && !existing && description.trim().length >= 10 && photo != null &&
            account.length in 8..24 && recipient.isNotBlank() && confirmed
    Page("Ajukan refund", "#${order.id} · ${order.storeName}", onBack) {
        item { Notice(if (eligible) "Masih dalam waktu pengajuan" else "Batas pengajuan berakhir",
            "Diterima ${order.receivedAt?.let(::buyerDate) ?: "setelah gagal dipenuhi"}. Batas ${refundDeadline(order)?.let(::buyerDate) ?: "sesuai penanganan pesanan gagal"}. Waktu demo: ${buyerDate(now)}.",
            if (eligible) Tone.SUCCESS else Tone.WARNING) }
        if (existing) item { Notice("Pengajuan sudah tercatat", "Satu pengajuan aktif per pesanan. Periksa status pengajuan yang ada."); PrimaryButton("Lihat status refund", onStatus) }
        item { Panel("Pesanan yang diterima") {
            StatusBadge(order.order.status.label); BuyerOrderItems(order)
            KeyValue("Produk sudah dibayar", rupiah(order.order.subtotal))
            KeyValue("Ongkir sudah dibayar", rupiah(order.order.deliveryFee))
            if (order.serviceFee + order.packagingFee > 0) KeyValue("Biaya lain tersimpan", rupiah(order.serviceFee + order.packagingFee))
            KeyValue("Refund penuh", rupiah(order.total), true)
            Text("Jumlah berasal dari total yang benar-benar dibayar. Harga produk terbaru tidak mengubah nilai refund.", style = MaterialTheme.typography.bodySmall)
        } }
        if (!existing) {
            item { Panel("Alasan & bukti") {
                Text("Alasan pengajuan", style = MaterialTheme.typography.labelLarge)
                FilterChips(listOf("Kondisi sayuran tidak sesuai", "Produk rusak", "Barang tidak lengkap"), reason, { reason = it })
                Field("Ceritakan masalahnya", description, { description = it.take(1000) }, "Uraikan kondisi produk, minimal 10 karakter", multiline = true)
                if (photo != null) LocalPhoto(photo, "cabbage", Modifier.fillMaxWidth().height(160.dp))
                MediaPicker("Tambah / ganti foto bukti", photo, { photo = it })
            } }
            item { Panel("Rekening tujuan refund") {
                StatusBadge("Gunakan data dummy untuk demo", Tone.INFO)
                Text("Bank tujuan", style = MaterialTheme.typography.labelLarge)
                FilterChips(listOf("BCA", "BRI", "BNI", "Mandiri"), bank, { bank = it })
                Field("Nomor rekening", account, { account = it.filter(Char::isDigit).take(24) }, "000000000000", keyboard = KeyboardType.Number)
                Field("Nama penerima", recipient, { recipient = it })
                Text("Pengembalian dilakukan petani di luar aplikasi, bukan dari saldo Panenku.", style = MaterialTheme.typography.bodySmall)
            } }
            item { Notice("Satu pengajuan aktif per pesanan", "Refund penuh saja. Petani memiliki 24 jam untuk menanggapi. Data dan jam pada demo ini merupakan contoh.") }
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(confirmed, { confirmed = it })
                Text("Saya memastikan alasan, bukti, dan tujuan refund sudah benar.", style = MaterialTheme.typography.bodySmall)
            } }
            item { PrimaryButton("Kirim pengajuan ${rupiah(order.total)}", {
                onSubmit(RefundRequest(order.id, reason, description.trim(), photo.orEmpty(), bank, account, recipient.trim(), confirmed))
            }, valid) }
        }
    }
}

@Composable
private fun RefundProof(refund: BuyerRefund) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Panel("Bukti transfer balik") {
        Notice("BUKTI DEMO · bukan transaksi nyata", "${rupiah(refund.amount)} · Penerima ${refund.request.recipient} · ${refund.request.bank} •••• ${refund.request.account.takeLast(4)}", Tone.INFO)
        SecondaryButton("Lihat bukti lengkap", { expanded = true })
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text("Transfer balik · ilustrasi") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ini bukti dummy, bukan dokumen bank.")
            KeyValue("Nominal", rupiah(refund.amount), true); KeyValue("Penerima", refund.request.recipient)
            KeyValue("Bank tujuan", "${refund.request.bank} •••• ${refund.request.account.takeLast(4)}")
            Text("Periksa mutasi rekening sebelum mengonfirmasi dana diterima.")
        } }, confirmButton = { TextButton({ expanded = false }) { Text("Tutup") } })
}

@Composable
fun BuyerRefundStatusScreen(refund: BuyerRefund, ticket: BuyerTicket?, now: Instant, onBack: () -> Unit,
                            onRespond: (Boolean) -> Unit, onConfirm: () -> Unit, onReportMissing: () -> Unit, onTicket: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Page("Status refund", "#${refund.id} · #${refund.orderId}", onBack) {
        item { Notice(when (refund.status) {
            BuyerRefundStatus.AWAITING_FUNDS -> "Cek rekening tujuanmu"
            BuyerRefundStatus.COMPLETED -> "Dana diterima · Refund selesai"
            BuyerRefundStatus.REJECTED -> "Pengajuan ditolak petani"
            else -> "Menunggu tanggapan petani"
        }, if (refund.status == BuyerRefundStatus.AWAITING_FUNDS) "Petani sudah mengunggah bukti transfer balik. Refund belum selesai sampai kamu mengonfirmasi dana benar-benar diterima." else "Periksa riwayat dan status pengajuan di bawah.",
            if (refund.status == BuyerRefundStatus.COMPLETED) Tone.SUCCESS else Tone.WARNING) }
        item { Panel("Pengembalian penuh") {
            StatusBadge(refund.status.label, if (refund.status == BuyerRefundStatus.COMPLETED) Tone.SUCCESS else Tone.WARNING)
            KeyValue("Nilai refund", rupiah(refund.amount), true)
            KeyValue("Tujuan", "${refund.request.bank} •••• ${refund.request.account.takeLast(4)} · ${refund.request.recipient}")
            Text("Alasan: ${refund.request.reason}. ${refund.request.description}", style = MaterialTheme.typography.bodyMedium)
        } }
        item { Panel("Riwayat refund") {
            Timeline(refund.events.map { it.title to it.detail })
            if (refund.status == BuyerRefundStatus.AWAITING_FUNDS) Text("Konfirmasi dana diterima dan refund selesai menunggu pemeriksaan rekening oleh pembeli.", style = MaterialTheme.typography.bodySmall)
        } }
        if (refund.status == BuyerRefundStatus.SUBMITTED) item { Panel("Simulasi balasan petani") {
            Text("Coba tampilan tahap berikutnya menggunakan data contoh.", style = MaterialTheme.typography.bodySmall)
            PrimaryButton("Simulasikan persetujuan & bukti transfer", { onRespond(true) })
            SecondaryButton("Simulasikan penolakan petani", { onRespond(false) })
        } }
        if (refund.status in listOf(BuyerRefundStatus.AWAITING_FUNDS, BuyerRefundStatus.COMPLETED)) item { RefundProof(refund) }
        if (refund.status == BuyerRefundStatus.AWAITING_FUNDS) {
            item { PrimaryButton("Dana sudah diterima", { confirming = true }) }
            item { SecondaryButton("Dana belum diterima", onReportMissing, enabled = !refund.missingReported) }
        }
        if (refund.missingReported) item { Notice("Dana belum diterima dilaporkan", "Refund tetap terbuka. Buat tiket eskalasi sebelum menghubungi CS.", Tone.WARNING) }
        if (ticket != null || escalationIssue(refund, now) != null) item { PrimaryButton(if (ticket == null) "Buat tiket eskalasi" else "Lihat tiket #${ticket.id}", onTicket) }
        item { Text("Konfirmasi dana menyelesaikan refund. Penutupan tiket oleh admin merupakan proses terpisah.", style = MaterialTheme.typography.bodySmall) }
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("Dana benar-benar sudah diterima?") },
        text = { Text("Pastikan ${rupiah(refund.amount)} sudah masuk ke rekening Anda. Konfirmasi ini menyelesaikan refund.") },
        confirmButton = { TextButton({ confirming = false; onConfirm() }) { Text("Ya, dana sudah masuk") } },
        dismissButton = { TextButton({ confirming = false }) { Text("Periksa lagi") } })
}

@Composable
fun BuyerHelpScreen(ticket: BuyerTicket, refund: BuyerRefund?, csPhone: String?, onBack: () -> Unit,
                    onAddEvidence: (String, String?, () -> Unit) -> Unit, onInfo: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable(ticket.id) { mutableStateOf("") }
    var photo by rememberSaveable(ticket.id) { mutableStateOf<String?>(null) }
    var contacting by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val message = safeCsMessage(ticket)
    val phone = csPhone?.filter(Char::isDigit)
    val configured = !phone.isNullOrBlank() && phone.length in 10..15 && phone !in listOf("6280000000000", "628000000000", "62800000000")
    Page("Bantuan & eskalasi", "Tiket tersimpan sebelum WhatsApp dibuka", onBack) {
        item { Notice("Tiket #${ticket.id} berhasil dicatat", "Dibuat ${buyerDate(ticket.createdAt)}. Tim CS dapat meninjau riwayat refund tanpa meminta data rekening lewat chat.") }
        item { Panel("Tiket aktif") {
            StatusBadge("Terbuka · Menunggu tinjauan CS", Tone.WARNING)
            KeyValue("Pesanan", "#${ticket.orderId}"); KeyValue("Refund", "#${ticket.refundId}"); KeyValue("Masalah", ticket.issue.label, true)
            ticket.notes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (ticket.evidenceUris.isNotEmpty()) Text("${ticket.evidenceUris.size} berkas lokal terlampir", style = MaterialTheme.typography.bodySmall)
            SecondaryButton("Tambah keterangan / bukti", { editing = true })
        } }
        if (editing) item { Panel("Tambah keterangan / bukti") {
            Field("Keterangan tambahan", note, { note = it.take(1000) }, multiline = true)
            MediaPicker("Pilih foto bukti", photo, { photo = it })
            PrimaryButton("Simpan", { onAddEvidence(note, photo) { editing = false; note = ""; photo = null } }, note.isNotBlank() || photo != null)
            SecondaryButton("Batal", { editing = false })
        } }
        item { Panel("Riwayat tiket") {
            Timeline(listOf(ticket.issue.label to "Laporan pembeli · status dana terpisah dari tiket", "Tiket dibuat" to "#${ticket.id} tercatat di Panenku", "Peninjauan admin" to "Menunggu CS menelaah bukti dan menghubungi pihak terkait"))
            if (refund?.status == BuyerRefundStatus.COMPLETED) Notice("Dana telah dikonfirmasi pembeli", "Tiket tetap menunggu penyelesaian oleh CS.")
        } }
        item { Panel("Lanjutkan ke WhatsApp CS") {
            Text("Pesan yang akan dibawa", style = MaterialTheme.typography.labelLarge); Text(message)
            Text("Hanya ID tiket dan ringkasan masalah. Tidak menyertakan rekening, KTP, alamat, atau lampiran transaksi.", style = MaterialTheme.typography.bodySmall)
            PrimaryButton("Hubungi WhatsApp CS", { contacting = true })
        } }
        item { Panel("Kapan bisa eskalasi?") {
            Text("• Pengajuan refund ditolak petani.")
            Text("• Tidak ada tanggapan petani selama 24 jam.")
            Text("• Transfer balik belum diterima pembeli.")
            Text("Tiket dicatat terlebih dahulu. Penutupan tiket admin tidak otomatis berarti dana sudah diterima.", style = MaterialTheme.typography.bodySmall)
        } }
    }
    if (contacting) AlertDialog(onDismissRequest = { contacting = false }, title = { Text("Hubungi CS") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message)
            if (!configured) Text("Nomor CS resmi belum dikonfigurasi. Anda dapat menyalin ringkasan tiket terlebih dahulu.", style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton({
            if (configured) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}"))
                runCatching { context.startActivity(intent) }.onFailure { onInfo("WhatsApp atau browser belum dapat dibuka.") }
            } else {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Ringkasan tiket Panenku", message))
                onInfo("Ringkasan tiket disalin.")
            }
            contacting = false
        }) { Text(if (configured) "Buka WhatsApp" else "Salin pesan tiket") } }, dismissButton = { TextButton({ contacting = false }) { Text("Tutup") } })
}






@Preview(showBackground = true, widthDp = 375, heightDp = 1000)
@Composable private fun BuyerRefundPreview() { PanenkuTheme { BuyerRefundStatusScreen(BuyerSamples.refundExample, null, BuyerSamples.now, {}, {}, {}, {}, {}) } }

@Preview(showBackground = true, widthDp = 375, heightDp = 1000)
@Composable private fun BuyerRefundFormPreview() { PanenkuTheme { BuyerRefundFormScreen(BuyerSamples.completed, null, BuyerSamples.now, {}, {}, {}) } }

@Preview(showBackground = true, widthDp = 375, heightDp = 1000)
@Composable private fun BuyerHelpPreview() { PanenkuTheme { BuyerHelpScreen(BuyerTicket("TKT-026", "RF-002", BuyerSamples.completed.id, TicketIssue.MISSING_FUNDS, BuyerSamples.now), BuyerSamples.refundExample, null, {}, { _, _, _ -> }, {}) } }
