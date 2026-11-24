package id.panenku.app.features.f4_checkout

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
fun BuyerCartDialog(data: BuyerData, onQuantity: (String, Int) -> Unit, onCheckout: (() -> Unit)? = null, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Keranjang demo") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (data.cart.isEmpty()) Text("Keranjang masih kosong. Tambahkan produk dari profil toko.")
            data.cart.forEach { (id, quantity) ->
                val product = data.products.firstOrNull { it.id == id }
                if (product != null) {
                    Text(product.name, style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ onQuantity(id, quantity - 1) }) { Icon(Icons.Outlined.Remove, "Kurangi ${product.name}") }
                        Text("$quantity kg", Modifier.weight(1f))
                        IconButton({ onQuantity(id, quantity + 1) }, enabled = quantity < product.available) { Icon(Icons.Outlined.Add, "Tambah ${product.name}") }
                    }
                }
            }
            if (data.cart.isNotEmpty()) KeyValue("Subtotal harga saat ini", rupiah(data.cart.entries.sumOf { (id, quantity) -> (data.products.find { it.id == id }?.price ?: 0) * quantity }), true)
            Text("Subtotal belum termasuk ongkir toko.", style = MaterialTheme.typography.bodySmall)
            if (onCheckout != null && data.cart.isNotEmpty()) PrimaryButton("Lanjut Checkout", onCheckout)
        }
    }, confirmButton = { TextButton(onClose) { Text("Tutup") } })
}
