package id.panenku.app.ui.connected

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.gson.JsonObject
import id.panenku.app.data.remote.*
import id.panenku.app.data.rupiah
import id.panenku.app.ui.components.*

@Composable internal fun ConnectedOrders(s: ConnectedState, vm: ConnectedViewModel, open: (String) -> Unit) {
    Page("Pesanan") {
        item { SecondaryButton("Muat ulang pesanan", { vm.orders() }) }
        if (s.orders.isEmpty() && !s.busy) item { Text("Belum ada pesanan.") }
        items(s.orders, key = { it.text("orderId") }) { order -> Panel(order.text("storeName").ifBlank { order.text("buyerName") }) {
            Text("#${order.text("orderId")}")
            Text(order.text("orderStatus").replace('_', ' '))
            Text(rupiah(order.long("totalPayment")))
            SecondaryButton("Lihat pesanan", { open(order.text("orderId")) })
        } }
        if (s.more) item { SecondaryButton("Muat pesanan berikutnya", { vm.orders(s.page + 1) }) }
    }
}
@Composable internal fun ConnectedOrder(s: ConnectedState, vm: ConnectedViewModel, buyer: Boolean, back: () -> Unit, refund: () -> Unit) {
    val order = s.detail
    if (order == null) { Page("Pesanan", onBack = back) { item { Text("Memuat pesanan…") } }; return }
    key(order.text("orderId")) {
        val id = order.text("orderId")
        val status = order.text("orderStatus")
        var reason by rememberSaveable { mutableStateOf("") }
        var date by rememberSaveable { mutableStateOf("") }
        var slot by rememberSaveable { mutableStateOf("") }
        var image by rememberSaveable { mutableStateOf<String?>(null) }
        var receiveConfirmation by remember { mutableStateOf(false) }
        Page("Detail pesanan", "#$id", back) {
            item { SecondaryButton("Perbarui status", { vm.order(id) }) }
            item { Panel("Status pesanan") {
                Text(status.replace('_', ' ')); Text("Pembayaran: ${order.text("paymentStatus").replace('_', ' ')}")
                Text("Jadwal: ${order.obj("effectiveSchedule").text("scheduledDate").ifBlank { order.text("scheduledDate") }} · ${order.obj("effectiveSchedule").text("scheduledTimeSlot").ifBlank { order.text("scheduledTimeSlot") }}")
                if (order.text("deliveryAddressSnapshot").isNotBlank()) Text(order.text("deliveryAddressSnapshot"))
                if (order.text("pickupLocationSnapshot").isNotBlank()) Text("Pengambilan: ${order.text("pickupLocationSnapshot")}")
                if (status in listOf("menunggu_petani", "menunggu_pembayaran")) Text("Batas waktu: ${order.text("expiresAt")}")
                order.list("items").forEach { Text("${it.text("productNameSnapshot")} · ${it.text("quantityKg")} kg · ${rupiah(it.long("itemSubtotal"))}") }
                Text("Total ${rupiah(order.long("totalPayment"))}")
            } }
            item { Field("Catatan / alasan tindakan", reason, { reason = it }, multiline = true) }
            if (buyer) {
                if (status == "menunggu_petani") item { SecondaryButton("Batalkan pesanan", { vm.cancel(id, reason) }) }
                if (status == "menunggu_penerimaan") item { PrimaryButton("Konfirmasi barang diterima", { receiveConfirmation = true }) }
                if (status in listOf("selesai", "gagal_dipenuhi")) item { SecondaryButton("Ajukan refund", refund) }
                if (status in listOf("menunggu_pembayaran", "verifikasi_pembayaran")) {
                    item { Panel("Pembayaran langsung ke petani") {
                        Text("Pilih tujuan pembayaran dan transfer sesuai total. Bukti akan diperiksa petani.")
                        SecondaryButton("Lihat pilihan pembayaran", { vm.payments(id) })
                        s.options.forEach { option -> SecondaryButton("${option.text("bankName").ifBlank { "QRIS" }} · ${option.text("accountHolderName")}", { vm.choosePayment(id, option.text("id")) }) }
                        s.destination?.let { target ->
                            Text("Penerima: ${target.text("accountHolderName")}")
                            if (target.text("accountNumber").isNotBlank()) Text("${target.text("bankName")} · ${target.text("accountNumber")}")
                            if (target.flag("hasQrisImage")) SecondaryButton("Lihat QRIS", { vm.image("buyer/orders/$id/payment-destination/qris") })
                            MediaPicker("Pilih bukti pembayaran (JPEG/PNG, maks. 5 MB)", image, { image = it })
                            PrimaryButton("Kirim bukti pembayaran", { image?.let { vm.proof(id, it) } }, image != null && status == "menunggu_pembayaran")
                        }
                    } }
                }
                val proposal = order.obj("pendingScheduleChange")
                if (proposal.text("id").isNotBlank()) item { Panel("Usulan perubahan jadwal") {
                    Text("${proposal.text("proposedDate")} · ${proposal.text("proposedSlot")}")
                    Text(proposal.text("reason"))
                    PrimaryButton("Setujui jadwal", { vm.decideSchedule(id, true, reason) })
                    SecondaryButton("Tolak usulan", { vm.decideSchedule(id, false, reason) })
                } }
            } else {
                if (status == "menunggu_petani") item { Panel("Konfirmasi pesanan") {
                    PrimaryButton("Terima pesanan", { vm.respond(id, true, reason) })
                    SecondaryButton("Tolak pesanan", { vm.respond(id, false, reason) }, enabled = reason.isNotBlank())
                } }
                if (status == "verifikasi_pembayaran") item { Panel("Verifikasi pembayaran") {
                    SecondaryButton("Ambil bukti pembayaran", { vm.payments(id) })
                    s.destination?.let { target -> Text("Tujuan: ${target.text("bankName")} ${target.text("accountNumber")} · ${target.text("accountHolderName")}") }
                    s.proofs.forEach { proof ->
                        Text("Bukti ${proof.text("submittedAt")} · ${proof.text("status")}")
                        SecondaryButton("Lihat gambar bukti", { vm.image("petani/orders/$id/payment-proofs/${proof.text("id")}/image") })
                        if (proof.text("status") == "menunggu_verifikasi") {
                            PrimaryButton("Terima bukti", { vm.verify(id, proof.text("id"), true, reason) })
                            SecondaryButton("Tolak bukti", { vm.verify(id, proof.text("id"), false, reason) }, enabled = reason.isNotBlank())
                        }
                    }
                } }
                if (status == "diproses") item { Panel("Pemenuhan pesanan") {
                    PrimaryButton(if (order.text("fulfillmentMethod") == "pickup") "Siap diambil" else "Mulai pengantaran", {
                        vm.fulfill(id, if (order.text("fulfillmentMethod") == "pickup") "siap_diambil" else "diantar", reason)
                    })
                    SecondaryButton("Tidak dapat memenuhi pesanan", { vm.fulfill(id, "gagal_dipenuhi", reason) }, enabled = reason.isNotBlank())
                } }
                if (status in listOf("siap_diambil", "diantar")) item { PrimaryButton("Minta konfirmasi penerimaan", { vm.fulfill(id, "menunggu_penerimaan", reason) }) }
                // Backend currently permits schedule proposals before payment proofs exist.
                if (status in listOf("menunggu_petani", "menunggu_pembayaran")) item { Panel("Usulkan perubahan jadwal") {
                    Field("Tanggal (YYYY-MM-DD)", date, { date = it })
                    Field("Slot waktu sesuai layanan toko", slot, { slot = it }, "09:00-12:00")
                    PrimaryButton("Kirim usulan", { vm.schedule(id, date, slot, reason) }, date.isNotBlank() && slot.isNotBlank() && reason.isNotBlank())
                } }
            }
            if (order.list("history").isNotEmpty()) item { Panel("Riwayat pesanan") {
                order.list("history").forEach { Text("${it.text("changedAt")} · ${it.text("toStatus").replace('_', ' ')}\n${it.text("note")}") }
            } }
        }
        if (receiveConfirmation) AlertDialog(onDismissRequest = { receiveConfirmation = false },
            title = { Text("Barang sudah diterima?") }, text = { Text("Konfirmasi ini menyelesaikan pesanan dan memulai batas waktu pengajuan refund.") },
            confirmButton = { TextButton(onClick = { receiveConfirmation = false; vm.receive(id) }) { Text("Sudah diterima") } },
            dismissButton = { TextButton(onClick = { receiveConfirmation = false }) { Text("Kembali") } })
    }
}
@Composable internal fun ConnectedRefundForm(orderId: String, vm: ConnectedViewModel, back: () -> Unit, done: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    var bank by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    Page("Ajukan refund", "#$orderId", back) {
        item { Panel {
            Text("Kelayakan dan nominal refund diperiksa server berdasarkan pesanan.")
            Field("Alasan pengajuan", reason, { reason = it }, multiline = true)
            Field("Bank tujuan", bank, { bank = it })
            Field("Nomor rekening", account, { account = it })
            Field("Nama pemilik rekening", name, { name = it })
            MediaPicker("Foto bukti (JPEG/PNG, maks. 5 MB)", image, { image = it })
            PrimaryButton("Kirim pengajuan", { image?.let { vm.refund(orderId, reason, bank, account, name, it, done) } },
                reason.isNotBlank() && bank.isNotBlank() && account.isNotBlank() && name.isNotBlank() && image != null)
        } }
    }
}
@Composable internal fun ConnectedRefunds(s: ConnectedState, vm: ConnectedViewModel, buyer: Boolean, admin: Boolean) {
    val prefix = if (buyer) "buyer" else if (admin) "admin" else "petani"
    Page("Refund") {
        item { SecondaryButton("Perbarui refund", { vm.refunds() }) }
        if (s.refunds.isEmpty() && !s.busy) item { Text("Belum ada pengajuan refund.") }
        items(s.refunds, key = { it.text("id") }) { refund ->
            val id = refund.text("id")
            var reason by rememberSaveable(id) { mutableStateOf("") }
            var image by rememberSaveable(id) { mutableStateOf<String?>(null) }
            var confirm by remember { mutableStateOf(false) }
            Panel("#${refund.text("orderId")}") {
                Text("${refund.text("status").replace('_', ' ')} · ${rupiah(refund.long("refundAmount"))}")
                Text(refund.text("reason"))
                Text("Tujuan: ${refund.text("destinationProvider")} · ${refund.text("destinationAccount")} · ${refund.text("destinationName")}")
                if (refund.flag("hasEvidence")) SecondaryButton("Lihat bukti pengajuan", { vm.image("$prefix/refunds/$id/evidence") })
                if (refund.flag("hasTransferProof")) SecondaryButton("Lihat bukti transfer balik", { vm.image("$prefix/refunds/$id/transfer-proof") })
                Field("Alasan tanggapan / bantuan", reason, { reason = it }, multiline = true)
                if (buyer) {
                    if (refund.text("status") == "menunggu_konfirmasi_dana") PrimaryButton("Dana sudah diterima", { confirm = true })
                    if (refund.text("status") in listOf("diajukan", "ditolak", "disetujui", "menunggu_konfirmasi_dana")) SecondaryButton("Ajukan bantuan admin", { vm.escalate(id, reason) }, enabled = reason.isNotBlank())
                } else if (!admin) {
                    if (refund.text("status") == "diajukan") {
                        PrimaryButton("Setujui refund", { vm.refundDecision(id, true, reason) })
                        SecondaryButton("Tolak refund", { vm.refundDecision(id, false, reason) }, enabled = reason.isNotBlank())
                    }
                    if (refund.text("status") == "disetujui") {
                        MediaPicker("Bukti transfer balik", image, { image = it })
                        PrimaryButton("Unggah bukti transfer balik", { image?.let { vm.refundTransfer(id, it) } }, image != null)
                    }
                }
            }
            if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Dana benar-benar sudah diterima?") },
                text = { Text("Periksa rekening tujuan sebelum menutup refund.") },
                confirmButton = { TextButton(onClick = { confirm = false; vm.refundConfirm(id) }) { Text("Ya, sudah diterima") } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("Kembali") } })
        }
        if (s.more) item { SecondaryButton("Muat refund berikutnya", { vm.refunds(s.page + 1) }) }
    }
}
