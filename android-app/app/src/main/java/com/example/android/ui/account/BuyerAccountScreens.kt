package id.panenku.app.ui.account

import id.panenku.app.ui.components.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.panenku.app.data.*
import id.panenku.app.data.buyer.*
import id.panenku.app.ui.theme.PanenkuTheme
import java.util.Locale


@Composable
fun BuyerAccountScreen(profile: BuyerProfile, onSave: (BuyerProfile) -> Unit, onOrders: () -> Unit,
                       onRefund: () -> Unit, onLogout: () -> Unit, onInfo: (String) -> Unit, addresses: List<BuyerAddress> = initialBuyerAddresses(), onAddresses: (() -> Unit)? = null) {
    var name by rememberSaveable { mutableStateOf(profile.name) }
    var email by rememberSaveable { mutableStateOf(profile.email) }
    var phone by rememberSaveable { mutableStateOf(profile.phone) }
    var addressDialog by rememberSaveable { mutableStateOf(false) }
    var notificationDialog by rememberSaveable { mutableStateOf(false) }
    val valid = name.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() && phone.length in 8..15
    Page("Akun saya", "Atur identitas dan kebutuhan belanjamu") {
        item { Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Text(profile.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }, Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                }
                Column { Text(profile.name, style = MaterialTheme.typography.titleMedium); StatusBadge("Akun pembeli") }
            }
            Field("Nama pembeli / usaha", name, { name = it })
            Field("Email", email, { email = it }, keyboard = KeyboardType.Email)
            Field("Nomor telepon", phone, { phone = it.filter(Char::isDigit).take(15) }, keyboard = KeyboardType.Phone)
            Text("Kontak contoh / dummy pada data awal.", style = MaterialTheme.typography.bodySmall)
            PrimaryButton("Simpan profil", { onSave(profile.copy(name = name.trim(), email = email.trim(), phone = phone)) }, valid)
        } }
        item { Panel("Belanja & transaksi") {
            MenuRow("Alamat tersimpan", "${addresses.size} alamat · ${addresses.getOrNull(profile.selectedAddress)?.label.orEmpty()} terpilih", Icons.Outlined.LocationOn) { if (onAddresses != null) onAddresses() else addressDialog = true }
            MenuRow("Pesanan saya", "Pantau jadwal dan penerimaan", Icons.Outlined.Receipt) { onOrders() }
            MenuRow("Refund & bantuan", "Lihat pengajuan dan tiket eskalasi", Icons.AutoMirrored.Outlined.HelpOutline) { onRefund() }
        } }
        item { Panel("Preferensi & keamanan") {
            MenuRow("Notifikasi transaksi", if (profile.notifications) "Aktif · Pembayaran, pesanan, refund" else "Nonaktif", Icons.Outlined.NotificationsNone) { notificationDialog = true }
            MenuRow("Kata sandi & privasi", "Perbarui akses dan pengaturan data", Icons.Outlined.Settings) { onInfo("Penggantian kata sandi memerlukan layanan akun. Preferensi demo disimpan selama aplikasi terbuka.") }
            MenuRow("Pusat bantuan", "Panduan belanja dan hubungi CS", Icons.AutoMirrored.Outlined.HelpOutline) { onRefund() }
        } }
        item { SecondaryButton("Keluar dari akun", onLogout) }
        item { Text("Panenku · Belanja hasil panen langsung dari petani.", style = MaterialTheme.typography.bodySmall) }
    }
    if (addressDialog) AlertDialog(onDismissRequest = { addressDialog = false }, title = { Text("Alamat tersimpan") },
        text = { Column { addresses.forEachIndexed { index, address ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(profile.selectedAddress == index, { onSave(profile.copy(selectedAddress = index)) })
                Column { Text(address.label, style = MaterialTheme.typography.labelLarge); Text(address.street, style = MaterialTheme.typography.bodySmall) }
            }
        } } }, confirmButton = { TextButton({ addressDialog = false }) { Text("Selesai") } })
    if (notificationDialog) AlertDialog(onDismissRequest = { notificationDialog = false }, title = { Text("Notifikasi transaksi") },
        text = { Row(verticalAlignment = Alignment.CenterVertically) { Text("Pembayaran, pesanan, refund", Modifier.weight(1f)); Switch(profile.notifications, { onSave(profile.copy(notifications = it)) }) } },
        confirmButton = { TextButton({ notificationDialog = false }) { Text("Selesai") } })
}



@Preview(showBackground = true, widthDp = 375, heightDp = 1000)
@Composable private fun BuyerAccountPreview() { PanenkuTheme { BuyerAccountScreen(BuyerProfile(), {}, {}, {}, {}, {}) } }
