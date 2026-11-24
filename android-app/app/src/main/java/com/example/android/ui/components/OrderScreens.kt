package id.panenku.app.ui.components

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
fun OrderSummary(order: Order) {
    Panel("Rincian pesanan tersimpan") {
        order.lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProductImage(if (line.productId == "sawi") "cabbage" else "tomato", Modifier.size(45.dp))
                Column(Modifier.weight(1f)) { Text(line.name, style = MaterialTheme.typography.labelLarge); Text("${line.quantity} kg × ${rupiah(line.price)}/kg", style = MaterialTheme.typography.bodySmall) }
                Text(rupiah(line.total), style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider()
        KeyValue("Subtotal", rupiah(order.subtotal)); KeyValue("Ongkir petani", rupiah(order.deliveryFee)); KeyValue("Total", rupiah(order.total), true)
        Text("Harga ini mengikuti rincian saat pesanan dibuat.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun EvidenceCard(title: String = "Bukti pembeli · Percobaan pertama", amount: Long = 202500) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Panel(title) {
        Notice("BUKTI DEMO · bukan transaksi nyata", "Nominal contoh ${rupiah(amount)}. Pengirim Rina Catering, penerima Darto Sobianto. Data rekening disamarkan.", Tone.INFO)
        SecondaryButton("Buka bukti demo lengkap", { expanded = true })
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text("Bukti pembayaran contoh") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ini kartu bukti dummy, bukan bukti bank atau QRIS yang dapat digunakan.")
            KeyValue("Nominal", rupiah(amount), true); KeyValue("Bank", "BRI · •••• 0000"); KeyValue("Pengirim", "Rina Catering")
            Text("Pada aplikasi lengkap, tampilkan foto bukti dari backend dan cocokkan mutasi rekening secara manual.")
        } }, confirmButton = { TextButton({ expanded = false }) { Text("Tutup") } })
}
