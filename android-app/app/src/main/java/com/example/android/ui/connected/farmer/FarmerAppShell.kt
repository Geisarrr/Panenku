package id.panenku.app.ui.connected.farmer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.*
import kotlinx.coroutines.flow.first

/** Active seller entry point. All mutations retain the existing server endpoints. */
@Composable internal fun FarmerConnectedApp(s: ConnectedState, vm: ConnectedViewModel) {
    var route by rememberSaveable(s.user?.id) { mutableStateOf("home") }
    var orderId by rememberSaveable(s.user?.id) { mutableStateOf("") }
    var productId by rememberSaveable(s.user?.id) { mutableStateOf<String?>(null) }
    var refundId by rememberSaveable(s.user?.id) { mutableStateOf("") }
    var orderFilter by rememberSaveable { mutableStateOf("semua") }
    fun openOrder(id: String) { orderId = id; route = "detail" }
    fun openProduct(id: String?) { productId = id; route = "product" }
    LaunchedEffect(route, orderId, refundId) {
        vm.state.first { !it.busy }
        when (route) {
            "home", "stock", "orders", "product" -> vm.farmerWorkspace()
            "detail" -> vm.farmerOrder(orderId)
            "schedule" -> if (s.detail?.text("orderId") != orderId) vm.farmerOrder(orderId)
            "profile", "services" -> vm.profile()
            "verification" -> vm.farmerVerification()
            "notifications" -> vm.notifications()
            "refund-review" -> vm.farmerRefundDetail(refundId)
            "refund-transfer" -> if (s.farmerRefund?.text("id") != refundId) vm.farmerRefundDetail(refundId)
            "refunds" -> vm.refunds()
        }
    }
    BackHandler(route != "home" && !s.busy) {
        route = when (route) { "schedule" -> "detail"; "detail" -> "orders"; "product" -> "stock"; "refunds", "services", "bank", "qris", "verification", "notifications" -> "profile"; "refund-review" -> "refunds"; "refund-transfer" -> "refund-review"; else -> "home" }
    }
    FarmerTheme {
        val showNav = route in listOf("home", "stock", "orders", "profile", "refunds")
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (route) {
                        "home" -> FarmerHomeScreen(s, { route = "stock" }, { openProduct(it.text("id")) }, { orderFilter = it; route = "orders" }, { route = "profile" })
                        "stock" -> FarmerStockScreen(s, { route = "home" }, { openProduct(null) }, { openProduct(it.text("id")) })
                        "product" -> key(productId) {
                            FarmerProductScreen(s.products.find { it.text("id") == productId }, s, { route = "stock" }) { id, body, photo, assigned, done ->
                                vm.saveFarmerProduct(id, body, photo, assigned, done)
                            }
                        }
                        "orders" -> FarmerOrdersScreen(s, orderFilter, ::openOrder)
                        "detail", "schedule" -> {
                            val order = s.detail?.takeIf { it.text("orderId") == orderId }
                            if (order == null) FPage("Detail pesanan", "#${shortId(orderId)}", { route = "orders" }) {
                                FNotice(if (s.busy) "Memuat pesanan…" else "Pesanan belum dapat ditampilkan", "Periksa koneksi lalu muat ulang rincian pesanan.")
                                if (!s.busy) FButton("Muat ulang", { vm.farmerOrder(orderId) })
                            } else if (route == "schedule") FarmerScheduleScreen(s, order, { route = "detail" }) { date, slot, reason ->
                                vm.schedule(orderId, date, slot, reason)
                            } else when (order.text("orderStatus")) {
                                "verifikasi_pembayaran" -> FarmerPaymentScreen(s, order, { route = "orders" },
                                    { vm.image("petani/orders/$orderId/payment-proofs/$it/image") },
                                    { id, accepted, reason -> vm.verify(orderId, id, accepted, reason) }, { vm.payments(orderId) })
                                "diproses", "diantar", "siap_diambil", "menunggu_penerimaan" -> FarmerProcessingScreen(s, order,
                                    { route = "orders" }, { status, note -> vm.fulfill(orderId, status, note) }, { route = "schedule" })
                                else -> FarmerPendingScreen(s, order, { route = "orders" }, { accepted, reason -> vm.respond(orderId, accepted, reason) }, { route = "schedule" })
                            }
                        }
                        "profile" -> FarmerProfileScreen(s, { route = "home" }, { route = it }, vm::saveFarmerIdentity, vm::logout)
                        "services" -> FarmerServicesScreen(s, { route = "profile" }, vm::saveProfile)
                        "bank" -> FarmerBankScreen(s, { route = "profile" }, vm::bankDetail) { id, bank, account, holder -> vm.saveBank(id, bank, account, holder) { route = "profile" } }
                        "qris" -> FarmerQrisScreen(s, { route = "profile" }, vm::farmerQris) { id, holder, uri -> vm.saveQris(id, holder, uri) { route = "profile" } }
                        "notifications" -> ConnectedNotifications(s, vm) { route = "profile" }
                        "verification" -> FarmerVerificationScreen(s, { route = "profile" }, { vm.image("petani/ktp/document") }, vm::farmerResubmitKtp)
                        "refunds" -> FarmerRefundListScreen(s, { route = "profile" }, { refundId = it; route = "refund-review" }, { vm.refunds(s.page + 1) })
                        "refund-review", "refund-transfer" -> {
                            val refund = s.farmerRefund?.takeIf { it.text("id") == refundId }
                            if (refund == null) FSettingsPage("Pengajuan refund", "#${shortId(refundId)}", { route = "refunds" }) {
                                FSettingsNotice("Memuat rincian pengajuan", "Rincian refund mengikuti data transaksi yang tersimpan.")
                                if (!s.busy) FButton("Muat ulang", { vm.farmerRefundDetail(refundId) })
                            } else if (route == "refund-review") FarmerRefundReviewScreen(s, refund, { route = "refunds" },
                                { vm.image("petani/refunds/$refundId/evidence") }, { accepted, reason -> vm.farmerRefundDecision(refundId, accepted, reason) { if(accepted) route = "refund-transfer" } }, { route = "refund-transfer" })
                            else FarmerRefundTransferScreen(s, refund, { route = "refund-review" }, { vm.image("petani/refunds/$refundId/transfer-proof") }, { vm.farmerRefundTransfer(refundId,it) })
                        }
                    }
                }
                if (showNav) FarmerNavigation(route, { if (!s.busy) route = it }, { if (!s.busy) openProduct(null) })
            }
            if (s.busy) Surface(color = FPaper.copy(alpha = 0.7f), modifier = Modifier.fillMaxSize().clickable { }) {
                Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FGreen) }
            }
        }
    }
}

