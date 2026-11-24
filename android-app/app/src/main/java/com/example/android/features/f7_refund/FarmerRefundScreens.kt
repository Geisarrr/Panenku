package id.panenku.app.features.f7_refund

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
fun RefundReviewScreen(decision: String?, onBack: () -> Unit, onDecide: (Boolean, String) -> Unit, onTransfer: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    Page("Tinjau pengajuan refund", "#RF-002 · #PES-2026-002 · data contoh", onBack) {
        item { Notice("Tanggapi dalam 24 jam", "Pengajuan demo sayur rusak. Periksa keluhan dan bukti sebelum mengambil keputusan.", Tone.WARNING) }
        item { Panel("Keluhan Rina Catering") {
            StatusBadge(decision ?: "Menunggu tanggapan petani", Tone.WARNING)
            Text("Sayuran rusak saat diterima", style = MaterialTheme.typography.titleMedium)
            Text("Sebagian daun berlubang dan layu. Pembeli meminta pengembalian penuh, termasuk ongkir.")
            EvidenceCard("kondisi-sayur-demo.jpg")
        } }
        item { Panel("Nilai & tujuan refund") {
            KeyValue("Produk sudah dibayar", "Rp200.000"); KeyValue("Ongkir sudah dibayar", "Rp2.500"); KeyValue("Refund penuh", "Rp202.500", true)
            KeyValue("Tujuan tersamarkan", "BCA · •••• 0000"); KeyValue("Penerima contoh", "Rina Catering")
        } }
        item { Panel("Keputusan petani") {
            PrimaryButton("Setujui refund penuh", { onDecide(true, "") }, decision == null)
            Field("Alasan penolakan (wajib)", reason, { reason = it }, "Jelaskan hasil pemeriksaan", multiline = true)
            SecondaryButton("Tolak pengajuan dengan alasan", { onDecide(false, reason) }, enabled = decision == null)
            if (decision?.startsWith("Disetujui") == true) PrimaryButton("Lanjut transfer balik", onTransfer)
            Text("Petani mengembalikan dana dari rekening sendiri. Panenku tidak memindahkan dana.", style = MaterialTheme.typography.bodySmall)
        } }
    }
}

@Composable
fun RefundTransferScreen(status: String?, onBack: () -> Unit, onSubmit: (String) -> Unit) {
    var proof by rememberSaveable { mutableStateOf<String?>(null) }
    Page("Transfer balik refund", "#RF-002 · #PES-2026-002", onBack) {
        item { StatusBadge(status ?: "Disetujui · Menunggu transfer petani") }
        item { Panel("Transfer penuh ke pembeli") {
            KeyValue("Jumlah pengembalian", "Rp202.500", true)
            KeyValue("Bank tujuan contoh", "BCA"); KeyValue("Rekening contoh", "0000 0000 0000"); KeyValue("Nama penerima", "Rina Catering (contoh)")
            Notice("Lakukan transfer di luar Panenku", "Gunakan aplikasi bank, cek penerima dan nominal, lalu unggah bukti. Tidak ada transaksi nyata pada demo.")
        } }
        item { Panel("Bukti transfer balik") {
            MediaPicker("Pilih foto bukti transfer", proof, { proof = it })
            PrimaryButton("Kirim bukti transfer balik", { onSubmit(proof.orEmpty()) }, proof != null && status == null)
        } }
        item { Panel("Setelah bukti dikirim") { Timeline(listOf("Menunggu konfirmasi pembeli" to "Pembeli memeriksa dana masuk, mengonfirmasi atau melaporkan kendala.", "Refund selesai" to "Hanya setelah pembeli mengonfirmasi dana diterima.")) } }
    }
}
