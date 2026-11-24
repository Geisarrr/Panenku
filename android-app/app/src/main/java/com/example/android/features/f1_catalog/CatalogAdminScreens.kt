package id.panenku.app.features.f1_catalog

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
fun AdminCommoditiesScreen(settings: Map<String, Map<String, String>>, onBack: () -> Unit, onSave: (String, Map<String, String>) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Semua") }
    var selected by rememberSaveable { mutableStateOf("Sayur") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(selected, creating) { mutableStateOf(if (creating) "" else selected) }
    var description by rememberSaveable(selected, creating) { mutableStateOf(settings["commodity-$selected"]?.get("Deskripsi") ?: "Sayuran segar dari petani") }
    var active by rememberSaveable(selected, creating) { mutableStateOf(settings["commodity-$selected"]?.get("Aktif")?.toBoolean() ?: (selected != "Rempah")) }
    val names = (listOf("Sayur", "Buah-buahan", "Bawang", "Umbi", "Biji-bijian & Padi", "Rempah") + settings.keys.filter { it.startsWith("commodity-") }.map { it.removePrefix("commodity-") }).distinct()
    fun enabled(n: String) = settings["commodity-$n"]?.get("Aktif")?.toBoolean() ?: (n != "Rempah")
    val filtered = names.filter { it.contains(query, true) && (filter == "Semua" || (filter == "Aktif") == enabled(it)) }
    Page("Kelola komoditas", "Kategori tidak dihapus dari riwayat produk", onBack, true) {
        item { SearchField(query, { query = it }, "Cari komoditas") }
        item { FilterChips(listOf("Semua", "Aktif", "Nonaktif"), filter, { filter = it }) }
        item { Panel("Daftar komoditas") {
            filtered.forEach { n -> MenuRow(n, if (enabled(n)) "Aktif" else "Nonaktif", Icons.Outlined.Category) { selected = n; creating = false } }
            if (filtered.isEmpty()) Text("Komoditas tidak ditemukan.")
            SecondaryButton("+ Tambah komoditas baru", { creating = true; selected = "baru" })
        } }
        item { Panel(if (creating) "Tambah komoditas" else "Edit komoditas · $selected") {
            Field("Nama komoditas", name, { name = it }, readOnly = !creating)
            Field("Deskripsi singkat", description, { description = it }, multiline = true)
            Text("Satuan perbandingan: Kilogram (kg) · Rp/kg", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Aktif", Modifier.weight(1f)); Switch(active, { active = it }) }
            PrimaryButton("Simpan komoditas", { onSave("commodity-${name.trim()}", mapOf("Deskripsi" to description, "Aktif" to active.toString())) }, name.isNotBlank() && (!creating || name.trim() !in names))
            if (!creating) SecondaryButton("Nonaktifkan tanpa menghapus", { active = false; onSave("commodity-$selected", mapOf("Deskripsi" to description, "Aktif" to "false")) })
        } }
        item { Notice("Perubahan tercatat", "Kategori nonaktif tidak dipakai untuk produk baru. Riwayat dan transaksi lama tetap tersimpan.") }
    }
}

@Composable
fun AdminModerationScreen(decision: String?, onBack: () -> Unit, onDecision: (String, String, Boolean) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Perlu tinjau") }
    val visible = when (filter) { "Ditindak" -> decision?.startsWith("Dinonaktifkan") == true; "Tidak melanggar" -> decision?.startsWith("Tidak melanggar") == true; else -> decision == null }
    Page("Moderasi produk", "Laporan produk menunggu tinjauan", onBack, true) {
        item { FilterChips(listOf("Perlu tinjau", "Ditindak", "Tidak melanggar"), filter, { filter = it }) }
        if (!visible) item { EmptyState("Tidak ada laporan pada filter ini") }
        else {
            item { Panel("Laporan #LAP-031") {
                StatusBadge(decision ?: "Kategori tidak sesuai", Tone.WARNING)
                KeyValue("Dilaporkan", "22 Juni 2026, 07.40")
                Text("Produk bawang dimasukkan ke komoditas buah-buahan. Pembeli kesulitan menemukan kategori yang tepat.")
                Text("Pelapor: Pembeli Contoh · identitas disamarkan", style = MaterialTheme.typography.bodySmall)
            } }
            item { Panel("Produk yang dilaporkan") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProductImage("shallot", Modifier.size(75.dp)); Column { Text("Bawang Merah", style = MaterialTheme.typography.titleMedium); Text("Rp35.000/kg"); Text("#PRD-052 · Tani Contoh", style = MaterialTheme.typography.bodySmall) }
                }
                KeyValue("Kategori terpasang", "Buah-buahan"); KeyValue("Stok tersedia", "12 kg"); KeyValue("Status petani", "Terverifikasi · Aktif")
            } }
            item { Panel("Keputusan moderasi") {
                Field("Alasan penonaktifan (wajib)", reason, { reason = it }, "Jelaskan alasan dan perbaikan yang diperlukan", multiline = true)
                PrimaryButton("Nonaktifkan produk", { onDecision("Dinonaktifkan", reason, true) }, decision == null)
                SecondaryButton("Tandai tidak melanggar", { onDecision("Tidak melanggar", "Laporan ditinjau", false) }, enabled = decision == null)
                Text("Produk nonaktif tidak dapat dibeli; rincian transaksi lama tetap tersimpan.", style = MaterialTheme.typography.bodySmall)
            } }
        }
        item { Notice("Konteks audit", decision ?: "Belum ada tindakan pada #PRD-052. Menonaktifkan produk tidak otomatis menyuspensi petani.") }
    }
}
