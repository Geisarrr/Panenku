package id.panenku.app.features.f3_delivery_schedule

import id.panenku.app.ui.components.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.panenku.app.R
import id.panenku.app.data.*
import id.panenku.app.data.buyer.*
import id.panenku.app.ui.theme.PanenkuTheme
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID


@Composable
fun BuyerAddressScreen(data: BuyerData, onBack: () -> Unit, onSelect: (String) -> Unit,
                       onSave: (BuyerAddress, Boolean, () -> Unit) -> Unit) {
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var formVersion by rememberSaveable { mutableIntStateOf(0) }
    val existing = data.addresses.find { it.id == editId }
    val newId = rememberSaveable(formVersion) { "alamat-${UUID.randomUUID()}" }
    var label by rememberSaveable(editId, formVersion) { mutableStateOf(existing?.label.orEmpty()) }
    var name by rememberSaveable(editId, formVersion) { mutableStateOf(existing?.recipient ?: data.profile.name) }
    var phone by rememberSaveable(editId, formVersion) { mutableStateOf(existing?.phone ?: data.profile.phone) }
    var street by rememberSaveable(editId, formVersion) { mutableStateOf(existing?.street.orEmpty()) }
    var district by rememberSaveable(editId, formVersion) { mutableStateOf(existing?.district ?: "Lowokwaru") }
    var city by rememberSaveable(editId, formVersion) { mutableStateOf(existing?.city ?: "Kota Malang") }
    var note by rememberSaveable(editId, formVersion) { mutableStateOf(existing?.courierNote.orEmpty()) }
    var primary by rememberSaveable(editId, formVersion) { mutableStateOf(false) }
    val draft = BuyerAddress(editId ?: newId, label.trim(), name.trim(), phone, street.trim(), district.trim(), city.trim(), note.trim())
    val valid = label.isNotBlank() && name.isNotBlank() && phone.length in 8..15 && street.trim().length >= 10 && district.isNotBlank() && city.isNotBlank()
    Page("Alamat pengiriman", "${data.addresses.size} alamat tersimpan", onBack) {
        itemsIndexed(data.addresses, key = { _, address -> address.id }) { index, address -> Panel(address.label) {
            if (index == data.profile.selectedAddress) StatusBadge("✓ Alamat utama")
            Text(address.recipient, style = MaterialTheme.typography.titleMedium)
            Text(address.street); Text("${address.district} · ${address.city}", style = MaterialTheme.typography.bodySmall)
            Text("Kontak: ${address.phone}", style = MaterialTheme.typography.bodySmall)
            Notice(if (canDeliverTo(address)) "Masuk area Toko Sobianto" else "Di luar area antar toko ini",
                if (canDeliverTo(address)) "Layanan antar tersedia · Ongkir Rp2.500." else "Pilih ambil sendiri atau alamat di Lowokwaru.",
                if (canDeliverTo(address)) Tone.SUCCESS else Tone.WARNING)
            SecondaryButton("Edit ${address.label}", { editId = address.id; formVersion++ })
            if (index != data.profile.selectedAddress) SecondaryButton("Jadikan ${address.label} alamat utama", { onSelect(address.id) })
        } }
        item { Panel(if (existing == null) "Tambah alamat baru" else "Edit alamat · ${existing.label}") {
            Field("Label alamat", label, { label = it }, "Dapur produksi")
            Field("Nama penerima", name, { name = it })
            Field("Nomor kontak", phone, { phone = it.filter(Char::isDigit).take(15) }, keyboard = KeyboardType.Phone)
            Field("Alamat lengkap", street, { street = it }, "Nama jalan, nomor, dan petunjuk lokasi", multiline = true)
            Field("Kecamatan", district, { district = it }); Field("Kota", city, { city = it })
            Field("Catatan pengantaran", note, { note = it }, "Pagar putih, hubungi saat tiba", multiline = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(primary, { primary = it }); Text("Jadikan alamat utama", style = MaterialTheme.typography.bodyMedium) }
            Notice(if (canDeliverTo(draft)) "Alamat dapat dilayani" else "Di luar area antar",
                "Area antar saat ini Lowokwaru, Kota Malang. Alamat utama tidak berubah kecuali dipilih.", if (canDeliverTo(draft)) Tone.SUCCESS else Tone.WARNING)
            PrimaryButton(if (existing == null) "Simpan alamat baru" else "Simpan perubahan alamat", {
                onSave(draft, primary) { editId = null; formVersion++ }
            }, valid)
            if (existing != null) SecondaryButton("Batal edit", { editId = null; formVersion++ })
        } }
    }
}



@Preview(showBackground = true, widthDp = 375, heightDp = 1000)
@Composable private fun AddressPreview() { PanenkuTheme { BuyerAddressScreen(BuyerSamples.initial, {}, {}, { _, _, _ -> }) } }
