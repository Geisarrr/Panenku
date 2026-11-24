package id.panenku.app.navigation

import id.panenku.app.features.f1_catalog.*
import id.panenku.app.features.f2_stock_reservation.*
import id.panenku.app.features.f3_delivery_schedule.*
import id.panenku.app.features.f4_checkout.*
import id.panenku.app.features.f5_order_status.*
import id.panenku.app.features.f6_payment_verification.*
import id.panenku.app.features.f7_refund.*
import id.panenku.app.ui.account.*
import id.panenku.app.ui.dashboard.*
import id.panenku.app.ui.components.*

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import id.panenku.app.data.*
import id.panenku.app.data.buyer.*
import id.panenku.app.ui.buyer.*
import androidx.compose.runtime.saveable.rememberSaveable
import id.panenku.app.ui.PanenkuViewModel
import id.panenku.app.ui.admin.*
import id.panenku.app.ui.auth.*
import kotlinx.coroutines.delay

private data class MainTab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    MainTab("home", "Beranda", Icons.Outlined.Home), MainTab("products", "Stok", Icons.Outlined.Inventory2),
    MainTab("orders", "Pesanan", Icons.AutoMirrored.Outlined.ReceiptLong), MainTab("profile", "Profil", Icons.Outlined.PersonOutline)
)
private val buyerTabs = listOf(
    MainTab("buyer-store", "Toko", Icons.Outlined.Storefront),
    MainTab("buyer-orders", "Pesanan", Icons.AutoMirrored.Outlined.ReceiptLong),
    MainTab("buyer-account", "Akun", Icons.Outlined.PersonOutline)
)
@Composable
fun PanenkuApp() {
    val repository = remember { DemoPanenkuRepository() }
    val vm: PanenkuViewModel = viewModel(factory = PanenkuViewModel.factory(repository))
    val state by vm.state.collectAsStateWithLifecycle()
    val buyerRepository = remember { DemoBuyerRepository() }
    val buyerVm: BuyerViewModel = viewModel(factory = BuyerViewModel.factory(buyerRepository))
    val buyerData by buyerVm.data.collectAsStateWithLifecycle()
    var cartOpen by rememberSaveable { mutableStateOf(false) }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    var info by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm) { vm.events.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(buyerVm) { buyerVm.events.collect { snackbar.showSnackbar(it) } }
    fun go(destination: String) { nav.navigate(destination) { launchSingleTop = true } }
    fun back() { nav.popBackStack() }
    fun logout() { nav.navigate("role") { popUpTo(nav.graph.id) { inclusive = true }; launchSingleTop = true } }
    fun login(role: Role) { nav.navigate(if (role == Role.ADMIN) "admin" else if (role == Role.FARMER) "home" else "buyer-orders") {
        popUpTo("role") { inclusive = true }; launchSingleTop = true
    } }
    fun tab(destination: String) { nav.navigate(destination) {
        popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true
    } }
    fun buyerTab(destination: String) { nav.navigate(destination) {
        popUpTo("buyer-orders") { saveState = true }; launchSingleTop = true; restoreState = true
    } }
    fun openBuyerOrder(orderId: String) {
        val order = buyerData.orders.find { it.id == orderId }
        go(when (order?.order?.status) {
            OrderStatus.PAYMENT, OrderStatus.REVIEW -> "buyer-payment/$orderId"
            OrderStatus.PROCESSING, OrderStatus.DELIVERY -> "buyer-tracking/$orderId"
            else -> "buyer-order/$orderId"
        })
    }
    fun refundDestination(orderId: String): String = if (buyerData.refundFor(orderId) != null) "buyer-refund/$orderId" else "buyer-refund-form/$orderId"
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tabs.any { it.route == route }) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.take(2).forEach { t -> NavigationBarItem(route == t.route, { tab(t.route) }, icon = { Icon(t.icon, t.label) }, label = { Text(t.label) }) }
                FloatingActionButton(onClick = { go("product/new") }, shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.padding(horizontal = 8.dp), containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Outlined.Add, "Buat produk baru")
                }
                tabs.drop(2).forEach { t -> NavigationBarItem(route == t.route, { tab(t.route) }, icon = { Icon(t.icon, t.label) }, label = { Text(t.label) }) }
            }
            if (buyerTabs.any { it.route == route }) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                buyerTabs.take(1).forEach { t -> NavigationBarItem(route == t.route, { buyerTab(t.route) }, icon = { Icon(t.icon, t.label) }, label = { Text(t.label) }) }
                NavigationBarItem(false, { cartOpen = true }, icon = {
                    BadgedBox(badge = { if (buyerData.cart.isNotEmpty()) Badge { Text(buyerData.cart.values.sum().toString()) } }) { Icon(Icons.Outlined.ShoppingCart, "Keranjang") }
                }, label = { Text("Keranjang") })
                buyerTabs.drop(1).forEach { t -> NavigationBarItem(route == t.route, { buyerTab(t.route) }, icon = { Icon(t.icon, t.label) }, label = { Text(t.label) }) }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) LoadingState()
            else if (state.error != null) ErrorState(state.error!!, { vm.reload() })
            else NavHost(nav, startDestination = "splash") {
                composable("splash") {
                    SplashScreen()
                    LaunchedEffect(Unit) { delay(1000); nav.navigate("role") { popUpTo("splash") { inclusive = true } } }
                }
                composable("role") { RoleScreen({ go("register/${it.name}") }, { go("login/${it.name}") }, { go("login/ADMIN") }) }
                composable("login/{role}", arguments = listOf(navArgument("role") { type = NavType.StringType })) { destination ->
                    val role = destination.role()
                    LoginScreen(role, ::back, { login(role) }, { go("register/${role.name}") }, { info = it })
                }
                composable("register/{role}", arguments = listOf(navArgument("role") { type = NavType.StringType })) { destination ->
                    val role = destination.role()
                    RegistrationScreen(role, ::back) { values ->
                        vm.saveSettings("registration", values)
                        if (role == Role.FARMER) go("verification") else {
                            buyerVm.saveProfile(BuyerProfile(name = values["Nama"].orEmpty(), email = values["Email"].orEmpty(), phone = values["Telepon"].orEmpty()))
                            login(Role.BUYER)
                        }
                    }
                }
                composable("verification") { VerificationForm(::back, { values -> vm.saveSettings("verification", values); go("verification-waiting") }) }
                composable("verification-rejected") { VerificationForm(::back, { values -> vm.saveSettings("verification", values); go("verification-waiting") }, true) }
                composable("verification-waiting") { VerificationWaitingScreen { go("login/FARMER") } }
                composable("buyer") { LaunchedEffect(Unit) { nav.navigate("buyer-orders") { popUpTo("buyer") { inclusive = true } } } }
                composable("buyer-orders") { BuyerOrdersScreen(buyerData, ::openBuyerOrder, { go("buyer-review/$it") },
                    { id -> buyerVm.reorder(id) { cartOpen = true } }, { info = it }, { go("buyer-schedule/$it") }) }
                composable("buyer-store") { BuyerStoreScreen(buyerData, { buyerTab("buyer-orders") }, buyerVm::addToCart, { cartOpen = true }) }
                composable("buyer-account") { BuyerAccountScreen(buyerData.profile, buyerVm::saveProfile, { buyerTab("buyer-orders") },
                    { go(refundDestination(BuyerSamples.completed.id)) }, ::logout, { info = it }, buyerData.addresses, { go("buyer-addresses") }) }
                composable("buyer-order/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { destination ->
                    val order = buyerData.orders.find { it.id == destination.arguments?.getString("id") }
                    if (order == null) EmptyState("Pesanan tidak ditemukan")
                    else BuyerOrderDetailScreen(order, buyerData.refundFor(order.id), buyerData.reviewFor(order.id), buyerVm.now, ::back,
                        { go("buyer-review/${order.id}") }, { buyerVm.reorder(order.id) { cartOpen = true } }, { go(refundDestination(order.id)) }, { go("buyer-store") }, { buyerVm.acceptOrder(order.id) }, { openBuyerOrder(order.id) })
                }
                composable("buyer-addresses") { BuyerAddressScreen(buyerData, ::back, buyerVm::selectAddress, buyerVm::saveAddress) }
                composable("buyer-checkout") { BuyerCheckoutScreen(buyerData, buyerVm.now, state.settings["admin-settings"]?.get("Limit")?.toLongOrNull() ?: 10000000L, ::back,
                    { go("buyer-addresses") }) { request -> buyerVm.checkout(request, state.settings["admin-settings"]?.get("Limit")?.toLongOrNull() ?: 10000000L) { go("buyer-created/$it") } } }
                composable("buyer-created/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { destination ->
                    val order = buyerData.orders.find { it.id == destination.arguments?.getString("id") }
                    if (order == null) EmptyState("Pesanan tidak ditemukan")
                    else BuyerOrderCreatedScreen(order, { buyerTab("buyer-orders") }, { openBuyerOrder(order.id) }, { buyerTab("buyer-store") }, { buyerVm.acceptOrder(order.id) })
                }
                composable("buyer-payment/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { destination ->
                    val order = buyerData.orders.find { it.id == destination.arguments?.getString("id") }
                    if (order == null) EmptyState("Pesanan tidak ditemukan")
                    else BuyerPaymentScreen(order, buyerVm.now, ::back, { buyerVm.submitProof(order.id, it) }, { buyerVm.reviewPayment(order.id, it) },
                        { go("buyer-tracking/${order.id}") }, buyerVm::message)
                }
                composable("buyer-tracking/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { destination ->
                    val order = buyerData.orders.find { it.id == destination.arguments?.getString("id") }
                    if (order == null) EmptyState("Pesanan tidak ditemukan")
                    else BuyerTrackingScreen(order, ::back, { buyerVm.receive(order.id) }, { buyerVm.arrive(order.id) },
                        { text, done -> buyerVm.reportDeliveryIssue(order.id, text, done) }, { go("buyer-review/${order.id}") },
                        { go(refundDestination(order.id)) }, { go("buyer-schedule/${order.id}") })
                }
                composable("buyer-schedule/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { destination ->
                    val order = buyerData.orders.find { it.id == destination.arguments?.getString("id") }
                    if (order == null) EmptyState("Pesanan tidak ditemukan")
                    else BuyerScheduleResponseScreen(order, ::back) { buyerVm.respondSchedule(order.id, it) }
                }
                composable("buyer-review/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { destination ->
                    val order = buyerData.orders.find { it.id == destination.arguments?.getString("id") }
                    if (order == null) EmptyState("Pesanan tidak ditemukan")
                    else BuyerReviewScreen(order, buyerData.reviewFor(order.id), ::back) { rating, tags, comment -> buyerVm.saveReview(order.id, rating, tags, comment) }
                }
                composable("buyer-refund-form/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { destination ->
                    val order = buyerData.orders.find { it.id == destination.arguments?.getString("id") }
                    if (order == null) EmptyState("Pesanan tidak ditemukan")
                    else BuyerRefundFormScreen(order, buyerData.refundFor(order.id), buyerVm.now, ::back,
                        { request -> buyerVm.submitRefund(request) { go("buyer-refund/${order.id}") } }, { go("buyer-refund/${order.id}") })
                }
                composable("buyer-refund/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { destination ->
                    val orderId = destination.arguments?.getString("id").orEmpty()
                    val refund = buyerData.refundFor(orderId)
                    if (refund == null) EmptyState("Pengajuan refund belum ada")
                    else BuyerRefundStatusScreen(refund, buyerData.ticketFor(refund.id), buyerVm.now, ::back,
                        { buyerVm.respond(orderId, it) }, { buyerVm.confirmReceived(orderId) }, { buyerVm.reportMissing(orderId) },
                        { buyerVm.createTicket(orderId) { go("buyer-help/$it") } })
                }
                composable("buyer-help/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { destination ->
                    val ticket = buyerData.tickets.find { it.id == destination.arguments?.getString("id") }
                    if (ticket == null) EmptyState("Tiket belum tercatat")
                    else BuyerHelpScreen(ticket, buyerData.refunds.find { it.id == ticket.refundId }, state.settings["admin-settings"]?.get("WhatsApp"), ::back,
                        { note, uri, done -> buyerVm.addEvidence(ticket.id, note, uri, done) }, { info = it })
                }
                composable("home") { FarmerHomeScreen(state.data, { tab("products") }, { tab("orders") }, { go("product/$it") }, { tab("profile") }) }
                composable("products") { ProductListScreen(state.data.products, { tab("home") }, { go("product/new") }, { go("product/$it") }) }
                composable("product/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { destination ->
                    val id = destination.arguments?.getString("id")
                    val product = state.data.products.find { it.id == id }
                    if (product == null && id != "new") EmptyState("Produk tidak ditemukan")
                    else ProductFormScreen(product, ::back, vm::saveProduct)
                }
                composable("orders") { OrderListScreen(state.data.orders, { tab("home") }, { go("order/$it") }, { go("refund") }) }
                composable("order/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { destination ->
                    val id = destination.arguments?.getString("id")
                    val order = state.data.orders.find { it.id == id }
                    if (order == null) EmptyState("Pesanan tidak ditemukan")
                    else OrderDetailScreen(order, ::back, { target, reason -> vm.changeOrder(order.id, target, reason) }, { go("schedule/${order.id}") })
                }
                composable("schedule/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { destination ->
                    val order = state.data.orders.find { it.id == destination.arguments?.getString("id") }
                    if (order == null) EmptyState("Pesanan tidak ditemukan")
                    else ScheduleScreen(order, ::back) { schedule, reason -> vm.proposeSchedule(order.id, schedule, reason) }
                }
                composable("refund") { RefundReviewScreen(state.decisions["RF-002"], ::back,
                    { approved, reason -> vm.decide("RF-002", if (approved) "Disetujui" else "Ditolak", reason, !approved) }, { go("refund-transfer") }) }
                composable("refund-transfer") {
                    if (state.decisions["RF-002"]?.startsWith("Disetujui") == true)
                        RefundTransferScreen(state.decisions["RF-002-proof"], ::back) { uri -> vm.decide("RF-002-proof", "Menunggu konfirmasi pembeli", uri) }
                    else EmptyState("Refund belum disetujui", "Setujui pengajuan terlebih dahulu.")
                }
                composable("profile") { ProfileScreen(::go, ::logout, { info = it }, state.settings) }
                composable("bank") { BankScreen(state.settings["bank"].orEmpty(), ::back) { vm.saveSettings("bank", it) } }
                composable("qris") { QrisScreen(state.settings["qris"].orEmpty(), ::back) { vm.saveSettings("qris", it) } }
                composable("service") { ServiceScreen(state.settings["service"].orEmpty(), ::back) { vm.saveSettings("service", it) } }
                composable("shop") { ShopScreen(state.settings["shop"].orEmpty(), ::back) { vm.saveSettings("shop", it) } }
                composable("admin") { AdminHomeScreen(::go, ::logout) }
                composable("admin-verification") { AdminVerificationScreen(state.decisions, ::back, vm::decide) }
                composable("admin-users") { AdminUsersScreen(state.decisions, ::back, vm::decide) }
                composable("admin-commodities") { AdminCommoditiesScreen(state.settings, ::back, vm::saveSettings) }
                composable("admin-moderation") { AdminModerationScreen(state.decisions["PRD-052"], ::back) { decision, reason, required -> vm.decide("PRD-052", decision, reason, required) } }
                composable("admin-orders") { AdminOrdersScreen(state.data.orders, ::back) }
                composable("admin-disputes") { AdminDisputeScreen(state.decisions["TKT-026"], ::back) { decision, reason, required ->
                    vm.decide("TKT-026", decision, reason, required)
                    if (reason.contains(" · Suspensi: ")) vm.decide("USR-012", "Disuspensi", reason.substringAfter(" · Suspensi: "), true)
                } }
                composable("admin-settings") { AdminSettingsScreen(state.settings["admin-settings"].orEmpty(), ::back) { vm.saveSettings("admin-settings", it) } }
            }
        }
    }
    if (cartOpen) BuyerCartDialog(buyerData, buyerVm::setCartQuantity, onCheckout = { cartOpen = false; go("buyer-checkout") }) { cartOpen = false }
    if (info != null) AlertDialog(onDismissRequest = { info = null }, title = { Text("Informasi") }, text = { Text(info!!) },
        confirmButton = { TextButton({ info = null }) { Text("Mengerti") } })
}
private fun NavBackStackEntry.role(): Role = runCatching { Role.valueOf(arguments?.getString("role").orEmpty()) }.getOrDefault(Role.BUYER)
