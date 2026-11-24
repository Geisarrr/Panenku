package id.panenku.app.ui.connected

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import id.panenku.app.data.remote.*
import id.panenku.app.data.rupiah
import id.panenku.app.ui.components.*

@Composable internal fun ConnectedAdminHub(open: (String) -> Unit) {
    Page("Kelola Panenku", admin = true) {
        item { Panel("Akun & operasional") {
            PrimaryButton("Daftar pengguna", { open("admin-users") })
            SecondaryButton("Pengaturan transaksi & WhatsApp CS", { open("admin-settings") })
            SecondaryButton("Audit log", { open("admin-audit") })
        } }
        item { Panel("Transaksi & katalog") {
            PrimaryButton("Sengketa refund", { open("admin-disputes") })
            SecondaryButton("Laporan pembayaran terlambat", { open("admin-late-payments") })
            SecondaryButton("Kelola komoditas", { open("commodities") })
        } }
    }
}

@Composable internal fun ConnectedAdminUsers(s: ConnectedState, vm: ConnectedViewModel, back: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable(s.adminUserDetail?.text("id")) { mutableStateOf("") }
    Page("Daftar pengguna", "Cari, filter, dan periksa akun.", back, admin = true) {
        item { Panel("Filter") {
            Field("Cari email atau nama", search, { search = it.take(100) })
            Text("Peran")
            Row { listOf("" to "Semua", "pembeli" to "Pembeli").forEach { (v, label) ->
                FilterChip(role == v, { role = v }, { Text(label) })
            } }
            Row { listOf("petani" to "Petani", "admin" to "Admin").forEach { (v, label) ->
                FilterChip(role == v, { role = v }, { Text(label) })
            } }
            Text("Status akun")
            Row { listOf("" to "Semua", "active" to "Aktif", "suspended" to "Ditangguhkan").forEach { (v, label) ->
                FilterChip(status == v, { status = v }, { Text(label) })
            } }
            PrimaryButton("Terapkan filter", { vm.adminUsers(role.ifBlank { null }, status.ifBlank { null }, search) })
        } }
        if (s.adminUsers.isEmpty() && !s.busy) item { Text("Tidak ada pengguna yang sesuai filter.") }
        items(s.adminUsers, key = { it.text("id") }) { user -> Panel(user.text("displayName").ifBlank { user.text("email") }) {
            Text(user.text("email")); Text("${user.text("role")} · ${user.text("status")}")
            user.text("ktpStatus").takeIf(String::isNotBlank)?.let { Text("KTP: $it") }
            SecondaryButton("Lihat detail pengguna", { reason = ""; vm.adminUser(user.text("id")) })
        } }
        s.adminUserDetail?.let { detail -> item { Panel("Detail ${detail.text("displayName").ifBlank { detail.text("email") }}") {
            Text(detail.text("email")); Text("Peran: ${detail.text("role")}"); Text("Status: ${detail.text("status")}")
            detail.text("ownerName").takeIf(String::isNotBlank)?.let { Text("Pemilik: $it") }
            detail.text("storeName").takeIf(String::isNotBlank)?.let { Text("Toko: $it") }
            detail.text("pickupLocation").takeIf(String::isNotBlank)?.let { Text("Lokasi: $it") }
            detail.text("ktpStatus").takeIf(String::isNotBlank)?.let { Text("KTP: $it") }
            if (detail.long("activeRefundCount") > 0) Text("Refund aktif: ${detail.long("activeRefundCount")}")
            detail.text("suspensionReason").takeIf(String::isNotBlank)?.let { Text("Alasan penangguhan: $it") }
            if (detail.text("role") == "petani") {
                Field(if (detail.text("status") == "suspended") "Catatan pemulihan" else "Alasan penangguhan", reason,
                    { reason = it.take(1000) }, multiline = true)
                if (detail.text("status") == "suspended") PrimaryButton("Pulihkan akun", {
                    vm.suspendUser(detail.text("id"), false, reason)
                }) else PrimaryButton("Tangguhkan akun", {
                    vm.suspendUser(detail.text("id"), true, reason)
                }, reason.isNotBlank())
            }
        } } }
    }
}

@Composable internal fun ConnectedAdminSettings(s: ConnectedState, vm: ConnectedViewModel, back: () -> Unit) {
    Page("Pengaturan admin", "Batas checkout dan WhatsApp CS.", back, admin = true) {
        item { SecondaryButton("Muat ulang pengaturan", vm::adminSettings) }
        val settings = s.settings
        if (settings == null && !s.busy) item { Text("Pengaturan belum dapat ditampilkan.") }
        settings?.let { item { key(it.toString()) { AdminSettingsForm(it, s.busy, vm) } } }
    }
}

@Composable private fun AdminSettingsForm(settings: com.google.gson.JsonObject, busy: Boolean, vm: ConnectedViewModel) {
    val oldLimit = settings.long("transactionLimit")
    var limit by rememberSaveable { mutableStateOf(oldLimit.toString()) }
    var whatsapp by rememberSaveable { mutableStateOf(settings.text("csWhatsapp")) }
    var reason by rememberSaveable { mutableStateOf("") }
    val parsed = limit.toLongOrNull()
    Panel("Pengaturan versi ${settings.long("settingsVersion")}") {
        Field("Batas transaksi checkout (rupiah)", limit, { limit = it.filter(Char::isDigit) })
        Field("WhatsApp CS", whatsapp, { whatsapp = it.take(50) }, "+6281234567890")
        Field("Alasan perubahan limit", reason, { reason = it.take(1000) }, multiline = true)
        Text("Alasan wajib jika nilai limit berubah. Nomor WhatsApp wajib memakai kode negara, misalnya +62.")
        settings.text("lastChangedByName").takeIf(String::isNotBlank)?.let { Text("Terakhir diubah oleh $it · ${settings.text("lastChangedAt")}") }
        PrimaryButton("Simpan pengaturan", { parsed?.let { vm.saveAdminSettings(it, whatsapp, reason) } },
            parsed != null && parsed > 0 && whatsapp.isNotBlank() && (parsed == oldLimit || reason.isNotBlank()) && !busy)
    }
}

