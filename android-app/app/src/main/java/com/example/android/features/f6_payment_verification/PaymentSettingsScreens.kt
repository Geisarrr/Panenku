package id.panenku.app.features.f6_payment_verification

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
fun BankScreen(values: Map<String, String>, onBack: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    var bank by rememberSaveable { mutableStateOf(values["Bank"] ?: "BRI") }
    var name by rememberSaveable { mutableStateOf(values["Nama"] ?: "Darto Sobianto (contoh)") }
    var number by rememberSaveable { mutableStateOf(values["Nomor"] ?: "000000000000") }
    Page("Rekening bank petani", "Pembayaran langsung ke Toko Sobianto", onBack) {
        item { Notice("Rekening penerima pembayaran", "Pembeli mentransfer langsung ke rekening petani setelah pesanan diterima.") }
        item { Panel("Data rekening bank") {
            Field("Bank", bank, { bank = it }); Field("Nama pemilik rekening", name, { name = it })
            Field("Nomor rekening", number, { number = it.filter(Char::isDigit).take(24) }, keyboard = KeyboardType.Number)
            Text("Nama pemilik harus sesuai rekening bank. Data contoh bukan tujuan transfer aktif.", style = MaterialTheme.typography.bodySmall)
        } }
        item { Notice("Dana masuk langsung ke petani", "Panenku tidak menyimpan saldo. Periksa dana masuk dan bukti pembayaran pesanan secara manual.") }
        item { PrimaryButton("Simpan Rekening", { onSave(mapOf("Bank" to bank, "Nama" to name, "Nomor" to number)) }, bank.isNotBlank() && name.isNotBlank() && number.length >= 8) }
    }
}

@Composable
fun QrisScreen(values: Map<String, String>, onBack: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    var uri by rememberSaveable { mutableStateOf(values["URI"]) }
    Page("QRIS toko", "Pembayaran langsung ke Toko Sobianto", onBack) {
        item { Notice("QRIS toko dilampirkan", "Pratinjau awal menggunakan QRIS DEMO, bukan kode pembayaran aktif.") }
        item { Panel("Pratinjau QRIS") {
            if (uri == null) {
                Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.QrCode2, "QRIS contoh", Modifier.size(76.dp))
                    Text("QRIS DEMO"); Text("Tidak dapat dipindai", style = MaterialTheme.typography.bodySmall)
                }
            } else LocalPhoto(uri, "shallot", Modifier.fillMaxWidth().height(230.dp))
            MediaPicker("Ganti unggahan QRIS", uri, { uri = it })
        } }
        item { Notice("Periksa dana masuk secara manual", "Pembayaran QRIS langsung ke petani, di luar Panenku. Tinjau bukti pembayaran pembeli di pesanan.") }
        item { PrimaryButton("Simpan QRIS", { onSave(mapOf("URI" to uri.orEmpty())) }, uri != null) }
        item { Text("Hanya menyimpan pengaturan QRIS toko di memori demo.", style = MaterialTheme.typography.bodySmall) }
    }
}
