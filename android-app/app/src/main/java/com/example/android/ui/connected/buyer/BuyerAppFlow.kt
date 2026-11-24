package id.panenku.app.ui.connected.buyer

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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import id.panenku.app.ui.connected.*
import kotlinx.coroutines.flow.first
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

/** Buyer UI is separate so seller, onboarding, notification and admin code can remain intact. */
@Composable internal fun BuyerAppFlow(state:ConnectedState,vm:ConnectedViewModel,
    notificationsScreen:(@Composable (onBack:()->Unit)->Unit)?=null,loadNotifications:(()->Unit)?=null) {
key(state.user!!.id) {
        var screen by rememberSaveable { mutableStateOf(if(state.user!!.role=="admin") "admin" else "catalog") }
        var orderId by rememberSaveable { mutableStateOf("") }
        var refundId by rememberSaveable { mutableStateOf("") }
        var ticketId by rememberSaveable { mutableStateOf("") }
        var storeProduct by rememberSaveable { mutableStateOf("") }
        var storeReturn by rememberSaveable { mutableStateOf("catalog") }
        var addressReturn by rememberSaveable { mutableStateOf("checkout") }
        var trackingReturn by rememberSaveable { mutableStateOf("orders") }
        var reorderId by rememberSaveable { mutableStateOf<String?>(null) }
        var navMessage by remember { mutableStateOf<String?>(null) }
        var productId by rememberSaveable { mutableStateOf("") }
        var productReturn by rememberSaveable { mutableStateOf("catalog") }
        var notificationReturn by rememberSaveable { mutableStateOf("catalog") }
        var searchQuery by rememberSaveable {mutableStateOf("")}
        var searchCommodity by rememberSaveable {mutableStateOf("")}
        var searchSort by rememberSaveable {mutableStateOf("price_asc")}
        var searchStores by rememberSaveable {mutableStateOf(false)}
        val criteria=BuyerSearch(searchQuery,searchCommodity,searchSort,searchStores)
        fun search(value:BuyerSearch=BuyerSearch()){searchQuery=value.query;searchCommodity=value.commodity;searchSort=value.sort;searchStores=value.stores;screen="results"}
        fun openProduct(id:String){productId=id;productReturn=screen;screen="product"}
        val buyer=state.user!!.role=="pembeli"
        val admin=state.user!!.role=="admin"
        val savedScreens=rememberSaveableStateHolder()
        val tabs=if(admin) listOf("admin" to "Verifikasi","refunds" to "Refund","account" to "Akun")
            else listOf("catalog" to if(buyer) "Toko" else "Stok","orders" to "Pesanan","refunds" to "Refund","account" to "Akun")
        val buyerMain=setOf("catalog","buyer-stock","orders","refunds","account","store","results")
        val green=setOf("payment","detail","refund-form","refund-detail","ticket","schedule-decision","created","buyer-addresses")
        fun openOrder(id:String){orderId=id;trackingReturn="orders";screen="detail"}
        fun openBuyerOrder(order:JsonObject){orderId=order.text("orderId");trackingReturn="orders";screen=if(order.text("orderStatus")=="selesai") "completed" else "detail"}
        fun openStore(id:String,back:String){if(id.isBlank())navMessage="Profil toko belum tersedia dari pesanan ini." else {storeProduct=id;storeReturn=back;screen="store"}}
        fun backBuyer(){screen=when(screen){
            "payment","schedule-decision","refund-form"->"detail"
            "detail"->trackingReturn
            "completed"->"orders"
            "review"->"completed"
            "notifications"->notificationReturn
            "late-payments"->"account"
            "product"->productReturn
            "refund-detail"->"refunds"
            "ticket"->"refund-detail"
            "store"->storeReturn
            "buyer-addresses"->addressReturn
            else->"catalog"
        }}
        LaunchedEffect(screen,orderId,refundId,ticketId,storeProduct,productId,criteria){
            vm.state.first{!it.busy}
            when(screen){
                "catalog"->vm.buyerHome()
                "buyer-stock"->vm.buyerCategories()
                "results"->vm.buyerSearch(criteria)
                "cart"->vm.buyerCart()
                "orders"->vm.orders()
                "refunds"->vm.refunds()
                "detail","payment","schedule-decision","refund-form"->if(buyer) vm.buyerTransaction(orderId) else vm.order(orderId)
                "completed"->vm.buyerCompleted(orderId)
                "review"->vm.buyerCompleted(orderId,false)
                "refund-detail"->vm.buyerRefundDetail(refundId)
                "ticket"->vm.buyerTicket(ticketId)
                "store"->vm.buyerStore(storeProduct)
                "account"->vm.profile()
                "late-payments"->vm.latePayments(false)
                "notifications"->vm.buyerNotifications()
                "admin"->vm.adminQueue()
                "product"->vm.buyerProduct(productId)
                "checkout","buyer-addresses"->if(buyer) vm.buyerAddresses() else vm.invalidateSchedules()
            }
        }
        BackHandler((if(buyer) screen !in setOf("catalog","buyer-stock","orders","refunds","account") else screen !in tabs.map{it.first}) && !state.busy){
            if(buyer)backBuyer() else screen=if(screen=="refund-form")"detail" else "catalog"
        }
        @Composable fun signedInContent(){
            Scaffold(contentWindowInsets=if(buyer) WindowInsets(0,0,0,0) else ScaffoldDefaults.contentWindowInsets,bottomBar={
                if(buyer){if(screen in buyerMain) BuyerNavigation(if(screen=="results")"buyer-stock" else screen){if(!state.busy)screen=it}}
                else NavigationBar{tabs.forEach{(route,label)->NavigationBarItem(selected=screen==route,onClick={if(!state.busy)screen=route},icon={Icon(when(route){"catalog"->Icons.Outlined.Storefront;"orders"->Icons.Outlined.ReceiptLong;"refunds"->Icons.Outlined.AssignmentReturn;else->Icons.Outlined.Person},label)},label={Text(label)})}}
            }){padding->Box(Modifier.fillMaxSize().padding(padding)){
                @Composable fun screenContent(){when(screen){
                    "catalog"->BuyerHomeScreen(state,vm,{search()},{screen="cart"},{notificationReturn="catalog";screen="notifications"},{addressReturn="catalog";screen="buyer-addresses"},::openProduct)
                    "buyer-stock"->BuyerCategoriesScreen(state,vm,{screen="catalog"},{screen="cart"},{search(BuyerSearch(commodity=it))},::openProduct)
                    "results"->BuyerResultsScreen(state,vm,criteria,{search(it)},{screen="catalog"},{screen="cart"},::openProduct,{openStore(it,"results")})
                    "cart"->BuyerCartScreen(state,vm,{screen="catalog"},{savedScreens.removeState("checkout");screen="checkout"},::openProduct)
                    "checkout"->if(buyer)BuyerCheckoutScreen(state,vm,{screen="cart"},{addressReturn="checkout";screen="buyer-addresses"}){
                        orderId=vm.state.value.detail?.text("orderId").orEmpty();vm.dismiss();screen="created"
                    } else ConnectedCheckout(state,vm,{screen="cart"}){screen="orders"}
                    "buyer-addresses"->BuyerAddressesScreen(state,vm){screen=addressReturn}
                    "created"->Column{state.buyerCheckoutWarning?.let{Box(Modifier.padding(16.dp)){BNotice("Periksa keranjang",it,"orange")}};Box(Modifier.weight(1f)){state.detail?.let{BuyerOrderCreatedScreen(it,{screen="detail"},{screen="catalog"})}}}
                    "orders"->if(buyer)BuyerOrdersScreen(state,vm,{screen="catalog"},::openBuyerOrder,{orderId=it.text("orderId");screen="payment"},{orderId=it.text("orderId");screen="review"},{reorderId=it.text("orderId")}) else ConnectedOrders(state,vm,::openOrder)
                    "detail"->if(buyer)BuyerTrackingScreen(state,vm,{screen=trackingReturn},{screen="payment"},{screen="schedule-decision"},{screen="refund-form"},{screen="completed"}) else ConnectedOrder(state,vm,buyer,{screen="orders"},{screen="refund-form"})
                    "completed"->BuyerCompletedScreen(state,vm,{screen="orders"},{trackingReturn="completed";screen="detail"},{screen="review"},{reorderId=orderId},{openStore(state.detail?.list("items")?.firstOrNull()?.text("productId").orEmpty(),"completed")})
                    "review"->BuyerReviewScreen(state,vm){screen="completed"}
                    "payment"->BuyerPaymentScreen(state,vm){screen="detail"}
                    "schedule-decision"->BuyerScheduleScreen(state,vm){screen="detail"}
                    "refund-form"->if(buyer)BuyerRefundScreen(state,vm,{screen="detail"}){screen="refunds"} else ConnectedRefundForm(orderId,vm,{screen="detail"}){screen="refunds"}
                    "refunds"->if(buyer)BuyerRefundHub(state,vm,{screen="account"}){refundId=it;screen="refund-detail"} else ConnectedRefunds(state,vm,buyer,admin)
                    "refund-detail"->BuyerRefundDetailScreen(state,vm,refundId,{screen="refunds"}){ticketId=it;screen="ticket"}
                    "ticket"->BuyerTicketScreen(state,vm,ticketId){screen="refund-detail"}
                    "account"->if(buyer)BuyerAccountScreen(state,vm,{screen="catalog"},{addressReturn="account";screen="buyer-addresses"},{screen="orders"},{screen="refunds"},notifications={notificationReturn="account";screen="notifications"},latePayments={screen="late-payments"}) else ConnectedAccount(state,vm)
                    "notifications"->BuyerNotificationsScreen(state,vm,{screen=notificationReturn}){route,id->
                        when(route){"refund-detail"->refundId=id;"ticket"->ticketId=id;else->{orderId=id;trackingReturn="orders"}}
                        screen=route
                    }
                    "late-payments"->ConnectedLatePayments(state,vm,false){screen="account"}
                    "store"->BuyerStoreScreen(state,vm){screen=storeReturn}
                    "product"->BuyerProductScreen(state,vm,{screen=productReturn},{screen="cart"},{openStore(it,"product")})
                    "admin"->ConnectedAdmin(state,vm)
                }}
                if(buyer)savedScreens.SaveableStateProvider(screen){screenContent()} else screenContent()
                if(state.busy)Surface(color=MaterialTheme.colorScheme.surface.copy(alpha=0.7f),modifier=Modifier.fillMaxSize().clickable{}){Box(contentAlignment=Alignment.Center){CircularProgressIndicator()}}
            }}
        }
        if(buyer)BuyerTransactionTheme(paper=if(screen in green)BPaper else FPaper){signedInContent()} else signedInContent()
        reorderId?.let{id->AlertDialog(onDismissRequest={reorderId=null},title={Text("Beli lagi pesanan ini?")},text={Text("Produk ditambahkan dengan harga dan stok saat ini. Isi keranjang yang sudah ada tetap dipertahankan. Periksa total kembali sebelum checkout.")},confirmButton={TextButton(onClick={reorderId=null;vm.buyerReorder(id){screen="cart"}}){Text("Tambahkan ke keranjang")}},dismissButton={TextButton(onClick={reorderId=null}){Text("Kembali")}})}
        navMessage?.let{AlertDialog(onDismissRequest={navMessage=null},text={Text(it)},confirmButton={TextButton(onClick={navMessage=null}){Text("OK")}})}
    }
}
