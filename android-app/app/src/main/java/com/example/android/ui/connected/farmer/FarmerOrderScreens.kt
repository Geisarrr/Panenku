package id.panenku.app.ui.connected.farmer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.ConnectedState
import java.time.LocalDate

@Composable internal fun FarmerOrdersScreen(s: ConnectedState, initialFilter: String = "semua", open: (String) -> Unit) {
    var tab by rememberSaveable(initialFilter) { mutableStateOf(if (initialFilter in listOf("diproses", "selesai", "dibatalkan")) initialFilter else "semua") }
    var special by rememberSaveable(initialFilter) { mutableStateOf(initialFilter.takeIf { it in listOf("menunggu", "verifikasi") }) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 24.dp)) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            FText("Kelola Permintaan", 14f, color = FMuted, line = 20f)
            FText("Pesanan Toko", 16f, 600, line = 24f)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2F2F2)).padding(4.dp)) {
            listOf("semua" to "Semua", "diproses" to "Diproses", "selesai" to "Selesai", "dibatalkan" to "Dibatalkan").forEach { (value, label) ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if (tab == value) Color.White else Color.Transparent)
                    .clickable(role = Role.Tab) { tab = value; special = null }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                    FText(label, 13f, if (tab == value) 600 else 400, if (tab == value) FInk else FMuted, line = 20f)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (special != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                FText(if (special == "menunggu") "Menunggu konfirmasi" else "Verifikasi pembayaran", 12f, 600, FForest, Modifier.weight(1f))
                FText("Tampilkan semua", 11f, color = FForest, modifier = Modifier.clickable { special = null })
            }
        }
        val rows = s.orders.filter { order ->
            val status = order.text("orderStatus")
            when {
                special == "menunggu" -> status == "menunggu_petani"
                special == "verifikasi" -> status == "verifikasi_pembayaran"
                tab == "diproses" -> status in listOf("diproses", "diantar", "siap_diambil", "menunggu_penerimaan")
                tab == "dibatalkan" -> status in listOf("dibatalkan", "kedaluwarsa", "gagal_dipenuhi")
                tab == "selesai" -> status == "selesai"
                else -> true
            }
        }
        if (rows.isEmpty()) FNotice("Belum ada pesanan", "Pesanan dari pembeli akan ditampilkan di sini.")
        rows.forEachIndexed { i, order ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            OrderListCard(order, s.farmerOrderProofs[order.text("orderId")].orEmpty()) { open(order.text("orderId")) }
        }
    }
}
@Composable private fun OrderListCard(order: JsonObject, proofs: List<JsonObject>, open: () -> Unit) {
    val status = order.text("orderStatus")
    FCard(padding = 12.dp, gap = 16.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                FText("#${shortId(order.text("orderId"))}", 14f, color = FMuted, line = 20f)
                FText(order.text("buyerName").ifBlank { "Pembeli" }, 16f, 600, line = 24f)
            }
            FBadge(orderLabel(status), if (status in listOf("menunggu_petani", "menunggu_pembayaran", "verifikasi_pembayaran")) "orange" else if (status == "dibatalkan") "gray" else "green")
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FKeyValue("Produk:", order.orderItemsLabel(), size = 14f)
            FKeyValue("Total:", money(order.long("totalPayment")), true, 14f)
            if (status == "verifikasi_pembayaran") {
                FKeyValue("Bukti:", if (proofs.isNotEmpty()) "${proofs.size} berkas · ${fileSize(proofs.sumOf { it.long("fileSizeBytes") })}" else "Perlu diperiksa", size = 14f)
                FKeyValue("Diunggah:", proofs.lastOrNull()?.text("submittedAt")?.let(::dateTimeLabel) ?: "Lihat detail bukti", size = 14f)
            } else {
                FKeyValue("Pengiriman:", "${dateLabel(order.scheduleDate(), true)} · ${displaySlot(order.scheduleSlot())}", size = 14f)
                FKeyValue("Status:", paymentLabel(order.text("paymentStatus")), size = 14f)
            }
        }
        Row {
            Box(Modifier.widthIn(min = 90.dp, max = 170.dp)) {
                FButton(when (status) { "verifikasi_pembayaran" -> "Periksa bukti"; "diproses" -> "Siapkan pesanan"; else -> "Lihat detail" }, open, height = 28.dp, font = 13f)
            }
        }
    }
}