@Composable internal fun ConnectedAdminAudit(s: ConnectedState, vm: ConnectedViewModel, back: () -> Unit) {
    var action by rememberSaveable { mutableStateOf("") }
    Page("Audit log", "Riwayat tindakan administratif.", back, admin = true) {
        item { Panel("Filter") {
            Field("Kode aksi (opsional)", action, { action = it.take(100) }, "USER_SUSPENDED")
            PrimaryButton("Muat audit", { vm.auditLogs(action) })
        } }
        if (s.auditLogs.isEmpty() && !s.busy) item { Text("Belum ada audit log untuk filter ini.") }
        items(s.auditLogs, key = { it.text("id") }) { log -> Panel(log.text("action")) {
            Text("${log.text("createdAt")} · ${log.text("actorName").ifBlank { log.text("actorEmail") }}")
            Text("Target: ${log.text("targetType")} ${log.text("targetId")}")
            log.text("reason").takeIf(String::isNotBlank)?.let { Text("Alasan: $it") }
            log.get("settingsVersion")?.takeUnless { it.isJsonNull }?.let { Text("Versi pengaturan: ${it.asLong}") }
        } }
    }
}

@Composable internal fun ConnectedAdminDisputes(s: ConnectedState, vm: ConnectedViewModel, back: () -> Unit) {
    Page("Sengketa refund", "Admin memutuskan sengketa; suspend akun dilakukan terpisah.", back, admin = true) {
        item { SecondaryButton("Muat ulang sengketa", vm::disputes) }
        if (s.disputes.isEmpty() && !s.busy) item { Text("Tidak ada sengketa refund yang terbuka.") }
        items(s.disputes, key = { it.text("id") }) { dispute ->
            var reason by rememberSaveable(dispute.text("id")) { mutableStateOf("") }
            Panel("Pesanan #${dispute.text("orderId")}") {
                Text("Status: ${dispute.text("status")}"); Text(dispute.text("reason"))
                SecondaryButton("Lihat bukti refund", { vm.image("admin/refunds/${dispute.text("refundId")}/evidence") })
                Field("Alasan keputusan admin", reason, { reason = it.take(1000) }, multiline = true)
                PrimaryButton("Setujui refund", { vm.decideDispute(dispute.text("id"), true, reason) }, reason.isNotBlank())
                SecondaryButton("Tutup sengketa", { vm.decideDispute(dispute.text("id"), false, reason) }, enabled = reason.isNotBlank())
            }
        }
    }
}

@Composable internal fun ConnectedLatePayments(s: ConnectedState, vm: ConnectedViewModel, admin: Boolean, back: (() -> Unit)? = null) {
    var orderId by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    Page(if (admin) "Pembayaran terlambat" else "Bantuan pembayaran", if (admin) "Laporan diperiksa manual oleh admin." else "Untuk transfer setelah pesanan kedaluwarsa.", back, admin) {
        item { SecondaryButton("Muat ulang laporan", { vm.latePayments(admin) }) }
        if (!admin) item { Panel("Buat laporan") {
            Field("ID pesanan", orderId, { orderId = it.trim() })
            Field("Nominal yang ditransfer", amount, { amount = it.filter(Char::isDigit) })
            Field("Penjelasan", reason, { reason = it.take(1000) }, multiline = true)
            MediaPicker("Bukti transfer terlambat", image, { image = it })
            PrimaryButton("Kirim laporan", {
                val value = amount.toLongOrNull(); if (value != null && image != null) vm.reportLatePayment(orderId, reason, value, image!!)
            }, orderId.isNotBlank() && amount.toLongOrNull()?.let { it > 0 } == true && reason.isNotBlank() && image != null)
        } }
        if (s.latePayments.isEmpty() && !s.busy) item { Text("Belum ada laporan pembayaran terlambat.") }
        items(s.latePayments, key = { it.text("id") }) { report ->
            var actionReason by rememberSaveable(report.text("id")) { mutableStateOf("") }
            Panel("Pesanan #${report.text("orderId")}") {
                Text("${report.text("status").replace('_', ' ')} · klaim ${rupiah(report.long("claimedAmount"))}")
                Text("Total pesanan: ${rupiah(report.long("expectedAmount"))}"); Text(report.text("reason"))
                SecondaryButton("Lihat bukti transfer", { vm.image("${if (admin) "admin" else "buyer"}/late-payment-reports/${report.text("id")}/evidence") })
                if (admin && report.text("status") != "ditutup") {
                    Field("Alasan pemeriksaan", actionReason, { actionReason = it.take(1000) }, multiline = true)
                    if (report.text("status") == "diajukan") {
                        PrimaryButton("Tindak lanjuti", { vm.reviewLatePayment(report.text("id"), true, actionReason) }, actionReason.isNotBlank())
                        SecondaryButton("Bukti tidak cukup", { vm.reviewLatePayment(report.text("id"), false, actionReason) }, enabled = actionReason.isNotBlank())
                    } else PrimaryButton("Tutup laporan", { vm.closeLatePayment(report.text("id"), actionReason) }, actionReason.isNotBlank())
                }
            }
        }
    }
}
