package id.panenku.app.features.f3_delivery_schedule

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
fun ScheduleScreen(order: Order, onBack: () -> Unit, onSubmit: (String, String) -> Unit) {
    var date by rememberSaveable { mutableStateOf("25 Juni 2026") }
    var slot by rememberSaveable { mutableStateOf("Pagi · 09.00–12.00 WIB") }
    var reason by rememberSaveable { mutableStateOf("") }
    Page("Usulkan jadwal baru", "#${order.id} · ${order.buyer}", onBack) {
        item { Panel("Jadwal yang masih berlaku") { StatusBadge("Diproses · Layanan antar"); Text(order.schedule); Text("Lowokwaru, Kota Malang") } }
        item { Panel("Usulan perubahan") {
            Field("Tanggal baru", date, { date = it }); Field("Slot layanan baru", slot, { slot = it })
            Field("Alasan perubahan (wajib)", reason, { reason = it }, "Jelaskan alasan perubahan", multiline = true)
            Notice("Menunggu persetujuan pembeli", "Mengirim usulan tidak mengganti jadwal saat ini. Jadwal lama tetap berlaku sampai disetujui.")
            PrimaryButton("Kirim usulan ke pembeli", { onSubmit("$date · $slot", reason) }, date.isNotBlank() && slot.isNotBlank() && reason.isNotBlank())
            SecondaryButton("Batalkan perubahan", onBack)
        } }
        item { Panel("Setelah usulan dikirim") { Text("Status usulan dapat ditampilkan dari backend pada pengembangan berikutnya."); KeyValue("Total dibayar tetap", rupiah(order.total), true) } }
    }
}
