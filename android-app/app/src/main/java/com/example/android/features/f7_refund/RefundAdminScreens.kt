package id.panenku.app.features.f7_refund

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
fun AdminDisputeScreen(decision: String?, onBack: () -> Unit, onDecision: (String, String, Boolean) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("Terbuka") }
    var note by rememberSaveable { mutableStateOf("") }
    var suspend by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    Page("Sengketa refund", "Tiket eskalasi · Tinjau kronologi", onBack, true) {
        item { FilterChips(listOf("Terbuka", "Ditutup admin"), filter, { filter = it }) }
        val closed = decision?.startsWith("Ditutup admin") == true
        val visible = if (filter == "Terbuka") !closed else closed
        if (!visible) item { EmptyState("Tidak ada tiket pada filter ini") }
        else {
            item { Panel("Antrean tiket") { Text("#TKT-026 · Dipilih"); Text("Dana belum diterima · #RF-002", style = MaterialTheme.typography.bodySmall) } }
            item { Panel("Detail tiket #TKT-026") {
                StatusBadge(decision ?: "Terbuka · Belum ada keputusan admin", Tone.WARNING)
                KeyValue("Pesanan / refund", "#PES-2026-002 / #RF-002"); KeyValue("Pembeli", "Rina Catering"); KeyValue("Petani", "Darto Sobianto"); KeyValue("Dana disengketakan", "Rp202.500", true)
                Text("Pembeli melaporkan dana belum masuk setelah bukti transfer balik diunggah.", style = MaterialTheme.typography.bodyMedium)
            } }
            item { Panel("Kronologi & bukti") {
                Timeline(listOf("Pesanan diterima pembeli" to "20 Juni, 10.00 · Rina", "Refund diajukan" to "20 Juni, 12.00 · Sayur rusak", "Petani menyetujui penuh" to "21 Juni, 09.00 · Rp202.500", "Bukti transfer balik diunggah" to "21 Juni, 12.00 · transfer-balik-demo.png", "Dana belum diterima" to "22 Juni, 07.55 · Rina", "Tiket dicatat" to "22 Juni, 08.00 · #TKT-026"))
                EvidenceCard("Bukti dummy keluhan, transfer balik, dan mutasi")
            } }
            item { Panel("Catatan keputusan admin") {
                Field("Hasil pemeriksaan / tindak lanjut (wajib)", note, { note = it }, "Tuliskan bukti yang diperiksa dan tindak lanjut", multiline = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(suspend, { suspend = it }); Text("Suspensi petani (opsional)") }
                if (suspend) Field("Alasan suspensi (wajib jika dipilih)", reason, { reason = it }, multiline = true)
                if (error) Text("Isi catatan keputusan dan alasan suspensi bila dipilih.", color = MaterialTheme.colorScheme.error)
                PrimaryButton("Simpan catatan penanganan", {
                    error = note.isBlank() || (suspend && reason.isBlank())
                    if (!error) onDecision("Catatan disimpan", note + if (suspend) " · Suspensi: $reason" else "", true)
                }, !closed)
                SecondaryButton("Tutup tiket dengan penjelasan", {
                    error = note.isBlank() || (suspend && reason.isBlank())
                    if (!error) onDecision("Ditutup admin", note + if (suspend) " · Suspensi: $reason" else "", true)
                })
            } }
        }
        item { Notice("Ditutup admin ≠ dana diterima", "Penutupan tiket tidak membuktikan refund berhasil. Status dana tetap memerlukan konfirmasi pembeli.", Tone.WARNING) }
    }
}
