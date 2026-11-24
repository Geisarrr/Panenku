package id.panenku.app.features.f3_delivery_schedule

import id.panenku.app.features.f5_order_status.*
import id.panenku.app.features.f6_payment_verification.*
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
fun BuyerScheduleResponseScreen(order: BuyerOrder, onBack: () -> Unit, onRespond: (Boolean) -> Unit) {
    val proposal = order.proposal
    Page("Perubahan jadwal", "#${order.id} · ${order.storeName}", onBack) {
        if (proposal == null) item { EmptyState("Tidak ada usulan jadwal") }
        else {
            item { StatusBadge(proposal.status.label, if (proposal.status == ProposalStatus.PENDING) Tone.WARNING else Tone.SUCCESS) }
            item { Panel(if (proposal.status == ProposalStatus.ACCEPTED) "Jadwal sebelumnya" else "Jadwal saat ini") {
                Text("${order.fulfillment.label} ke ${order.address.recipient}", style = MaterialTheme.typography.titleMedium)
                Text(order.address.street); Text(proposal.originalSchedule ?: order.order.schedule, style = MaterialTheme.typography.labelLarge)
            } }
            item { Panel("Usulan jadwal baru") {
                StatusBadge("Diajukan ${buyerDate(proposal.submittedAt)}")
                KeyValue("Tanggal & slot usulan", scheduleLabel(proposal.date, proposal.slot), true)
                Text("Alasan ${order.farmerName}", style = MaterialTheme.typography.titleMedium)
                Text(proposal.reason)
                Notice("Jadwal tidak berubah otomatis", "Jika setuju, jadwal baru berlaku. Jika ditolak, jadwal lama tetap berlaku.")
            } }
            item { PrimaryButton("Setujui jadwal baru", { onRespond(true) }, proposal.status == ProposalStatus.PENDING) }
            item { SecondaryButton("Tolak perubahan jadwal", { onRespond(false) }, enabled = proposal.status == ProposalStatus.PENDING) }
            item { Panel("Ringkasan tetap sama") {
                KeyValue("Isi pesanan", order.order.lines.joinToString(" + ") { "${it.name} ${it.quantity} kg" })
                KeyValue("Total dibayar", rupiah(order.total), true); Text("Alamat, jumlah, dan harga pesanan tidak berubah.", style = MaterialTheme.typography.bodySmall)
                if (proposal.status != ProposalStatus.PENDING) KeyValue("Jadwal berlaku", order.order.schedule, true)
            } }
        }
    }
}


@Preview(showBackground = true, widthDp = 375, heightDp = 1000)
@Composable private fun SchedulePreview() { PanenkuTheme { BuyerScheduleResponseScreen(BuyerSamples.initial.orders.first { it.id == "PES-2026-006" }, {}, {}) } }