@Composable internal fun FarmerNavigation(selected: String,
    navigate: (String) -> Unit, create: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("farmer-navbar").background(Color.White).padding(horizontal = 32.dp, vertical = 12.dp).heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        val entries = listOf(Triple("home", "Beranda", R.drawable.farmer_home), Triple("stock", "Stok", R.drawable.farmer_stock),
            Triple("orders", "Pesanan", R.drawable.farmer_orders), Triple("profile", "Profil", R.drawable.farmer_profile))
        entries.forEachIndexed { i, (route, label, icon) ->
            if (i == 2) Box(Modifier.size(56.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF6AC662), Color(0xFF36912E))))
                .clickable(role = Role.Button, onClick = create), contentAlignment = Alignment.Center) { FIcon(R.drawable.farmer_plus, 24.dp, "Buat produk baru") }
            val active = route == selected || (route == "profile" && selected == "refunds")
            Column(Modifier.width(40.dp).clickable(role = Role.Tab) { navigate(route) }, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FIcon(icon, 24.dp, tint = if (active) Color(0xFF36912E) else Color(0xFFA3A3A3))
                FText(label, 12f, modifier = Modifier.requiredWidth(60.dp), maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.Center, color = if (active) Color(0xFF36912E) else Color(0xFFA3A3A3), line = 16f)
            }
        }
    }
}
