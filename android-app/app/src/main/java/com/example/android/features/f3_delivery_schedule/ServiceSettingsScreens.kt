package id.panenku.app.features.f3_delivery_schedule

import id.panenku.app.ui.components.*

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp


@Composable
fun ServiceScreen(values: Map<String, String>, onBack: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    var location by rememberSaveable { mutableStateOf(values["Lokasi"] ?: "Kebun Darto") }
    var address by rememberSaveable { mutableStateOf(values["Alamat"] ?: "Lowokwaru, Kota Malang, Jawa Timur") }
    var hint by rememberSaveable { mutableStateOf(values["Petunjuk"] ?: "Gerbang kebun, sebelah pos warga") }
    var area by rememberSaveable { mutableStateOf(values["Area"] ?: "Lowokwaru, Kota Malang") }
    var fee by rememberSaveable { mutableStateOf(values["Ongkir"] ?: "2500") }
    var pickup by rememberSaveable { mutableStateOf(values["Ambil"]?.toBoolean() ?: true) }
    var delivery by rememberSaveable { mutableStateOf(values["Antar"]?.toBoolean() ?: true) }
    var day by rememberSaveable { mutableStateOf(values["Hari"] ?: "Rab") }
    var hours by rememberSaveable { mutableStateOf(values["Jam"] ?: "08.00–16.00 WIB") }
    var slot by rememberSaveable { mutableStateOf(values["Slot"] ?: "Pagi · 09.00–12.00 WIB") }
    var pickupSlot by rememberSaveable { mutableStateOf(values["Slot ambil"] ?: "08.00–12.00 / 13.00–16.00") }
    Page("Layanan & area antar", "Toko Sobianto · Pengaturan operasional", onBack) {
        item { Panel("Lokasi pengambilan") {
            Field("Nama lokasi", location, { location = it }); Field("Alamat ambil", address, { address = it }, multiline = true); Field("Petunjuk lokasi", hint, { hint = it })
        } }
        item { Panel("Layanan tersedia") {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Ambil sendiri · Rp0", Modifier.weight(1f)); Switch(pickup, { pickup = it }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Antar oleh petani", Modifier.weight(1f)); Switch(delivery, { delivery = it }) }
            Field("Kecamatan yang dilayani", area, { area = it })
            Field("Ongkir per pesanan (Rp)", fee, { fee = it.filter(Char::isDigit).take(8) }, keyboard = KeyboardType.Number)
        } }
        item { Panel("Hari & jam layanan") {
            FilterChips(listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab"), day, { day = it })
            Field("Jam buka – tutup", hours, { hours = it }); Field("Slot antar", slot, { slot = it }); Field("Slot ambil sendiri", pickupSlot, { pickupSlot = it })
            Text("Demo menyimpan satu konfigurasi hari yang dipilih. Minggu tutup.", style = MaterialTheme.typography.bodySmall)
        } }
        item { Notice("Jadwal checkout mengikuti layanan", "Perubahan berlaku untuk pesanan baru. Jadwal pesanan yang sudah dibuat tetap tersimpan.") }
        item { PrimaryButton("Simpan pengaturan layanan", { onSave(mapOf("Lokasi" to location, "Alamat" to address, "Petunjuk" to hint, "Area" to area,
            "Ongkir" to fee, "Ambil" to pickup.toString(), "Antar" to delivery.toString(), "Hari" to day, "Jam" to hours, "Slot" to slot, "Slot ambil" to pickupSlot)) },
            location.isNotBlank() && address.isNotBlank() && (pickup || delivery) && fee.toLongOrNull() != null && hours.isNotBlank() && (!delivery || area.isNotBlank())) }
    }
}
