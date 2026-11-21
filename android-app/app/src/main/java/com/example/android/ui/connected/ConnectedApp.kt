package id.panenku.app.ui.connected

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.components.*
import id.panenku.app.ui.auth.PanenkuOpeningFlow

/** Live entry point. Uses the sliced design system, never Demo*Repository. */
@Composable
fun ConnectedPanenkuApp(vm: ConnectedViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.user == null) {
        PanenkuOpeningFlow(state.busy, vm::login, vm::registerAccount,
            farmer = state.farmerOnboarding, onPrepareFarmer = vm::prepareFarmerAccount,
            onVerifyFarmer = vm::submitFarmerRegistration, onVerificationExit = vm::finishFarmerVerification)
    } else key(state.user!!.id) {
        var screen by rememberSaveable { mutableStateOf(if (state.user!!.role == "admin") "admin" else "catalog") }
        var orderId by rememberSaveable { mutableStateOf("") }
        var product by remember { mutableStateOf<JsonObject?>(null) }
        val buyer = state.user!!.role == "pembeli"
        val admin = state.user!!.role == "admin"
        val tabs = if (admin) listOf("admin" to "Verifikasi", "refunds" to "Refund", "account" to "Akun")
            else listOf("catalog" to if (buyer) "Toko" else "Stok", "orders" to "Pesanan", "refunds" to "Refund", "account" to "Akun")
        fun openOrder(id: String) { orderId = id; screen = "detail" }
        LaunchedEffect(screen) {
            when (screen) {
                "catalog" -> vm.catalog()
                "cart" -> vm.cart()
                "orders" -> vm.orders()
                "refunds" -> vm.refunds()
                "detail" -> vm.order(orderId)
                "account" -> vm.profile()
                "admin" -> vm.adminQueue()
                "product" -> vm.commodities()
                "checkout" -> vm.invalidateSchedules()
            }
        }
        BackHandler(screen !in tabs.map { it.first } && !state.busy) { screen = if (screen == "refund-form") "detail" else "catalog" }
        Scaffold(bottomBar = {
            NavigationBar {
                tabs.forEach { (route, label) ->
                    NavigationBarItem(selected = screen == route, onClick = { if (!state.busy) screen = route },
                        icon = { Icon(when (route) { "catalog" -> Icons.Outlined.Storefront; "orders" -> Icons.Outlined.ReceiptLong; "refunds" -> Icons.Outlined.AssignmentReturn; else -> Icons.Outlined.Person }, label) },
                        label = { Text(label) })
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    "catalog" -> ConnectedCatalog(state, vm, buyer, { screen = "cart" }, { product = it; screen = "product" })
                    "cart" -> ConnectedCart(state, vm, { screen = "catalog" }, { screen = "checkout" })
                    "checkout" -> ConnectedCheckout(state, vm, { screen = "cart" }) { screen = "orders" }
                    "orders" -> ConnectedOrders(state, vm, ::openOrder)
                    "detail" -> ConnectedOrder(state, vm, buyer, { screen = "orders" }, { screen = "refund-form" })
                    "refund-form" -> ConnectedRefundForm(orderId, vm, { screen = "detail" }) { screen = "refunds" }
                    "refunds" -> ConnectedRefunds(state, vm, buyer, admin)
                    "product" -> ConnectedProduct(product, state, vm) { screen = "catalog" }
                    "account" -> ConnectedAccount(state, vm)
                    "admin" -> ConnectedAdmin(state, vm)
                }
                if (state.busy) Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), modifier = Modifier.fillMaxSize().clickable { }) {
                    Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }
    if (state.error != null || state.message != null) AlertDialog(onDismissRequest = vm::dismiss,
        title = { Text(if (state.error != null) "Belum berhasil" else "Berhasil") },
        text = { Text(state.error ?: state.message.orEmpty()) }, confirmButton = { TextButton(onClick = vm::dismiss) { Text("OK") } })
    state.image?.let { bytes ->
        val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        AlertDialog(onDismissRequest = vm::dismiss, title = { Text("Bukti dari server") },
            text = { if (bitmap != null) Image(bitmap.asImageBitmap(), "Gambar bukti", Modifier.fillMaxWidth().heightIn(max = 450.dp)) else Text("Gambar tidak dapat ditampilkan.") },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text("Tutup") } })
    }
}