@Composable internal fun FarmerPendingScreen(s: ConnectedState, order: JsonObject, back: () -> Unit,
    respond: (Boolean, String) -> Unit, schedule: () -> Unit) {
    var reason by rememberSaveable(order.text("orderId")) { mutableStateOf("") }
    var customReason by rememberSaveable(order.text("orderId")) { mutableStateOf("") }
    val finalReason = if (reason == "Lainnya") customReason.trim() else reason.trim()
    val pending = order.text("orderStatus") == "menunggu_petani"
    FPage(if (pending) "Detail pesanan masuk" else "Detail pesanan", "#${shortId(order.text("orderId"))} · ${dateTimeLabel(order.text("createdAt"))}", back) {
        FNotice("${orderLabel(order.text("orderStatus"))} · ${paymentLabel(order.text("paymentStatus"))}",
            if (pending) "Tanggapi paling lambat ${dateTimeLabel(order.text("expiresAt"))} WIB. Pembeli baru membayar setelah kamu menerima." else "${orderLabel(order.text("orderStatus"))}. Jadwal dan total mengikuti rincian pesanan yang tersimpan.", true)
        FCard("Pembeli & pemenuhan") {
            FDestination(R.drawable.farmer_person, order.text("buyerName").ifBlank { "Pembeli" }, "Pembeli · ${order.list("items").size} produk, total ${order.totalKg()} kg")
            FRule()
            FBadge(if (order.text("fulfillmentMethod") == "pickup") "Ambil di toko" else "Antar oleh petani")
            FText("${dateLabel(order.scheduleDate())} · ${displaySlot(order.scheduleSlot())} WIB", 13f, 600, line = 18f)
            val ready = order.list("items").map { it.text("readyDateSnapshot") }.filter(String::isNotBlank).maxOrNull().orEmpty()
            FText(if (ready.isNotBlank()) "Produk siap mulai ${dateLabel(ready)}. Jadwal sesuai slot layanan toko." else "Jadwal sesuai pilihan pembeli dan layanan toko.", 12f, color = FMuted, line = 15f)
            FDestination(R.drawable.farmer_location, if (order.text("fulfillmentMethod") == "pickup") "Lokasi pengambilan" else "Alamat pengiriman",
                order.text(if (order.text("fulfillmentMethod") == "pickup") "pickupLocationSnapshot" else "deliveryAddressSnapshot").ifBlank { "Alamat belum tersedia" })
            if (order.text("buyerNotes").isNotBlank()) FText("Catatan: ${order.text("buyerNotes")}", 12f, color = FMuted)
        }
        OrderItemsCard(order, s.products)
        if (pending) FCard("Keputusan petani") {
            FButton("Terima pesanan", { respond(true, "") }, enabled = !s.busy)
            FText("Setelah diterima: Menunggu Pembayaran. Pembeli diberi waktu 24 jam untuk mengunggah bukti ke rekening / QRIS toko.", 12f, color = FMuted, line = 15f)
            FRule()
            FSelectField("Alasan penolakan (wajib jika menolak)", reason.ifBlank { "Pilih / tulis alasan penolakan" },
                listOf("Stok tidak mencukupi" to "Stok tidak mencukupi", "Jadwal tidak tersedia" to "Jadwal tidak tersedia", "Lainnya" to "Lainnya"), reason, { reason = it }, enabled = !s.busy)
            if (reason == "Lainnya") FField("Tuliskan alasan", customReason, { customReason = it }, multiline = true)
            FButton("Tolak dengan alasan", { respond(false, finalReason) }, primary = false, enabled = finalReason.isNotBlank() && !s.busy)
            FText("Jika ditolak atau tenggat habis, pesanan dibatalkan dan stok yang ditahan tersedia kembali. Tidak ada dana yang perlu dikembalikan.", 12f, color = FMuted, line = 15f)
        }
        if (!pending && canProposeSchedule(order)) FButton("Usulkan perubahan jadwal", schedule, primary = false)
        if (!pending && order.obj("pendingScheduleChange").text("id").isNotBlank()) SchedulePendingNotice(order.obj("pendingScheduleChange"))
    }
}
@Composable internal fun OrderItemsCard(order: JsonObject, products: List<JsonObject>) {
    FCard("Rincian pesanan tersimpan") {
        order.list("items").forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                val photo = products.find { it.text("id") == item.text("productId") }?.text("photoAccessUrl").orEmpty()
                FPhoto(photo, item.text("productNameSnapshot"), Modifier.size(42.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FText(item.text("productNameSnapshot"), 13f, 600)
                    FText("${item.qty("quantityKg")} kg × ${money(item.long("pricePerKgSnapshot"))}/kg", 11f, color = FMuted)
                }
                FText(money(item.long("itemSubtotal")), 12f, 600)
            }
        }
        FRule(); FKeyValue("Subtotal", money(order.long("subtotalItems")))
        FKeyValue("Ongkir petani", money(order.long("deliveryFee")))
        FKeyValue("Total", money(order.long("totalPayment")), true, 14f)
        FText("Harga ini mengikuti rincian saat pesanan dibuat, bukan perubahan harga produk terbaru.", 10f, color = FMuted)
    }
}
@Composable internal fun SchedulePendingNotice(proposal: JsonObject) {
    FNotice("Menunggu persetujuan pembeli", "${dateLabel(proposal.text("proposedDate"))} · ${displaySlot(proposal.text("proposedSlot"))}. ${proposal.text("reason")} Jadwal lama tetap berlaku sampai pembeli menyetujui.")
}

