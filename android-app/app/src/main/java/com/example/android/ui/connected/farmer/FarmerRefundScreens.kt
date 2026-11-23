package id.panenku.app.ui.connected.farmer

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.ConnectedState
import java.time.Instant
import java.time.temporal.ChronoUnit

internal fun refundLabel(status: String) = when(status) {
    "diajukan" -> "Menunggu tanggapan petani"; "disetujui" -> "Disetujui · Menunggu transfer petani"
    "ditolak" -> "Pengajuan ditolak"; "menunggu_konfirmasi_dana" -> "Menunggu konfirmasi pembeli"; "selesai" -> "Refund selesai"; else -> status.replace('_',' ')
}
@Composable internal fun FarmerRefundListScreen(s: ConnectedState, back: () -> Unit, open: (String) -> Unit, more: () -> Unit) {
    FSettingsPage("Pengajuan refund", "Pengembalian penuh langsung ke pembeli", back) {
        if (s.refunds.isEmpty()) FSettingsNotice("Belum ada pengajuan refund", "Pengajuan pembeli akan muncul di halaman ini.")
        s.refunds.forEach { r -> FCard("#${shortId(r.text("id"))} · #${shortId(r.text("orderId"))}") {
            FBadge(refundLabel(r.text("status")), if(r.text("status") == "diajukan") "orange" else "green")
            FText(r.text("reason"), 12f); FKeyValue("Refund penuh", money(r.long("refundAmount")), true)
            FButton(if(r.text("status") == "disetujui") "Transfer balik refund" else "Tinjau pengajuan", { open(r.text("id")) })
        } }
        if (s.more) FButton("Muat refund berikutnya", more, primary = false, enabled = !s.busy)
    }
}
@Composable internal fun FarmerRefundReviewScreen(s: ConnectedState, refund: JsonObject, back: () -> Unit,
    evidence: () -> Unit, decide: (Boolean, String) -> Unit, transfer: () -> Unit) {
    val order = s.farmerRefundOrder
    var reason by rememberSaveable(refund.text("id")) { mutableStateOf("") }
    val pending = refund.text("status") == "diajukan"
    val deadline = runCatching { Instant.parse(refund.text("createdAt")).plus(24, ChronoUnit.HOURS).toString() }.getOrDefault("")
    FSettingsPage("Tinjau pengajuan refund", "#${shortId(refund.text("id"))} · #${shortId(refund.text("orderId"))}", back) {
        FSettingsNotice(if (pending) "Tanggapi dalam 24 jam" else refundLabel(refund.text("status")), "Diajukan ${dateTimeLabel(refund.text("createdAt"))} WIB. ${if(pending) "Beri keputusan paling lambat ${dateTimeLabel(deadline)} WIB. Belum ada keputusan." else "Keputusan dan transfer mengikuti status yang tersimpan."}", if(pending) "orange" else "green")
        FCard("Keluhan ${order?.text("buyerName").orEmpty().ifBlank { "pembeli" }}") {
            FBadge(refundLabel(refund.text("status")), if(pending) "orange" else "green")
            val lines = refund.text("reason").split('\n')
            FText(lines.firstOrNull().orEmpty(), 14f, 600)
            FText(lines.drop(1).joinToString("\n").ifBlank { "Pembeli meminta pengembalian penuh, termasuk ongkir yang sudah dibayar." }, 12f)
            if(refund.flag("hasEvidence")) FUploadCard("Bukti keluhan pembeli", "Lihat lampiran pengajuan", R.drawable.farmer_refund_file, evidence)
            FKeyValue("Diterima pembeli", dateTimeLabel(order?.text("receivedAt").orEmpty()) + " WIB")
            FText("Pengajuan mengikuti batas waktu setelah diterima. Satu refund aktif untuk pesanan ini.", 10f, color = FMuted)
        }
        FCard("Nilai & tujuan refund") {
            if(order != null) { FKeyValue("Produk sudah dibayar", money(order.long("subtotalItems"))); FKeyValue("Ongkir sudah dibayar", money(order.long("deliveryFee"))); FRule() }
            FKeyValue("Refund penuh", money(refund.long("refundAmount")), true, 14f)
            FKeyValue("Tujuan tersamarkan", "${refund.text("destinationProvider")} · •••• ${refund.text("destinationAccount").takeLast(4)}", size = 11f)
            FKeyValue("Penerima", refund.text("destinationName"), size = 11f)
            FText("Tidak melihat dana yang benar-benar diterima. Nomor rekening lengkap tersedia di tahap transfer balik.", 10f, color = FMuted)
        }
        if(pending) FCard("Keputusan petani") {
            FButton("Setujui refund penuh", { decide(true, "") }, enabled = !s.busy)
            FText("Setelah disetujui, transfer ${money(refund.long("refundAmount"))} di luar aplikasi dan unggah buktinya. Refund belum selesai sampai pembeli menerima dana.", 12f, color = FMuted)
            FRule()
            FField("Alasan penolakan (wajib)", reason, { reason = it.take(1000) }, "Jelaskan hasil pemeriksaan dan bukti pendukung", multiline = true)
            FButton("Tolak pengajuan dengan alasan", { decide(false, reason.trim()) }, primary = false, enabled = reason.isNotBlank() && !s.busy)
        }
        if(refund.text("status") in listOf("disetujui", "menunggu_konfirmasi_dana", "selesai")) FButton("Lihat transfer balik refund", transfer)
        if(refund.text("farmerReason").isNotBlank()) FSettingsNotice("Tanggapan petani", refund.text("farmerReason"))
        FText("Pembeli dapat membuat tiket eskalasi jika ditolak, tidak ada tanggapan 24 jam, atau dana transfer balik belum diterima.", 12f, color = FMuted)
    }
}
@Composable internal fun FarmerRefundTransferScreen(s: ConnectedState, refund: JsonObject, back: () -> Unit,
    image: () -> Unit, send: (String) -> Unit) {
    var uri by rememberSaveable(refund.text("id")) { mutableStateOf<String?>(null) }
    var file by rememberSaveable(refund.text("id")) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val choose = rememberFarmerImagePicker({value,name,_ -> uri=value;file=name;error=null},{error=it})
    val clipboard = LocalContext.current.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val order = s.farmerRefundOrder
    val canSend = refund.text("status") == "disetujui" && !refund.flag("hasTransferProof")
    FSettingsPage("Transfer balik refund", "#${shortId(refund.text("id"))} · #${shortId(refund.text("orderId"))}", back) {
        FBadge(refundLabel(refund.text("status")))
        FCard("Transfer penuh ke pembeli") {
            FKeyValue("Jumlah pengembalian", money(refund.long("refundAmount")), true, 14f)
            FText(if(order != null) "Termasuk produk ${money(order.long("subtotalItems"))} dan ongkir yang dibayar ${money(order.long("deliveryFee"))}." else "Transfer nominal penuh sesuai pengajuan yang disetujui.",12f,color=FMuted)
            FKeyValue("Bank tujuan", refund.text("destinationProvider"))
            FKeyValue("Nomor rekening", refund.text("destinationAccount"))
            FKeyValue("Nama penerima", refund.text("destinationName"))
            FBadge("Periksa tujuan sebelum transfer", "gray")
            FButton("Salin tujuan transfer", { clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Tujuan refund", "${refund.text("destinationProvider")} ${refund.text("destinationAccount")} a.n. ${refund.text("destinationName")}")) }, primary=false)
        }
        FSettingsNotice("Lakukan transfer di luar Panenku", "Gunakan aplikasi bank, cek penerima dan nominal, lalu unggah bukti transfer yang benar-benar dilakukan. Panenku tidak memindahkan dana.")
        FCard("Bukti transfer balik") {
            FUploadCard(if(refund.flag("hasTransferProof")) "Bukti transfer sudah dikirim" else file.ifBlank { "Pilih foto bukti transfer" }, if(refund.flag("hasTransferProof")) "Lihat bukti yang tersimpan" else if(uri != null) "JPG/PNG · Bukti siap dikirim" else "Belum ada berkas · JPG/PNG · Maks. 5 MB", click={if(refund.flag("hasTransferProof"))image()else if(canSend)choose()})
            FButton(if(refund.flag("hasTransferProof")) "Lihat bukti transfer" else "Pilih bukti transfer", {if(refund.flag("hasTransferProof"))image()else choose()}, primary=false,enabled=canSend||refund.flag("hasTransferProof"))
            FText("Pastikan nominal, penerima, tanggal, dan status transfer terlihat. Jangan mengunggah bukti pembayaran awal pembeli.",12f,color=FMuted)
        }
        if(error!=null)FSettingsNotice("Gambar belum dapat digunakan",error!!,"red")
        FButton("Kirim bukti transfer balik", {uri?.let(send)},enabled=canSend&&uri!=null&&!s.busy)
        FText(if(canSend)"Tombol aktif setelah bukti dipilih." else "Bukti yang sudah dikirim menunggu konfirmasi penerimaan dana dari pembeli.",10f,color=FMuted)
        FCard("Setelah bukti dikirim") {
            TimelineRow("Menunggu konfirmasi pembeli","${order?.text("buyerName").orEmpty().ifBlank{"Pembeli"}} mengecek dana masuk. Unggah bukti tidak otomatis menyelesaikan refund.",refund.text("status")=="menunggu_konfirmasi_dana")
            TimelineRow("Refund selesai","Hanya setelah pembeli memilih Dana sudah diterima.",refund.text("status")=="selesai")
        }
    }
}
