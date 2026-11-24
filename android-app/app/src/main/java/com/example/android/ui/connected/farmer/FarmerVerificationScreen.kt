package id.panenku.app.ui.connected.farmer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.ConnectedState

@Composable internal fun FarmerVerificationScreen(s: ConnectedState, back: () -> Unit, image: () -> Unit, submit: (String) -> Unit) {
    val v = s.farmerVerification; val status = v?.text("status")?.ifBlank { null } ?: s.profile?.text("ktpStatus").orEmpty()
    var uri by rememberSaveable { mutableStateOf<String?>(null) }
    var file by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val choose = rememberFarmerImagePicker({ value,name,_ -> uri = value; file = name; error = null }, { error = it })
    val allowed = status in listOf("ditolak", "belum_diajukan")
    FSettingsPage("Status verifikasi", "Pengajuan #${shortId(v?.text("applicationId").orEmpty())} · ${s.profile?.text("ownerName").orEmpty()}", back) {
        FSettingsNotice(when(status) { "ditolak" -> "Pengajuan ditolak · Perlu perbaikan"; "menunggu" -> "Menunggu pemeriksaan"; "disetujui" -> "Identitas sudah disetujui"; else -> "Lengkapi verifikasi petani" },
            if (status == "ditolak") "Admin meninjau ${dateTimeLabel(v?.text("reviewedAt").orEmpty())} WIB. Produk belum dapat diterbitkan sampai verifikasi disetujui."
            else if (status == "menunggu") "Pengajuan telah diterima. Admin akan memeriksa dokumen yang dikirim." else "Status dan dokumen mengikuti pengajuan yang tersimpan.", if (status == "ditolak") "red" else "green")
        if (status == "ditolak") FCard("Alasan dari admin") {
            FBadge("Ditolak", "red")
            val reason = v?.text("rejectionReason").orEmpty()
            FText(reason.lineSequence().firstOrNull().orEmpty().ifBlank { "Periksa kembali dokumen pengajuan" }, 14f, 600)
            FText(reason.lineSequence().drop(1).joinToString("\n").ifBlank { "Ambil ulang seluruh dokumen contoh dengan jelas, lalu kirim kembali sesuai catatan admin." }, 12f)
        }
        FCard("Dokumen sebelumnya") {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFEDF5FC)).clickable(enabled = v?.flag("hasDocument") == true, onClick = image).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FText("KTP", 16f, 700, Color(0xFF237CB4))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FIcon(R.drawable.farmer_identity, 24.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        FText("Nama: ${v?.text("dummyName").orEmpty().ifBlank { "—" }}", 11f)
                        FText("ID: ${v?.text("dummyNik").orEmpty().ifBlank { "—" }}", 11f)
                        FText("Bukan dokumen identitas asli", 10f, color = FMuted)
                    }
                }
            }
            FKeyValue("Berkas dummy", v?.text("fileName").orEmpty().ifBlank { "Belum ada dokumen" } + if ((v?.long("fileSizeBytes") ?: 0) > 0) " · ${shortFileSize(v!!.long("fileSizeBytes"))}" else "", size = 11f)
            FKeyValue("Dikirim", dateTimeLabel(v?.text("submittedAt").orEmpty()) + " WIB", size = 11f)
            FText("Seluruh identitas pada contoh ini fiktif. Tidak menampilkan NIK atau dokumen asli.", 10f, color = FMuted)
        }
        if (allowed) FCard("Perbaiki pengajuan") {
            FUploadCard(file.ifBlank { "Unggah foto KTP pengganti" }, "Foto dokumen contoh · JPG/PNG · Maks. 5 MB", click = choose)
            FText("✓ Nama toko dan lokasi sudah diisi\n○ Gunakan foto contoh yang jelas dan tidak terpotong", 10f, color = FMuted)
            FButton(if (uri == null) "Pilih dokumen & kirim ulang" else "Kirim ulang dokumen", { if (uri == null) choose() else submit(uri!!) }, enabled = !s.busy)
        }
        if (error != null) FSettingsNotice("Gambar belum dapat digunakan", error!!, "red")
        FCard("Alur verifikasi") {
            TimelineRow("Perbaikan diperlukan", "Status aktif: ${verificationLabel(status)}. Kirim ulang dokumen yang diminta.", status == "ditolak" || status == "belum_diajukan", activeMark = "•")
            TimelineRow("Menunggu pemeriksaan", "Setelah dikirim ulang, admin memeriksa pengajuan baru.", status == "menunggu", activeMark = "•")
            TimelineRow("Disetujui", "Jika memenuhi syarat, produk dapat diterbitkan.", status == "disetujui", activeMark = "•")
        }
    }
}