@Composable internal fun FarmerPaymentScreen(s: ConnectedState, order: JsonObject, back: () -> Unit,
    image: (String) -> Unit, verify: (String, Boolean, String) -> Unit, refresh: () -> Unit) {
    var reason by rememberSaveable(order.text("orderId")) { mutableStateOf("") }
    val proof = s.proofs.lastOrNull { it.text("status") == "menunggu_verifikasi" } ?: s.proofs.lastOrNull()
    val target = s.destination
    FPage("Tinjau bukti pembayaran", "#${shortId(order.text("orderId"))} · ${order.text("buyerName")}", back) {
        FBadge("Verifikasi Pembayaran", "orange")
        FNotice("Cocokkan dengan dana masuk", "Bukti terunggah belum berarti pembayaran disetujui. Cek mutasi rekening atau riwayat QRIS di luar Panenku sebelum memberi keputusan.")
        if (proof == null) {
            FNotice("Bukti belum tersedia", "Muat ulang bukti pembayaran untuk pesanan ini.")
            FButton("Muat ulang bukti", refresh, primary = false)
        } else {
            FCard("Bukti pembeli · Percobaan ${s.proofs.indexOf(proof) + 1}") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F5F5)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FText(if (proof.flag("designDemo")) "BUKTI DEMO - bukan transaksi nyata" else "BUKTI PEMBAYARAN DIUNGGAH", 10f, 600, FMuted)
                    FIcon(R.drawable.farmer_bank, 22.dp)
                    FText(money(proof.long("expectedAmount")), 20f, 700, FForest, line = 25f)
                    FKeyValue("Penerima", target?.text("accountHolderName").orEmpty().ifBlank { "—" }, size = 11f)
                    FKeyValue("Bank", listOfNotNull(target?.text("bankName"), target?.text("accountNumber")?.takeLast(4)?.let { "•••• $it" }).joinToString(" · ").ifBlank { "QRIS" }, size = 11f)
                    FText("${dateTimeLabel(proof.text("submittedAt"))} WIB${if (proof.flag("designDemo")) " · Data ilustratif" else ""}", 9f, color = FMuted)
                }
                FKeyValue("Berkas", "${proof.text("originalFileName").ifBlank { "Bukti pembayaran" }} · ${fileSize(proof.long("fileSizeBytes"))}", size = 11f)
                FKeyValue("Dikirim pembeli", "${dateTimeLabel(proof.text("submittedAt"))} WIB", size = 11f)
                FButton(if (proof.flag("designDemo")) "Buka bukti demo lengkap" else "Buka bukti pembayaran lengkap", { image(proof.text("id")) }, primary = false)
            }
        }
        FCard("Nominal yang diharapkan") {
            FKeyValue("Produk", money(order.long("subtotalItems")))
            FKeyValue("Ongkir", money(order.long("deliveryFee")))
            FKeyValue("Total harus diterima", money(order.long("totalPayment")), true, 14f)
            FRule()
            FKeyValue("Rekening toko contoh".takeIf { proof?.flag("designDemo") == true } ?: "Rekening tujuan toko",
                listOfNotNull(target?.text("bankName"), target?.text("accountNumber")?.takeLast(4)?.let { "•••• $it" }).joinToString(" · ").ifBlank { "QRIS" }, size = 11f)
            FKeyValue("Nama penerima", target?.text("accountHolderName").orEmpty().ifBlank { "—" }, size = 11f)
        }
        FCard("Riwayat pembayaran") {
            val accepted = order.list("history").firstOrNull { it.text("toStatus") == "menunggu_pembayaran" }
            TimelineRow("Pesanan diterima petani", "${dateTimeLabel(accepted?.text("changedAt").orEmpty())} · Menunggu Pembayaran", true)
            s.proofs.forEach { row -> TimelineRow("Bukti diunggah ${order.text("buyerName")}", "${dateTimeLabel(row.text("submittedAt"))} · ${paymentLabel(row.text("status"))}", true) }
            FText("Tidak ada penolakan sebelumnya.".takeIf { s.proofs.none { it.text("decision") == "tolak" } } ?: "Lihat hasil pemeriksaan pada riwayat bukti.", 10f, color = FMuted)
        }
        if (proof != null) FCard("Keputusan pemeriksaan") {
            val enabled = proof.text("status") == "menunggu_verifikasi" && !s.busy && order.text("orderStatus") == "verifikasi_pembayaran"
            FButton("Setujui pembayaran", { verify(proof.text("id"), true, "") }, enabled = enabled)
            FText("Hanya jika dana sesuai telah masuk. Setelah disetujui, pesanan menjadi Diproses.", 12f, color = FMuted)
            FRule()
            FField("Alasan penolakan (wajib)", reason, { reason = it }, "Contoh: nominal / penerima tidak jelas")
            FButton("Tolak bukti dengan alasan", { verify(proof.text("id"), false, reason.trim()) }, primary = false, enabled = enabled && reason.isNotBlank())
            FText("Penolakan tersimpan pada riwayat; pembeli mendapat waktu unggah ulang 24 jam sejak ditolak.", 12f, color = FMuted)
        }
    }
}
private fun fileSize(bytes: Long): String = if (bytes <= 0) "Ukuran tidak tersedia" else String.format(java.util.Locale("id", "ID"), "%.1f MB", bytes / (1024.0 * 1024))
@Composable internal fun TimelineRow(title: String, body: String, active: Boolean, activeMark: String = "✓") {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (active) Color(0xFFE2F6DE) else Color(0xFFF5F5F5)), contentAlignment = Alignment.Center) {
            FText(if (active) activeMark else "○", 11f, 600, if (active) FForest else Color(0xFFAAAAAA))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FText(title, 13f, 600, line = 18f); FText(body, 12f, color = FMuted, line = 15f)
        }
    }
}

@Composable internal fun FarmerProcessingScreen(s: ConnectedState, order: JsonObject, back: () -> Unit,
    fulfill: (String, String) -> Unit, schedule: () -> Unit) {
    var reason by rememberSaveable(order.text("orderId")) { mutableStateOf("") }
    val status = order.text("orderStatus")
    val pickup = order.text("fulfillmentMethod") == "pickup"
    FPage("Penyiapan Pesanan", "#${shortId(order.text("orderId"))} · ${dateLabel(order.scheduleDate())}", back) {
        FCard("Ringkasan pesanan") {
            FBadge("${orderLabel(status)} · Pembayaran disetujui")
            FText("${order.text("buyerName")} · ${order.totalKg()} kg", 13f, 600, line = 18f)
            FText("${order.orderItemsLabel()} Total dibayar ${money(order.long("totalPayment"))}, termasuk ongkir.", 12f, line = 15f)
            FDestination(R.drawable.farmer_location, if (pickup) "Ambil di toko" else "Antar ke ${order.text("deliveryDistrictSnapshot").ifBlank { "pembeli" }}",
                order.text(if (pickup) "pickupLocationSnapshot" else "deliveryAddressSnapshot"))
            FKeyValue("Jadwal berlaku", "${dateLabel(order.scheduleDate(), true)} · ${displaySlot(order.scheduleSlot())}")
            if (status == "diproses") FButton(if (pickup) "Tandai siap diambil" else "Tandai mulai diantar", { fulfill(if (pickup) "siap_diambil" else "diantar", "") }, enabled = !s.busy)
            if (status in listOf("diantar", "siap_diambil")) FButton("Tandai diserahkan", { fulfill("menunggu_penerimaan", "") }, enabled = !s.busy)
            if (status == "menunggu_penerimaan") FNotice("Menunggu penerimaan", "Pembeli perlu mengonfirmasi bahwa pesanan telah diterima.")
            FButton("Usulkan perubahan jadwal", schedule, primary = false)
        }
        FCard("Tahap yang tepat untuk setiap layanan") {
            TimelineRow(if (pickup) "Diproses → Siap Diambil" else "Diproses → Diantar", if (pickup) "Tandai saat pesanan siap diambil pembeli." else "Tandai saat pesanan berangkat ke pembeli.", status != "menunggu_penerimaan")
            TimelineRow(if (pickup) "Siap Diambil → Menunggu Penerimaan" else "Diantar → Menunggu Penerimaan", "Gunakan Tandai tiba hanya saat diserahkan. Pembeli kemudian mengonfirmasi.", status == "menunggu_penerimaan")
            FRule()
            FText("Ambil: Diproses → Siap Diambil", 13f, 600)
            FText("Setelah barang diserahkan, pilih Tandai diserahkan → Menunggu Penerimaan.", 12f, color = FMuted)
        }
        FNotice("Bukan Selesai saat diserahkan", "Status Selesai hanya setelah pembeli mengonfirmasi penerimaan. Jangan tandai pesanan tiba sebelum benar-benar sampai.")
        if (status in listOf("diproses", "diantar", "siap_diambil")) FCard("Jika gagal memenuhi pesanan") {
            FField("Alasan gagal pemenuhan (wajib)", reason, { reason = it }, "Tulis kendala dan upaya yang dilakukan")
            FButton("Laporkan gagal pemenuhan", { fulfill("gagal_dipenuhi", reason.trim()) }, primary = false, enabled = reason.isNotBlank() && !s.busy)
            FText("Pesanan berbayar dapat diajukan refund penuh oleh pembeli. Stok yang ditahan dilepas; pengembalian dana tetap perlu diproses.", 12f, color = FMuted)
        }
    }
}

@Composable internal fun FarmerScheduleScreen(s: ConnectedState, order: JsonObject, back: () -> Unit, initialReason: String = "",
    submit: (String, String, String) -> Unit) {
    var date by rememberSaveable(order.text("orderId")) { mutableStateOf(runCatching { LocalDate.parse(order.scheduleDate()).plusDays(1).toString() }.getOrDefault("")) }
    var slot by rememberSaveable(order.text("orderId")) { mutableStateOf(order.scheduleSlot()) }
    var reason by rememberSaveable(order.text("orderId")) { mutableStateOf(initialReason) }
    var validation by remember { mutableStateOf<String?>(null) }
    val allowed = canProposeSchedule(order)
    val slots = s.profile?.getAsJsonArray("timeSlots")?.map { it.asString }.orEmpty()
    FPage("Usulkan jadwal baru", "#${shortId(order.text("orderId"))} · ${order.text("buyerName")}", back) {
        FCard("Jadwal yang masih berlaku") {
            FBadge("${orderLabel(order.text("orderStatus"))} · ${if (order.text("fulfillmentMethod") == "pickup") "Layanan ambil" else "Layanan antar"}")
            FKeyValue("Tanggal", dateLabel(order.scheduleDate()))
            FKeyValue("Jam", "${displaySlot(order.scheduleSlot())} WIB")
            FDestination(R.drawable.farmer_location, order.text("deliveryDistrictSnapshot").ifBlank { "Lokasi toko" }, "Alamat dan total pesanan tetap sama.")
        }
        FCard("Usulan perubahan") {
            FDateField("Tanggal baru", date) { date = it }
            val ready = order.list("items").map { it.text("readyDateSnapshot") }.filter(String::isNotBlank).maxOrNull().orEmpty()
            if (ready.isNotBlank()) FText("Valid: setelah tanggal siap produk ${dateLabel(ready)}.", 10f, color = FMuted)
            FSelectField("Slot layanan", "${if (slot.substringBefore(':').toIntOrNull()?.let { it < 12 } == true) "Pagi" else "Siang"} · ${displaySlot(slot)} WIB",
                slots.map { it to "${displaySlot(it)} WIB" }, slot, { slot = it })
            FText("Kami masih melayani toko pada slot yang tersedia.", 10f, color = FMuted)
            FField("Alasan perubahan (wajib)", reason, { reason = it }, "Tulis alasan perubahan jadwal", multiline = true)
            FText("Pembeli akan melihat jadwal lama, jadwal usulan, dan alasan ini sebelum memilih setuju atau tolak.", 12f, color = FMuted, line = 15f)
        }
        if (allowed) FNotice("Menunggu persetujuan pembeli", "Mengirim usulan tidak mengganti jadwal saat ini. Jadwal ${dateLabel(order.scheduleDate(), true)} tetap berlaku sampai pembeli menyetujui. Jika ditolak, kembali jadwal lama atau diskusikan kendalanya.")
        else FNotice("Perubahan jadwal belum tersedia", if (order.obj("pendingScheduleChange").text("id").isNotBlank()) "Masih ada usulan yang menunggu keputusan pembeli." else "Usulan hanya dapat dikirim sebelum pembayaran dan sebelum ada bukti pembayaran. Jadwal pesanan ini tetap berlaku.", true)
        if (validation != null) FNotice("Periksa usulan", validation!!, true)
        FButton("Kirim usulan ke pembeli", {
            if (date.isBlank() || slot.isBlank() || reason.isBlank() || slot !in slots) validation = "Pilih tanggal, slot layanan toko, dan isi alasan perubahan."
            else submit(date, slot, reason.trim())
        }, enabled = allowed && !s.busy)
        FButton("Batalkan perubahan", back, primary = false)
        FCard("Setelah usulan dikirim") {
            FText("Status usulan menjadi “Menunggu persetujuan pembeli”. Jangan mengirim ulang usulan yang sama atau menganggap diam sebagai persetujuan.", 12f, line = 15f)
            FKeyValue("Total dibayar tetap", money(order.long("totalPayment")), true, 14f)
        }
    }
}
