package id.panenku.app.ui.connected

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.*
import id.panenku.app.BuildConfig
import id.panenku.app.ui.auth.RegistrationInput
import id.panenku.app.ui.auth.OpeningRole
import id.panenku.app.ui.auth.FarmerOnboardingState
import id.panenku.app.ui.auth.FarmerVerificationInput
import id.panenku.app.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import id.panenku.app.ui.connected.buyer.*
import id.panenku.app.ui.admin.AdminUiState
import java.security.MessageDigest
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** All active screens use this state; legacy SampleData is never copied into it. */
data class ConnectedState(
    val adminUi: AdminUiState = AdminUiState(),
    val user: ApiUser? = null, val busy: Boolean = false, val error: String? = null, val message: String? = null,
    val products: List<JsonObject> = emptyList(), val cart: JsonObject = JsonObject(),
    val orders: List<JsonObject> = emptyList(), val refunds: List<JsonObject> = emptyList(),
    val commodities: List<JsonObject> = emptyList(), val detail: JsonObject? = null,
    val paymentMethods: List<JsonObject> = emptyList(), val paymentMethodDetail: JsonObject? = null,
    val adminUsers: List<JsonObject> = emptyList(), val adminUserDetail: JsonObject? = null,
    val settings: JsonObject? = null, val auditLogs: List<JsonObject> = emptyList(),
    val disputes: List<JsonObject> = emptyList(), val latePayments: List<JsonObject> = emptyList(),
    val notifications: List<JsonObject> = emptyList(), val unreadNotificationCount: Long = 0,
    val options: List<JsonObject> = emptyList(), val destination: JsonObject? = null,
    val proofs: List<JsonObject> = emptyList(), val fulfillment: JsonObject? = null,
    val quote: JsonObject? = null, val checkoutBody: JsonObject? = null,
    val deliveryAreas: List<String> = emptyList(), val shippingEnabled: Boolean = false,
    val review: JsonObject? = null, val reviewLoaded: Boolean = false,
    val image: ByteArray? = null, val profile: JsonObject? = null,
    val page: Int = 0, val more: Boolean = false,
    val buyerReview: JsonObject? = null, val buyerReviewLoaded: Boolean = false, val buyerReviewAvailable: Boolean = true,
    val buyerRefund: JsonObject? = null, val buyerTicket: JsonObject? = null, val buyerTicketId: String? = null,
    val buyerTicketDraft: JsonObject = JsonObject(), val buyerTicketPendingReason: String? = null,
    val buyerStore: JsonObject? = null, val buyerStoreProducts: List<JsonObject> = emptyList(),
    val buyerStoreReviews: List<JsonObject> = emptyList(),

    val buyerHome: List<JsonObject> = emptyList(), val buyerResults: List<JsonObject> = emptyList(),
    val buyerSearch: BuyerSearch = BuyerSearch(), val buyerSearchLoaded: Boolean = false,
    val buyerSearchPage: Int = 0, val buyerSearchTotal: Long = 0, val buyerSearchMore: Boolean = false,
    val buyerProduct: JsonObject? = null, val buyerRecent: List<JsonObject> = emptyList(),
    val buyerSelected: Set<String> = emptySet(), val buyerKnownCart: Set<String> = emptySet(),
    val buyerSelectionInitialized: Boolean = false, val buyerFulfillment: String = "pickup",
    val buyerNotificationPage: Int = 0, val buyerNotificationMore: Boolean = false,
    val buyerCheckoutWarning: String? = null,
    val buyerAddresses: List<SavedBuyerAddress> = emptyList(), val buyerAddressId: String? = null,
    val buyerTransactionLoaded: Boolean = false, val buyerQris: ByteArray? = null, val buyerQrisId: String? = null,
    val farmerOrderProofs: Map<String, List<JsonObject>> = emptyMap(),
    val farmerVerification: JsonObject? = null,
    val farmerRefund: JsonObject? = null, val farmerRefundOrder: JsonObject? = null,
    val farmerQrisId: String? = null, val farmerQrisImage: ByteArray? = null,
    val farmerOnboarding: FarmerOnboardingState = FarmerOnboardingState()
)
class ConnectedViewModel @JvmOverloads constructor(app: Application,
    private val api: PanenkuApi = PanenkuApi(BuildConfig.API_BASE_URL)) : AndroidViewModel(app) {
    private val buyerAddressStore = BuyerAddressStore(app)
    private val buyerHelpStore = BuyerHelpStore(app)
    private val enrollment = FarmerEnrollment(api)
    private var farmerDraft: RegistrationInput? = null
    private var farmerDummyId = newDummyId()
    private fun newDummyId() = "DUMMY-" + java.util.UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
    private val prefs = app.getSharedPreferences("request_keys", 0)
    private val keys = RequestKeys({ prefs.getString(it, null) }, { k, v -> prefs.edit().putString(k, v).commit(); Unit }, { prefs.edit().remove(it).commit(); Unit })
    private val mutable = MutableStateFlow(ConnectedState())
    val state = mutable.asStateFlow()
    private val role get() = if (state.value.user?.role == "pembeli") "buyer" else state.value.user?.role ?: "buyer"
    private fun set(block: (ConnectedState) -> ConnectedState) { mutable.update(block) }
    private fun run(message: String? = null, block: suspend () -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch {
            set { it.copy(busy = true, error = null, message = null) }
            try { block(); if (message != null) set { it.copy(message = message) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (api.session == null && state.value.user != null) mutable.value = ConnectedState(error = "Sesi berakhir. Silakan masuk kembali.")
                else set { it.copy(error = e.message ?: "Server belum dapat dihubungi.") }
            }
            finally { set { it.copy(busy = false) } }
        }
    }

    /** Buyer catalog uses live products and the existing profile/notification/cart contracts. */
    fun buyerHome() = run {
        require(role=="buyer")
        val result=api.get(BuyerSearch().path()).asJsonObject
        val products=result.list("items")
        set { it.copy(buyerHome=products,products=(it.products+products).distinctBy { p->p.text("id") }) }
        val categories=api.get("buyer/commodities").rows()
        val profile=api.get("auth/profile").asJsonObject
        val notices=api.get("notifications?page=0&size=1").asJsonObject
        val cart=api.get("buyer/cart").asJsonObject
        set { it.copy(commodities=categories,profile=profile,unreadNotificationCount=notices.long("unreadCount"),cart=cart) }
    }
    fun buyerCategories() = run {
        val rows=api.get("buyer/commodities").rows();set { it.copy(commodities=rows) }
    }
    fun buyerSearch(criteria:BuyerSearch,page:Int=0) = run {
        require(role=="buyer")
        criteria.path(page) // validate even when searching stores locally
        if(page==0)set { it.copy(buyerSearch=criteria,buyerSearchLoaded=false,buyerResults=emptyList(),buyerSearchMore=false) }
        else require(state.value.buyerSearch==criteria) { "Pencarian berubah. Muat ulang hasil." }
        val products:List<JsonObject>;val total:Long;val more:Boolean
        if(criteria.stores) {
            val all=mutableListOf<JsonObject>();var n=0
            do {val r=api.get("buyer/products?sort=${criteria.sort}&page=$n&size=100"+(if(criteria.commodity.isBlank()) "" else "&commodityId=${criteria.commodity}")).asJsonObject
                all+=r.list("items");n++
                val remaining=n*100L<r.long("totalElements")
            } while(remaining)
            products=all.filter { it.text("storeName").contains(criteria.query.trim(),true) }.distinctBy { it.text("farmerId") }
            total=products.size.toLong();more=false
        } else {
            val r=api.get(criteria.path(page)).asJsonObject;products=r.list("items");total=r.long("totalElements");more=(page+1)*20L<total
        }
        set { it.copy(buyerSearch=criteria,buyerSearchLoaded=true,buyerSearchPage=page,buyerSearchTotal=total,buyerSearchMore=more,
            buyerResults=if(page==0)products else (it.buyerResults+products).distinctBy { p->p.text("id") },products=(it.products+products).distinctBy { p->p.text("id") }) }
        if(state.value.commodities.isEmpty()) {val c=api.get("buyer/commodities").rows();set { it.copy(commodities=c) }}
    }
    fun buyerProduct(id:String) = run {
        set { it.copy(buyerProduct=null) }
        val result=api.get("buyer/products/$id").asJsonObject
        val product=result.getAsJsonObject("product") ?: error("Produk belum tersedia.")
        set { it.copy(buyerProduct=result,buyerRecent=(listOf(product)+it.buyerRecent).distinctBy { p->p.text("id") }.take(12),products=(listOf(product)+it.products).distinctBy { p->p.text("id") }) }
    }
    private suspend fun loadBuyerCart() {
        val cart=api.get("buyer/cart").asJsonObject
        val rows=cart.list("items");val ids=rows.map { it.text("id") }.toSet()
        val available=rows.filter { it.flag("available") }.map { it.text("id") }.toSet()
        set { it.copy(cart=cart,buyerSelected=if(!it.buyerSelectionInitialized)available else (it.buyerSelected.intersect(ids)+(available-it.buyerKnownCart)),
            buyerKnownCart=ids,buyerSelectionInitialized=true,quote=null,checkoutBody=null,fulfillment=null,deliveryAreas=emptyList(),shippingEnabled=false) }
        rows.firstOrNull { it.flag("available") }?.let { row ->
            try {val detail=api.get("buyer/products/${row.text("productId")}").asJsonObject
                set { it.copy(shippingEnabled=detail.flag("shippingEnabled"),deliveryAreas=detail.getAsJsonArray("serviceArea")?.map { a->a.asString }.orEmpty()) }
            } catch(e:ApiFailure) {if(e.status !in setOf(404,409))throw e}
        }
    }
    fun buyerCart() = run { loadBuyerCart() }
    fun buyerSelectCart(id:String?,checked:Boolean) {
        if(state.value.busy)return
        val rows=state.value.cart.list("items")
        // Individual unavailable items remain selectable for removal, never for checkout.
        val targets=if(id==null)rows.filter { it.flag("available") }.map { it.text("id") }.toSet() else rows.filter { it.text("id")==id }.map { it.text("id") }.toSet()
        set { it.copy(buyerSelected=if(checked)it.buyerSelected+targets else if(id==null)emptySet() else it.buyerSelected-targets,buyerSelectionInitialized=true,fulfillment=null,quote=null,checkoutBody=null) }
    }
    fun buyerFulfillment(method:String) {require(method in setOf("pickup","delivery"));if(state.value.busy)return;set { it.copy(buyerFulfillment=method,fulfillment=null,quote=null,checkoutBody=null) }}
    fun buyerQuantity(id:String,value:String) = run {
        val row=state.value.cart.list("items").firstOrNull { it.text("id")==id } ?: error("Item tidak ditemukan.")
        val amount=kg(value)
        // A shortage describes the old quantity; the backend validates the replacement.
        require(amount<=row.text("stockAvailable").toBigDecimal()) { "Jumlah melebihi stok tersedia." }
        api.put("buyer/cart/items/$id",json("quantityKg" to amount));loadBuyerCart()
    }
    fun buyerDeleteSelected() = run {
        val ids=state.value.buyerSelected.toList();require(ids.isNotEmpty()) { "Pilih produk dahulu." }
        var failure:Exception?=null
        try {ids.forEach { api.delete("buyer/cart/items/$it") }} catch(e:CancellationException){throw e} catch(e:Exception){failure=e}
        loadBuyerCart()
        if(failure!=null)error("Sebagian produk mungkin sudah dihapus. Periksa keranjang sebelum mencoba kembali.")
    }
    /** Order creation is acknowledged before optional cart cleanup; never create a second order to retry cleanup. */
    fun buyerCheckout(done:()->Unit) = run {
        val body=state.value.checkoutBody ?: error("Periksa total terlebih dahulu.")
        require(state.value.quote?.flag("canCheckout")==true) { "Checkout belum diizinkan server." }
        val orderedIds=body.list("items").map { it.text("productId") }.toSet()
        val before=state.value.cart.list("items").filter { it.text("productId") in orderedIds }.map { it.deepCopy() }
        val order=try {keyedPost("buyer/checkout",body).asJsonObject} catch(e:ApiFailure) {if(e.status in setOf(400,409))invalidateSchedules();throw e}
        set { it.copy(detail=order,quote=null,checkoutBody=null,fulfillment=null,buyerCheckoutWarning=null,message="Pesanan ${order.text("orderId")} dibuat.") }
        try {
            val live=api.get("buyer/cart").asJsonObject
            before.forEach { old ->
                val current=live.list("items").firstOrNull { it.text("id")==old.text("id") && it.text("productId")==old.text("productId") }
                if(current!=null) {
                    if(current.text("quantityKg").toBigDecimal()==old.text("quantityKg").toBigDecimal()) api.delete("buyer/cart/items/${old.text("id")}")
                    else set { it.copy(buyerCheckoutWarning="Pesanan sudah dibuat. Isi keranjang berubah; periksa kembali sebelum memesan lagi.") }
                }
            }
            val remaining=api.get("buyer/cart").asJsonObject
            set { it.copy(cart=remaining,buyerSelected=emptySet(),buyerSelectionInitialized=false) }
        } catch(e:CancellationException){throw e} catch(e:Exception) {
            set { it.copy(buyerCheckoutWarning="Pesanan sudah dibuat. Sebagian item mungkin masih ada di keranjang; periksa sebelum memesan lagi.") }
        }
        done()
    }
    private suspend fun loadBuyerNotifications(page:Int=0) {
        val result=api.get("notifications?page=$page&size=50").asJsonObject
        set { it.copy(notifications=if(page==0)result.list("items") else (it.notifications+result.list("items")).distinctBy { n->n.text("id") },
            unreadNotificationCount=result.long("unreadCount"),buyerNotificationPage=page,buyerNotificationMore=(page+1)*50L<result.long("totalElements")) }
    }
    fun buyerNotifications(page:Int=0) = run {loadBuyerNotifications(page)}
    fun buyerReadAll() = run {api.put("notifications/read-all",json());loadBuyerNotifications()}
    fun buyerOpenNotification(notice:JsonObject,done:(String,String)->Unit) = run {
        val id=notice.text("id");require(state.value.notifications.any { it.text("id")==id })
        if(!notice.flag("isRead"))api.put("notifications/$id/read",json())
        loadBuyerNotifications()
        val link=buyerLink(notice.text("deepLink")) ?: return@run
        when(link.first) {
            "orders"->{val order=api.get("buyer/orders/${link.second}").asJsonObject
                val route=when { order.obj("pendingScheduleChange").text("id").isNotBlank()->"schedule-decision";order.text("orderStatus")=="selesai"->"completed";order.text("orderStatus") in setOf("menunggu_pembayaran","pembayaran_ditolak")->"payment";else->"detail" }
                done(route,link.second)
            }
            "refunds"->done("refund-detail",link.second)
            "escalations"->done("ticket",link.second)
        }
    }


    private fun requireAdmin() { require(state.value.user?.role=="admin") { "Gunakan akun administrator." } }
    private fun adminSet(block:(AdminUiState)->AdminUiState) = set { it.copy(adminUi=block(it.adminUi)) }
    fun loginAdmin(email:String,password:String) = run {
        val session=api.login(email,password)
        if(session.user.role!="admin") {
            try {api.logout()} catch(e:CancellationException){throw e} catch(_:Exception){} // logout always clears local tokens
            error("Akun ini bukan administrator. Gunakan halaman masuk sesuai peran Anda.")
        }
        mutable.value=ConnectedState(user=session.user,busy=true)
    }
    fun adminLoadHome() = run {
        requireAdmin()
        val queue=api.get("admin/farmers/verification-queue?page=0&size=3&status=menunggu&search=").asJsonObject
        val disputes=api.get("admin/disputes?page=0&size=3").asJsonObject
        val users=api.get("admin/users?page=0&size=1&status=suspended").asJsonObject
        val audit=api.get("admin/audit-logs?page=0&size=5").asJsonObject
        adminSet { it.copy(loaded=true,verificationCount=queue.long("totalElements"),disputeCount=disputes.long("totalElements"),suspendedCount=users.long("totalElements"),priorities=queue.list("items"),recentAudit=audit.list("items"),disputes=disputes.list("items")) }
    }
    private suspend fun adminLoadQueueSnapshot(status:String,search:String,page:Int) {
        require(status in setOf("menunggu","disetujui","ditolak"));require(search.length<=100)
        val r=api.get("admin/farmers/verification-queue?page=$page&size=50&status=${query(status)}&search=${query(search.trim())}").asJsonObject
        adminSet { it.copy(queue=if(page==0)r.list("items") else (it.queue+r.list("items")).distinctBy { q->q.text("farmerId") },queueStatus=status,queueQuery=search,queuePage=page,queueMore=(page+1)*50L<r.long("totalElements"),queueTotal=r.long("totalElements"),verificationCount=if(status=="menunggu")r.long("totalElements") else it.verificationCount,verification=if(page==0)null else it.verification,verificationUser=if(page==0)"" else it.verificationUser) }
    }
    fun adminLoadQueue(status:String="menunggu",search:String="",page:Int=0) = run {requireAdmin();adminLoadQueueSnapshot(status,search,page)}
    fun adminInspectFarmer(id:String) = run {
        requireAdmin();adminSet { it.copy(verification=null,verificationUser=id) }
        val result=api.get("admin/farmers/$id/verification").asJsonObject
        adminSet {it.copy(verification=result)}
    }
    fun adminVerifySelected(approved:Boolean,reason:String,checked:Boolean) = run {
        requireAdmin();val a=state.value.adminUi;val row=a.verification ?: error("Pilih dan periksa pengajuan dahulu.")
        require(row.text("status")=="menunggu") { "Pengajuan sudah diputuskan. Muat ulang." }
        require(!approved || checked && row.flag("hasDocument")) { "Lengkapi pemeriksaan dokumen dan data toko." }
        require(approved || reason.trim().isNotEmpty()) { "Alasan penolakan wajib diisi." }
        val latest=api.get("admin/farmers/${a.verificationUser}/verification").asJsonObject
        require(latest.text("applicationId")==row.text("applicationId") && latest.text("submittedAt")==row.text("submittedAt") && latest.text("status")=="menunggu") {
            adminSet { it.copy(verification=latest) }; "Pengajuan berubah. Periksa ulang data dan dokumen sebelum memutuskan."
        }
        api.put("admin/farmers/${a.verificationUser}/verify",json("approved" to approved,"rejectionReason" to reason.trim().takeIf { !approved }))
        val acknowledged=row.deepCopy().apply{addProperty("status",if(approved)"disetujui" else "ditolak");addProperty("rejectionReason",if(approved)"" else reason.trim())}
        adminSet {it.copy(verification=acknowledged)}
        var message="Keputusan verifikasi tersimpan di server."
        try {adminLoadQueueSnapshot(a.queueStatus,a.queueQuery,0)} catch(e:CancellationException){throw e} catch(_:Exception){message+=" Antrean belum dimuat ulang; perbarui sebelum tindakan berikutnya."}
        set {it.copy(message=message)}
    }
    private suspend fun adminLoadUsersSnapshot(role:String,status:String,search:String,page:Int) {
        require(role in setOf("","admin","petani","pembeli") && status in setOf("","active","suspended") && search.length<=100)
        val filters="page=$page&size=50"+(if(role.isNotBlank())"&role=${query(role)}" else "")+(if(status.isNotBlank())"&status=${query(status)}" else "")+(if(search.isNotBlank())"&search=${query(search.trim())}" else "")
        val r=api.get("admin/users?$filters").asJsonObject
        adminSet { it.copy(users=if(page==0)r.list("items") else (it.users+r.list("items")).distinctBy { u->u.text("id") },usersRole=role,usersStatus=status,usersQuery=search,usersPage=page,usersMore=(page+1)*50L<r.long("totalElements"),user=if(page==0)null else it.user) }
    }
    fun adminLoadUsers(role:String="",status:String="",search:String="",page:Int=0,selected:String="") = run {
        requireAdmin();adminLoadUsersSnapshot(role,status,search,page)
        if(selected.isNotBlank()) {val u=api.get("admin/users/$selected").asJsonObject;adminSet {it.copy(user=u)}}
    }
    fun adminInspectUser(id:String) = run {requireAdmin();adminSet {it.copy(user=null)};val result=api.get("admin/users/$id").asJsonObject;adminSet {it.copy(user=result)}}
    fun adminChangeUser(suspend:Boolean,reason:String) = run {
        requireAdmin();val a=state.value.adminUi;val u=a.user ?: error("Pilih pengguna dahulu.")
        require(u.text("role")!="admin" && u.text("id")!=state.value.user?.id) { "Akun admin tidak dapat disuspensi." }
        require(reason.trim().isNotEmpty() && reason.length<=1000) { "Isi alasan tindakan maksimal 1000 karakter." }
        api.put("admin/users/${u.text("id")}/suspend",json("suspend" to suspend,"reason" to reason.trim()))
        val acknowledged=u.deepCopy().apply{addProperty("status",if(suspend)"suspended" else "active")}
        adminSet {it.copy(user=acknowledged)}
        var message="Status akun tersimpan di server."
        try {
            adminLoadUsersSnapshot(a.usersRole,a.usersStatus,a.usersQuery,0)
            val result=api.get("admin/users/${u.text("id")}").asJsonObject;adminSet {it.copy(user=result)}
        } catch(e:CancellationException){throw e} catch(_:Exception){adminSet {it.copy(user=acknowledged)};message+=" Daftar pengguna belum dimuat ulang."}
        set {it.copy(message=message)}
    }
    fun adminLoadCommodities() = run {requireAdmin();val rows=api.get("admin/commodities").rows();adminSet {it.copy(commodities=rows)}}
    fun adminSaveCommodity(id:String?,name:String,active:Boolean,done:()->Unit) = run {
        requireAdmin();require(name.trim().isNotEmpty() && name.trim().length<=100) { "Isi nama komoditas maksimal 100 karakter." }
        val body=json("name" to name.trim(),"isActive" to active)
        val saved=(if(id==null)api.post("admin/commodities",body) else api.put("admin/commodities/$id",body)).asJsonObject
        adminSet {it.copy(commodities=(it.commodities.filterNot { c->c.text("id")==saved.text("id") }+saved))};done()
        var message="Komoditas tersimpan di server."
        try {val rows=api.get("admin/commodities").rows();adminSet {it.copy(commodities=rows)}} catch(e:CancellationException){throw e} catch(_:Exception){message+=" Daftar belum dimuat ulang."}
        set {it.copy(message=message)}
    }
    private suspend fun adminLoadDisputesSnapshot(page:Int) {
        val r=api.get("admin/disputes?page=$page&size=50").asJsonObject
        adminSet { it.copy(disputes=if(page==0)r.list("items") else (it.disputes+r.list("items")).distinctBy { d->d.text("id") },disputePage=page,disputeMore=(page+1)*50L<r.long("totalElements")) }
    }
    fun adminLoadDisputes(page:Int=0) = run {
        requireAdmin()
        val selected=state.value.adminUi.dispute?.text("id").orEmpty()
        adminLoadDisputesSnapshot(page)
        // Returning from evidence keeps the selected ticket and its saved form while refreshing server facts.
        if(page==0 && selected.isNotBlank()) {
            val dispute=api.get("admin/disputes/$selected").asJsonObject
            val refund=api.get("admin/refunds/${dispute.text("refundId")}").asJsonObject
            adminSet {it.copy(dispute=dispute,refund=refund)}
        }
    }
    fun adminInspectDispute(id:String) = run {
        requireAdmin();adminSet {it.copy(dispute=null,refund=null)}
        val dispute=api.get("admin/disputes/$id").asJsonObject
        val refund=api.get("admin/refunds/${dispute.text("refundId")}").asJsonObject
        adminSet {it.copy(dispute=dispute,refund=refund)}
    }
    fun adminDecideSelected(approve:Boolean,reason:String) = run("Keputusan sengketa tersimpan. Status penerimaan dana tetap mengikuti konfirmasi pembeli.") {
        requireAdmin();val d=state.value.adminUi.dispute ?: error("Pilih sengketa dahulu.")
        require(d.text("status")=="terbuka") { "Sengketa sudah diputuskan." }
        require(reason.trim().isNotEmpty() && reason.length<=1000) { "Isi alasan keputusan maksimal 1000 karakter." }
        val result=api.put("admin/disputes/${d.text("id")}/decide",json("decision" to if(approve)"setujui_refund" else "tutup_sengketa","reason" to reason.trim())).asJsonObject
        // Record the acknowledged decision before refreshing supplementary data.
        adminSet {it.copy(dispute=result,decidedDisputes=(listOf(result)+it.decidedDisputes).distinctBy { x->x.text("id") })}
        try {
            adminLoadDisputesSnapshot(0)
            val refund=api.get("admin/refunds/${d.text("refundId")}").asJsonObject;adminSet {it.copy(refund=refund)}
        } catch(e:CancellationException){throw e} catch(_:Exception) {adminSet {it.copy(refund=null)} }
    }
    fun adminLoadEvidence(orderId:String) = run {requireAdmin();set {it.copy(proofs=emptyList())};val rows=api.get("admin/orders/$orderId/payment-proofs").rows();set {it.copy(proofs=rows)}}
    fun adminLoadAudit(page:Int=0) = run {
        requireAdmin();val r=api.get("admin/audit-logs?page=$page&size=50").asJsonObject
        adminSet {it.copy(audit=if(page==0)r.list("items") else (it.audit+r.list("items")).distinctBy { x->x.text("id") },auditPage=page,auditMore=(page+1)*50L<r.long("totalElements"))}
    }

    fun dismiss() = set { it.copy(error = null, message = null, image = null) }
    fun login(email: String, password: String) = run {
        val user = api.login(email, password).user
        mutable.value = ConnectedState(user = user, busy = true)
    }
    fun register(email: String, password: String, name: String, farmer: Boolean) = run("Akun dibuat. Silakan masuk.") {
        api.register(email, password, name, if (farmer) "petani" else "pembeli")
    }
    /** Register with existing API, then create a session and save the profile via existing endpoints. */
    fun registerAccount(input: RegistrationInput) = run {
        api.register(input.email, input.password, input.name, input.role.apiValue, input.phone, input.storeName)
        // Registration can succeed even if login loses the connection: do not falsely report account rollback.
        val session = try { api.login(input.email, input.password) }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                set { it.copy(error = "Akun sudah dibuat. Silakan masuk dengan email dan kata sandi yang baru didaftarkan.") }
                return@run
            }
        var profileError: String? = null
        try {
            api.put("auth/profile", json("fullName" to input.name, "contactPhone" to input.phone,
                "deliveryAddress" to input.address.takeIf { input.role == OpeningRole.BUYER }))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            profileError = "Akun berhasil dibuat dan masuk. Profil belum lengkap; silakan simpan kembali melalui halaman Akun."
        }
        mutable.value = ConnectedState(user = session.user, busy = true, error = profileError)
    }

    fun prepareFarmerAccount(input: RegistrationInput) {
        if (state.value.busy || enrollment.accountCreated) return
        require(input.role == OpeningRole.FARMER)
        farmerDraft = input
        farmerDummyId = newDummyId()
        set { it.copy(farmerOnboarding = FarmerOnboardingState(draftReady = true, email = input.email)) }
    }
    fun submitFarmerRegistration(input: FarmerVerificationInput) = run {
        val account = farmerDraft ?: error("Isi kembali data akun sebelum mengirim verifikasi.")
        try {
            enrollment.submit(account.email, account.password, account.name, account.phone,
                input.storeName, input.location, input.area.split(',').map(String::trim).filter(String::isNotBlank)) {
                upload("petani/ktp/application", input.photoUri,
                    mapOf("dummyName" to "DOKUMEN CONTOH", "dummyNik" to farmerDummyId))
            }
            // Display waiting only after the backend acknowledges a pending application.
            farmerDraft = null
            set { it.copy(farmerOnboarding = FarmerOnboardingState(accountCreated = true, waiting = true, email = account.email)) }
        } catch (e: Exception) {
            set { it.copy(farmerOnboarding = it.farmerOnboarding.copy(accountCreated = enrollment.accountCreated)) }
            throw e
        }
    }
    fun finishFarmerVerification() = run {
        try { api.logout() }
        finally {
            enrollment.clear(); farmerDraft = null
            set { it.copy(farmerOnboarding = FarmerOnboardingState()) }
        }
    }
    fun logout() = run {
        try { api.logout() } finally { mutable.value = ConnectedState() }
    }
    fun catalog(page: Int = 0) = run {
        val path = if (role == "petani") "petani/products" else "buyer/products"
        val result = api.get("$path?page=$page&size=20").asJsonObject
        val rows = result.list("items")
        set { it.copy(products = if (page == 0) rows else (it.products + rows).distinctBy { p -> p.text("id") },
            page = page, more = (page + 1L) * 20 < result.long("totalElements")) }
    }
    /** One serialized snapshot prevents tab loads from being dropped while another request is busy. */
    fun farmerWorkspace() = run {
        require(role == "petani") { "Gunakan akun petani." }
        suspend fun allPages(path: String): List<JsonObject> {
            val rows = mutableListOf<JsonObject>()
            var page = 0
            do {
                val result = api.get("$path?page=$page&size=20").asJsonObject
                val batch = result.list("items")
                rows.addAll(batch)
                page++
            } while (batch.isNotEmpty() && page.toLong() * 20 < result.long("totalElements"))
            return rows
        }
        val products = allPages("petani/products")
        val orders = allPages("petani/orders")
        val profile = api.get("petani/profile").asJsonObject
        val methods = api.get("petani/payment-methods").rows()
        val commodities = api.get("buyer/commodities").rows()
        val proofs = orders.filter { it.text("orderStatus") == "verifikasi_pembayaran" }.associate { order ->
            order.text("orderId") to api.get("petani/orders/${order.text("orderId")}/payment-proofs").rows()
        }
        set { it.copy(products = products, orders = orders, profile = profile, paymentMethods = methods,
            commodities = commodities, farmerOrderProofs = proofs, page = 0, more = false) }
    }
    fun farmerOrder(id: String) = run {
        require(role == "petani") { "Gunakan akun petani." }
        detail(id)
        if (state.value.detail?.text("orderStatus") == "verifikasi_pembayaran") {
            val proofs = api.get("petani/orders/$id/payment-proofs").rows()
            val destination = api.get("petani/orders/$id/payment-destination").asJsonObject
            set { it.copy(proofs = proofs, destination = destination) }
        }
    }
    /** Create a draft before its first photo upload; remember its ID for safe UI retries. */
    fun saveFarmerProduct(id: String?, body: JsonObject, photo: String?, assigned: (String) -> Unit, done: () -> Unit) = run("Produk tersimpan.") {
        require(role == "petani") { "Gunakan akun petani." }
        val productId = id ?: kotlin.run {
            val draft = body.deepCopy().apply { addProperty("publicationStatus", "draft") }
            val created = api.post("petani/products", draft).asJsonObject
            val newId = created.text("id").also { require(it.isNotBlank()) { "ID produk belum diterima." } }
            set { it.copy(products = it.products + created) }
            assigned(newId)
            newId
        }
        if (photo != null) upload("petani/products/$productId/photo", photo, partName = "file")
        val updated = api.put("petani/products/$productId", body).asJsonObject
        set { it.copy(products = it.products.filterNot { p -> p.text("id") == productId } + updated) }
        done()
    }
    fun cart() = run {
        val cart = api.get("buyer/cart").asJsonObject
        set { it.copy(cart = cart, fulfillment = null, quote = null, checkoutBody = null,
            deliveryAreas = emptyList(), shippingEnabled = false) }
        cart.list("items").firstOrNull()?.let { first ->
            val product = api.get("buyer/products/${first.text("productId")}").asJsonObject
            set { it.copy(deliveryAreas = product.getAsJsonArray("serviceArea")?.map { area -> area.asString }.orEmpty(),
                shippingEnabled = product.flag("shippingEnabled")) }
        }
    }
    fun add(product: String, quantity: String) = run("Keranjang tersimpan di server.") {
        api.post("buyer/cart/items", json("productId" to product, "quantityKg" to kg(quantity)))
        invalidateSchedules()
    }
    fun quantity(id: String, quantity: String) = run("Keranjang diperbarui.") {
        api.put("buyer/cart/items/$id", json("quantityKg" to kg(quantity)))
        val cart = api.get("buyer/cart").asJsonObject
        set { it.copy(cart = cart, fulfillment = null, quote = null, checkoutBody = null) }
    }
    fun remove(id: String) = run("Item dihapus.") {
        api.delete("buyer/cart/items/$id")
        val cart = api.get("buyer/cart").asJsonObject
        set { it.copy(cart = cart, fulfillment = null, quote = null, checkoutBody = null) }
    }
    private fun cartItems() = selectedBuyerRows(state.value.cart,state.value.buyerSelectionInitialized,state.value.buyerSelected).map { p -> json("productId" to p.text("productId"), "quantityKg" to p.get("quantityKg")) }
    fun clearQuote() = set { it.copy(quote = null, checkoutBody = null) }
    fun schedules(method: String, district: String) = run {
        val result = api.post("buyer/fulfillment-options", json("items" to cartItems(), "fulfillmentMethod" to method,
            "deliveryDistrict" to district.takeIf { method == "delivery" })).asJsonObject
        set { it.copy(fulfillment = result, quote = null, checkoutBody = null) }
    }
    fun preview(method: String, district: String, address: String, date: String, slot: String, notes: String = "") = run {
        val result = api.post("buyer/checkout/preview", json("items" to cartItems(), "fulfillmentMethod" to method,
            "deliveryDistrict" to district.takeIf { method == "delivery" }, "deliveryAddress" to address.takeIf { method == "delivery" },
            "scheduledDate" to date, "scheduledTimeSlot" to slot, "buyerNotes" to notes.takeIf(String::isNotBlank))).asJsonObject
        val items = result.list("items").map { json("productId" to it.text("productId"), "quantityKg" to it.get("quantityKg"), "expectedPricePerKg" to it.long("pricePerKg")) }
        val body = json("items" to items, "fulfillmentMethod" to method, "scheduledDate" to date, "scheduledTimeSlot" to slot,
            "expectedDeliveryFee" to result.long("deliveryFee"), "deliveryDistrict" to district.takeIf { method == "delivery" },
            "deliveryAddress" to address.takeIf { method == "delivery" }, "expectedFulfillmentToken" to result.text("expectedFulfillmentToken"),
            "expectedCheckoutToken" to result.text("quoteToken"), "buyerNotes" to notes.takeIf(String::isNotBlank))
        set { it.copy(quote = result, checkoutBody = body) }
    }
    private fun key(path: String, payload: String) = keys.key(state.value.user?.id ?: api.session?.user?.id ?: error("Masuk terlebih dahulu."), path, payload)
    private suspend fun keyedPost(path: String, body: JsonObject): JsonElement {
        val result = api.post(path, body, key(path, body.toString()))
        keys.complete(state.value.user!!.id, path, body.toString())
        return result
    }
    fun checkout(done: () -> Unit) = run {
        val body = state.value.checkoutBody ?: error("Periksa total terlebih dahulu.")
        require(state.value.quote?.flag("canCheckout") == true) { "Checkout belum diizinkan server." }
        val order = try { keyedPost("buyer/checkout", body).asJsonObject }
        catch (e: ApiFailure) {
            if (e.status in listOf(400, 409)) {
                invalidateSchedules()
                throw ApiFailure(e.status, "${e.message} Muat ulang keranjang, pilih jadwal dan periksa total kembali sebelum menyetujui.")
            }
            // A lost response may already have created an order. Keep body and key for a safe retry.
            throw e
        }
        set { it.copy(detail = order, quote = null, checkoutBody = null, cart = JsonObject(), fulfillment = null, message = "Pesanan ${order.text("orderId")} dibuat.") }
        done()
    }
    fun orders(page: Int = 0) = run {
        val result = api.get("$role/orders?page=$page&size=20").asJsonObject
        set { it.copy(orders = if (page == 0) result.list("items") else (it.orders + result.list("items")).distinctBy { o -> o.text("orderId") },
            page = page, more = (page + 1L) * 20 < result.long("totalElements")) }
    }
    private suspend fun detail(id: String, loadReview:Boolean = true) {
        val order = api.get("$role/orders/$id").asJsonObject
        set { it.copy(detail = order, options = emptyList(), destination = null, proofs = emptyList(), image = null,
            review = null, reviewLoaded = false) }
        if (loadReview && role == "buyer" && order.text("orderStatus") == "selesai") {
            val review = api.get("buyer/orders/$id/review")
            set { it.copy(review = review.takeIf { value -> value.isJsonObject }?.asJsonObject, reviewLoaded = true) }
        }
    }
    fun review(id: String, rating: Int, comment: String) = run("Ulasan terkirim.") {
        try {
            val review = api.post("buyer/orders/$id/review", json("rating" to rating, "comment" to comment)).asJsonObject
            set { it.copy(review = review, reviewLoaded = true) }
        } catch (e: ApiFailure) {
            if (e.status == 409) {
                val review = api.get("buyer/orders/$id/review")
                if (review.isJsonObject) { set { it.copy(review = review.asJsonObject, reviewLoaded = true) }; return@run }
            }
            throw e
        }
    }
    fun order(id: String) = run { set { it.copy(detail = null) }; detail(id) }
    private suspend fun mutateOrder(id: String, path: String, body: JsonObject, put: Boolean) {
        if (put) api.put(path, body) else api.post(path, body)
        detail(id)
    }
    fun respond(id: String, accepted: Boolean, reason: String) = run("Tanggapan tersimpan.") {
        mutateOrder(id, "petani/orders/$id/respond", json("decision" to if (accepted) "terima" else "tolak", "reason" to reason), true)
    }
    fun fulfill(id: String, status: String, note: String) = run("Status tersimpan.") {
        mutateOrder(id, "petani/orders/$id/fulfillment", json("status" to status, "note" to note), true)
    }
    fun receive(id: String) = run("Penerimaan tersimpan.") { mutateOrder(id, "buyer/orders/$id/receive", json(), false) }
    fun cancel(id: String, reason: String) = run("Pesanan dibatalkan.") { mutateOrder(id, "buyer/orders/$id/cancel", json("reason" to reason), false) }
    fun schedule(id: String, date: String, slot: String, reason: String) = run("Usulan jadwal dikirim.") {
        val version = state.value.detail?.obj("effectiveSchedule")?.long("version") ?: 0
        val body = json("expectedScheduleVersion" to version, "scheduledDate" to date, "scheduledTimeSlot" to slot, "reason" to reason)
        val path = "petani/orders/$id/schedule-changes"
        keyedPost(path, body); detail(id)
    }
    fun decideSchedule(id: String, accepted: Boolean, reason: String) = run("Keputusan jadwal tersimpan.") {
        val proposal = state.value.detail?.obj("pendingScheduleChange") ?: error("Usulan tidak ditemukan.")
        val body = json("expectedScheduleVersion" to proposal.long("baseVersion"), "decision" to if (accepted) "terima" else "tolak", "reason" to reason)
        api.put("buyer/orders/$id/schedule-changes/${proposal.text("id")}/decision", body); detail(id)
    }
    /** One serialized transaction refresh: no concurrent requests dropped by the busy guard. */
    fun buyerTransaction(id: String) = run { loadBuyerTransaction(id) }
    private suspend fun loadBuyerTransaction(id: String) {
        require(role == "buyer")
        set { it.copy(detail = null, buyerTransactionLoaded = false, buyerQris = null) }
        detail(id,false)
        val proofs = api.get("buyer/orders/$id/payment-proofs").rows()
        val order = state.value.detail!!
        val mayPay = order.text("orderStatus") !in setOf("menunggu_petani", "ditolak", "dibatalkan", "kedaluwarsa") && order.obj("pendingScheduleChange").text("id").isBlank() &&
            (order.text("orderStatus") != "menunggu_pembayaran" || runCatching { java.time.Instant.parse(order.text("expiresAt")).isAfter(java.time.Instant.now()) }.getOrDefault(false))
        val options = if (mayPay) api.get("buyer/orders/$id/payment-options").rows() else emptyList()
        val refunds = mutableListOf<JsonObject>()
        var page = 0
        do {
            val result = api.get("buyer/refunds?page=$page&size=100").asJsonObject
            refunds += result.list("items")
            page++
        } while (page * 100L < result.long("totalElements"))
        // Destination may not exist until the buyer chooses one. Only 404 is optional.
        val destination = if (!mayPay) null else try { api.get("buyer/orders/$id/payment-destination").takeIf { it.isJsonObject }?.asJsonObject }
            catch (e: ApiFailure) { if (e.status == 404) null else throw e }
        set { it.copy(proofs = proofs, options = options, refunds = refunds, destination = destination, buyerTransactionLoaded = true) }
    }
    fun buyerProof(id: String, uri: String) = run("Bukti dikirim untuk diperiksa petani.") {
        require(state.value.detail?.let { it.text("orderId") == id && canUploadBuyerProof(it) } == true) { "Unggah bukti belum tersedia. Perbarui status pesanan." }
        upload("buyer/orders/$id/payment-proof", uri)
        loadBuyerTransaction(id)
    }
    fun buyerReceive(id: String) = run("Penerimaan tersimpan.") {
        require(state.value.detail?.let { it.text("orderId") == id && it.text("orderStatus") == "menunggu_penerimaan" } == true) { "Status pesanan belum mengizinkan konfirmasi." }
        api.post("buyer/orders/$id/receive", json())
        loadBuyerTransaction(id)
    }
    fun buyerQris(id: String, method: String) = run {
        val bytes = api.image("buyer/orders/$id/payment-options/$method/qris")
        set { it.copy(buyerQris = bytes, buyerQrisId = method) }
    }
    fun buyerAddresses() = run {
        val user = state.value.user ?: error("Masuk terlebih dahulu.")
        val profile = api.get("auth/profile").asJsonObject
        val rows = buyerAddressStore.seed(user.id, profile)
        set { it.copy(profile = profile, buyerAddresses = rows, buyerAddressId = buyerAddressStore.selected(user.id)) }
    }
    fun saveBuyerAddress(address: SavedBuyerAddress, main: Boolean) = run("Alamat disimpan di perangkat ini.") {
        require(address.label.isNotBlank() && address.recipient.isNotBlank() && address.phone.isNotBlank() && address.address.isNotBlank()) { "Lengkapi data alamat." }
        require(address.snapshot().length <= 2000) { "Alamat terlalu panjang (maksimal 2000 karakter)." }
        val user = state.value.user ?: error("Masuk terlebih dahulu.")
        val rows = (state.value.buyerAddresses.filterNot { it.id == address.id } + address)
        val selected = if (main || state.value.buyerAddressId == null) address.id else state.value.buyerAddressId
        if (selected == address.id) savePrimaryBuyerAddress(address)
        buyerAddressStore.write(user.id, rows, selected)
        invalidateSchedules()
        set { it.copy(buyerAddresses = rows, buyerAddressId = selected) }
    }
    private suspend fun savePrimaryBuyerAddress(address: SavedBuyerAddress) {
        val owner = api.get("auth/profile").asJsonObject
        val profile = api.put("auth/profile", json("fullName" to owner.text("fullName"), "contactPhone" to owner.text("contactPhone"), "deliveryAddress" to address.address)).asJsonObject
        set { it.copy(profile = profile) }
    }
    fun selectBuyerAddress(id: String) = run {
        val user = state.value.user ?: error("Masuk terlebih dahulu.")
        val address = state.value.buyerAddresses.firstOrNull { it.id == id } ?: error("Alamat tidak ditemukan.")
        savePrimaryBuyerAddress(address)
        buyerAddressStore.write(user.id, state.value.buyerAddresses, id)
        invalidateSchedules()
        set { it.copy(buyerAddressId = id) }
    }
    fun buyerReview(id: String) = run { loadBuyerReview(id) }
    private suspend fun loadBuyerReview(id: String) {
        set { it.copy(buyerReview=null,buyerReviewLoaded=false,buyerReviewAvailable=true) }
        try {
            val result=api.get("buyer/orders/$id/review")
            set { it.copy(buyerReview=result.takeIf{v->v.isJsonObject}?.asJsonObject,buyerReviewLoaded=true) }
        } catch(e: ApiFailure) {
            // This endpoint exists on newer team backends. A missing route must not become fake success.
            if(e.status !in setOf(404,405)) throw e
            set { it.copy(buyerReviewLoaded=true,buyerReviewAvailable=false) }
        }
    }
    fun buyerSubmitReview(id:String,rating:Int,tags:Set<String>,comment:String) = run("Ulasan berhasil dikirim.") {
        require(state.value.detail?.let{it.text("orderId")==id && it.text("orderStatus")=="selesai"}==true){"Ulasan hanya untuk pesanan selesai."}
        require(state.value.buyerReviewLoaded && state.value.buyerReviewAvailable && state.value.buyerReview==null){"Perbarui status ulasan terlebih dahulu."}
        val text=buyerReviewComment(tags,comment)
        require(rating in 1..5 && text.length<=500){"Pilih 1–5 bintang dan batasi komentar hingga 500 karakter."}
        try {
            val review=api.post("buyer/orders/$id/review",json("rating" to rating,"comment" to text.takeIf(String::isNotBlank))).asJsonObject
            set { it.copy(buyerReview=review) }
        } catch(e:ApiFailure) {
            if(e.status in setOf(404,405)){set{it.copy(buyerReviewAvailable=false)};error("Layanan ulasan belum tersedia pada backend ini.")}
            if(e.status==409){loadBuyerReview(id);if(state.value.buyerReview!=null)return@run}
            throw e
        }
    }
    fun buyerCompleted(id:String,includeDestination:Boolean=true)=run {
        set{it.copy(detail=null,buyerTransactionLoaded=false)};detail(id,false);loadBuyerReview(id)
        if(includeDestination){
            val destination=try{api.get("buyer/orders/$id/payment-destination").takeIf{it.isJsonObject}?.asJsonObject}catch(e:ApiFailure){if(e.status==404)null else throw e}
            set{it.copy(destination=destination)}
        }
    }
    fun buyerSaveIdentity(name:String,phone:String)=run("Profil berhasil disimpan.") {
        require(name.isNotBlank() && name.length<=255 && phone.length<=20){"Periksa nama dan nomor telepon."}
        val current=api.get("auth/profile").asJsonObject
        val saved=api.put("auth/profile",json("fullName" to name.trim(),"contactPhone" to phone.trim(),"deliveryAddress" to current.get("deliveryAddress"))).asJsonObject
        set{it.copy(profile=saved)}
    }
    fun buyerRefundDetail(id:String)=run { loadBuyerRefund(id) }
    private suspend fun loadBuyerRefund(id:String) {
        set{it.copy(buyerRefund=null,buyerTicket=null,buyerTicketId=null,buyerTicketDraft=JsonObject())}
        val refund=api.get("buyer/refunds/$id").asJsonObject
        val user=state.value.user!!.id
        set{it.copy(buyerRefund=refund,buyerTicketId=buyerHelpStore.ticket(user,id),buyerTicketPendingReason=buyerHelpStore.pendingReason(user,id))}
    }
    fun buyerConfirmRefund(id:String)=run("Dana dikonfirmasi diterima. Refund selesai.") {
        require(state.value.buyerRefund?.let{it.text("id")==id && canConfirmBuyerRefund(it)}==true){"Refund belum menunggu konfirmasi dana."}
        api.post("buyer/refunds/$id/confirm",json());loadBuyerRefund(id)
    }
    fun buyerCreateTicket(refundId:String,reason:String,done:(String)->Unit)=run {
        val user=state.value.user!!.id
        val pending=buyerHelpStore.pendingReason(user,refundId)
        val text=pending ?: reason.trim()
        require(text.isNotBlank() && text.length<=1000){"Jelaskan masalah maksimal 1000 karakter."}
        val refund=state.value.buyerRefund ?: error("Muat detail refund terlebih dahulu.")
        require(refund.text("id")==refundId && (pending!=null || canEscalateBuyerRefund(refund))){"Status refund tidak mengizinkan eskalasi baru."}
        buyerHelpStore.pending(user,refundId,text)
        set{it.copy(buyerTicketPendingReason=text)}
        val path="buyer/refunds/$refundId/escalations";val body=json("reason" to text)
        val ticket=api.post(path,body,key(path,body.toString())).asJsonObject
        require(ticket.text("id").isNotBlank()){ "Tiket belum dikonfirmasi server. Coba lagi dengan alasan yang sama." }
        buyerHelpStore.rememberTicket(user,refundId,ticket.text("id"))
        buyerHelpStore.pending(user,refundId,null)
        keys.complete(user,path,body.toString())
        set{it.copy(buyerTicket=ticket,buyerTicketId=ticket.text("id"),buyerTicketPendingReason=null)}
        done(ticket.text("id"))
    }
    fun buyerTicket(id:String)=run {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,128}"))){"ID tiket tidak valid."}
        set{it.copy(buyerTicket=null,buyerTicketDraft=JsonObject())}
        val ticket=api.get("buyer/escalations/$id").asJsonObject
        val refund=api.get("buyer/refunds/${ticket.text("refundId")}").asJsonObject
        val user=state.value.user!!.id
        buyerHelpStore.rememberTicket(user,refund.text("id"),id)
        set{it.copy(buyerTicket=ticket,buyerRefund=refund,buyerTicketId=id,buyerTicketDraft=buyerHelpStore.draft(user,id))}
    }
    fun buyerTicketNote(id:String,note:String,uri:String?,name:String)=run("Catatan tambahan disimpan di perangkat. Bagikan ke CS melalui WhatsApp.") {
        require(state.value.buyerTicket?.text("id")==id){"Muat tiket terlebih dahulu."}
        require(note.length<=1000){"Catatan maksimal 1000 karakter."}
        val user=state.value.user!!.id
        buyerHelpStore.saveDraft(user,id,note,uri,name)
        set{it.copy(buyerTicketDraft=buyerHelpStore.draft(user,id))}
    }
    fun buyerStore(productId:String)=run {
        set{it.copy(buyerStore=null,buyerStoreProducts=emptyList(),buyerStoreReviews=emptyList())}
        val detail=api.get("buyer/products/$productId").asJsonObject
        val owner=detail.obj("product").text("farmerId")
        require(owner.isNotBlank()){ "Identitas toko belum tersedia." }
        val rows=mutableListOf<JsonObject>();var page=0
        do {
            val result=api.get("buyer/products?page=$page&size=100").asJsonObject
            rows+=result.list("items").filter{it.text("farmerId")==owner};page++
        } while(page*100L<result.long("totalElements"))
        set{it.copy(buyerStore=detail,buyerStoreProducts=(rows+listOf(detail.obj("product"))).distinctBy{p->p.text("id")})}
    }
    /** Absolute targets persist across lost responses, so retrying a reorder does not double quantities. */
    fun buyerReorder(id:String,done:()->Unit)=run("Produk ditambahkan ke keranjang dengan harga saat ini.") {
        val user=state.value.user!!.id
        var plan=buyerHelpStore.reorder(user,id)
        if(plan==null){
            val order=api.get("buyer/orders/$id").asJsonObject
            require(order.text("orderStatus")=="selesai"){"Beli lagi tersedia untuk pesanan selesai."}
            val cart=api.get("buyer/cart").asJsonObject
            val owner=order.text("farmerId")
            require(cart.list("items").isEmpty() || cart.list("items").all{it.text("farmerId")==owner}){"Keranjang berisi produk toko lain. Periksa keranjang terlebih dahulu."}
            val targets=order.list("items").map{item->
                val product=api.get("buyer/products/${item.text("productId")}").asJsonObject.obj("product")
                val previous=cart.list("items").firstOrNull{it.text("productId")==item.text("productId")}?.text("quantityKg")?.toBigDecimalOrNull()?:java.math.BigDecimal.ZERO
                val target=previous+(item.text("quantityKg").toBigDecimalOrNull()?:error("Jumlah produk tidak valid."))
                require(product.flag("availableForPurchase") && (product.text("stockAvailable").toBigDecimalOrNull()?:java.math.BigDecimal.ZERO)>=target){"${item.text("productNameSnapshot")} belum tersedia dalam jumlah yang diminta. Keranjang tidak diubah."}
                json("productId" to item.text("productId"),"target" to target)
            }
            require(targets.isNotEmpty()){ "Pesanan tidak memiliki produk." }
            plan=json("items" to targets);buyerHelpStore.reorder(user,id,plan)
        }
        try {
            for(item in plan.list("items")){
                val cart=api.get("buyer/cart").asJsonObject
                val current=cart.list("items").firstOrNull{it.text("productId")==item.text("productId")}
                val target=item.text("target").toBigDecimal()
                val quantity=current?.text("quantityKg")?.toBigDecimalOrNull()?:java.math.BigDecimal.ZERO
                if(quantity<target){
                    val updated=if(current!=null)api.put("buyer/cart/items/${current.text("id")}",json("quantityKg" to target))
                        else api.post("buyer/cart/items",json("productId" to item.text("productId"),"quantityKg" to target))
                    set{it.copy(cart=updated.asJsonObject)}
                } else set{it.copy(cart=cart)}
            }
        }catch(e:CancellationException){throw e}catch(e:Exception){throw java.io.IOException("Periksa keranjang sebelum mencoba lagi. Sebagian produk mungkin sudah ditambahkan. ${e.message}",e)}
        buyerHelpStore.reorder(user,id,null);invalidateSchedules();done()
    }
    fun payments(id: String) = run {
        if (role == "buyer") {
            val options = api.get("buyer/orders/$id/payment-options").rows()
            set { it.copy(options = options) }
        } else {
            val proofs = api.get("petani/orders/$id/payment-proofs").rows()
            val destination = api.get("petani/orders/$id/payment-destination").asJsonObject
            set { it.copy(proofs = proofs, destination = destination) }
        }
    }
    fun choosePayment(id: String, method: String) = run {
        val destination = api.put("buyer/orders/$id/payment-destination", json("paymentMethodId" to method)).asJsonObject
        set { it.copy(destination = destination) }
    }
    fun verify(id: String, submission: String, accepted: Boolean, reason: String) = run("Pemeriksaan pembayaran tersimpan.") {
        api.put("petani/orders/$id/payment-verify", json("submissionId" to submission, "decision" to if (accepted) "terima" else "tolak", "reason" to reason))
        detail(id)
    }
    private suspend fun upload(path: String, uri: String, fields: Map<String, String> = emptyMap(), partName: String = "image",
        put: Boolean = false): JsonElement {
        val (bytes, mime) = withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val parsed = Uri.parse(uri)
            require(parsed.scheme == "content") { "Pilih gambar dari perangkat." }
            val mime = resolver.getType(parsed) ?: error("Jenis gambar tidak diketahui.")
            val bytes = resolver.openInputStream(parsed)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 5 * 1024 * 1024) { val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
                output.toByteArray() } ?: error("Gambar tidak dapat dibaca.")
            bytes to mime
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val payload = json("fields" to fields, "imageHash" to digest).toString()
        val result = api.upload(path, bytes, mime, fields, key(path, payload), partName, put)
        keys.complete(state.value.user?.id ?: api.session!!.user.id, path, payload)
        return result
    }
    fun proof(id: String, uri: String) = run("Bukti dikirim untuk diperiksa petani.") { upload("buyer/orders/$id/payment-proof", uri); detail(id) }
    fun image(path: String) = run { val image = api.image(path); set { it.copy(image = image) } }
    fun refunds(page: Int = 0) = run {
        val result = api.get("$role/refunds?page=$page&size=20").asJsonObject
        set { it.copy(refunds = if (page == 0) result.list("items") else (it.refunds + result.list("items")).distinctBy { r -> r.text("id") },
            page = page, more = (page + 1L) * 20 < result.long("totalElements")) }
    }
    fun refund(id: String, reason: String, bank: String, account: String, name: String, uri: String, done: () -> Unit) = run("Pengajuan refund dikirim.") {
        upload("buyer/refunds", uri, mapOf("orderId" to id, "reason" to reason, "destinationType" to "bank", "destinationProvider" to bank,
            "destinationAccount" to account, "destinationName" to name)); done()
    }
    fun refundDecision(id: String, accepted: Boolean, reason: String) = run("Tanggapan refund tersimpan.") {
        api.put("petani/refunds/$id/respond", json("decision" to if (accepted) "terima" else "tolak", "reason" to reason))
        val rows = api.get("$role/refunds").rows(); set { it.copy(refunds = rows) }
    }
    fun refundTransfer(id: String, uri: String) = run("Bukti transfer balik tersimpan.") {
        upload("petani/refunds/$id/transfer-proof", uri)
        val rows = api.get("$role/refunds").rows(); set { it.copy(refunds = rows) }
    }
    fun refundConfirm(id: String) = run("Dana dikonfirmasi diterima.") {
        api.post("buyer/refunds/$id/confirm")
        val rows = api.get("buyer/refunds").rows(); set { it.copy(refunds = rows) }
    }
    fun escalate(id: String, reason: String) = run("Tiket eskalasi dikirim ke admin.") {
        val body = json("reason" to reason); val path = "buyer/refunds/$id/escalations"
        keyedPost(path, body)
        val rows = api.get("buyer/refunds?size=20").rows(); set { it.copy(refunds = rows) }
    }
    fun invalidateSchedules() = set { it.copy(fulfillment = null, quote = null, checkoutBody = null) }
    fun notifications() = run {
        val result = api.get("notifications?size=50").asJsonObject
        set { it.copy(notifications = result.list("items"), unreadNotificationCount = result.long("unreadCount")) }
    }
    fun readNotification(id: String) = run {
        api.put("notifications/$id/read", json())
        val result = api.get("notifications?size=50").asJsonObject
        set { it.copy(notifications = result.list("items"), unreadNotificationCount = result.long("unreadCount")) }
    }
    fun readAllNotifications() = run("Semua notifikasi telah ditandai dibaca.") {
        api.put("notifications/read-all", json())
        val result = api.get("notifications?size=50").asJsonObject
        set { it.copy(notifications = result.list("items"), unreadNotificationCount = result.long("unreadCount")) }
    }
    fun profile() = run {
        val result = api.get(if (role == "petani") "petani/profile" else "auth/profile").asJsonObject
        val methods = if (role == "petani") api.get("petani/payment-methods").rows() else state.value.paymentMethods
        set { it.copy(profile = result, paymentMethods = methods) }
    }
    fun saveProfile(body: JsonObject) = run("Profil tersimpan.") {
        val result = api.put(if (role == "petani") "petani/profile" else "auth/profile", body).asJsonObject
        set { it.copy(profile = result) }
    }
    fun commodities() = run {
        val result = api.get("buyer/commodities").rows(); set { it.copy(commodities = result) }
        if (role == "petani") {
            val profile = api.get("petani/profile").asJsonObject
            val methods = api.get("petani/payment-methods").rows()
            set { it.copy(profile = profile, paymentMethods = methods) }
        }
    }
    fun adminCommodities() = run {
        val rows = api.get("admin/commodities").rows(); set { it.copy(commodities = rows) }
    }
    fun saveCommodity(id: String?, name: String, active: Boolean, done: () -> Unit) = run("Komoditas tersimpan.") {
        val body = json("name" to name.trim(), "isActive" to active)
        if (id == null) api.post("admin/commodities", body) else api.put("admin/commodities/$id", body)
        val rows = api.get("admin/commodities").rows(); set { it.copy(commodities = rows) }; done()
    }
    fun saveProduct(id: String?, body: JsonObject, done: () -> Unit) = run("Produk tersimpan.") {
        if (id == null) api.post("petani/products", body) else api.put("petani/products/$id", body)
        done()
    }
    fun bank(bank: String, account: String, name: String) = run("Rekening tersimpan.") {
        val body = json("bankName" to bank, "accountNumber" to account, "accountHolderName" to name)
        val path = "petani/payment-methods/bank"
        keyedPost(path, body); refreshPaymentMethods()
    }
    private suspend fun refreshPaymentMethods() {
        val methods = api.get("petani/payment-methods").rows()
        set { it.copy(paymentMethods = methods) }
    }
    fun paymentMethods() = run { refreshPaymentMethods() }
    fun bankDetail(id: String) = run {
        val detail = api.get("petani/payment-methods/$id/bank").asJsonObject
        set { it.copy(paymentMethodDetail = detail) }
    }
    fun clearPaymentMethodDetail() = set { it.copy(paymentMethodDetail = null) }
    fun saveBank(id: String?, bank: String, account: String, name: String, done: () -> Unit) = run("Rekening tersimpan.") {
        val body = json("bankName" to bank.trim(), "accountNumber" to account.trim(), "accountHolderName" to name.trim())
        if (id == null) keyedPost("petani/payment-methods/bank", body)
        else api.put("petani/payment-methods/$id/bank", body)
        refreshPaymentMethods(); set { it.copy(paymentMethodDetail = null) }; done()
    }
    fun deleteBank(id: String) = run("Rekening dihapus dari pembayaran baru.") {
        api.delete("petani/payment-methods/$id/bank"); refreshPaymentMethods(); set { it.copy(paymentMethodDetail = null) }
    }
    fun saveQris(id: String?, holder: String, uri: String, done: () -> Unit) = run("QRIS tersimpan.") {
        val path = if (id == null) "petani/payment-methods/qris" else "petani/payment-methods/$id/qris"
        upload(path, uri, mapOf("accountHolderName" to holder.trim()), put = id != null)
        refreshPaymentMethods(); done()
    }
    fun deleteQris(id: String) = run("QRIS dihapus dari pembayaran baru.") {
        api.delete("petani/payment-methods/$id/qris"); refreshPaymentMethods()
    }
    private fun query(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    fun adminQueue(status: String = "menunggu", search: String = "") = run {
        val rows = api.get("admin/farmers/verification-queue?size=50&status=${query(status)}&search=${query(search.trim())}").rows()
        set { it.copy(products = rows) }
    }
    fun verifyFarmer(id: String, approved: Boolean, reason: String) = run("Keputusan verifikasi tersimpan.") {
        api.put("admin/farmers/$id/verify", json("approved" to approved, "rejectionReason" to reason))
        val rows = api.get("admin/farmers/verification-queue").rows(); set { it.copy(products = rows) }
    }

    fun adminUsers(role: String? = null, status: String? = null, search: String = "") = run {
        val filters = buildList {
            add("size=50")
            role?.takeIf(String::isNotBlank)?.let { add("role=${query(it)}") }
            status?.takeIf(String::isNotBlank)?.let { add("status=${query(it)}") }
            if (search.isNotBlank()) add("search=${query(search.trim())}")
        }.joinToString("&")
        val rows = api.get("admin/users?$filters").rows()
        set { it.copy(adminUsers = rows, adminUserDetail = null) }
    }
    fun adminUser(id: String) = run {
        val detail = api.get("admin/users/$id").asJsonObject
        set { it.copy(adminUserDetail = detail) }
    }
    fun suspendUser(id: String, suspend: Boolean, reason: String) = run(if (suspend) "Akun ditangguhkan." else "Akun dipulihkan.") {
        api.put("admin/users/$id/suspend", json("suspend" to suspend, "reason" to reason.trim().takeIf(String::isNotBlank)))
        val detail = api.get("admin/users/$id").asJsonObject
        val users = api.get("admin/users?size=50").rows()
        set { it.copy(adminUserDetail = detail, adminUsers = users) }
    }
    fun adminSettings() = run {
        val settings = api.get("admin/settings").asJsonObject
        set { it.copy(settings = settings) }
    }
    fun saveAdminSettings(limit: Long, whatsapp: String, reason: String) = run("Pengaturan admin tersimpan.") {
        val current = state.value.settings ?: error("Muat pengaturan terlebih dahulu.")
        val body = json(
            "newLimit" to limit, "csWhatsapp" to whatsapp.trim(),
            "expectedTransactionLimit" to current.long("transactionLimit"),
            "reason" to reason.trim().takeIf(String::isNotBlank)
        )
        body.add("expectedCsWhatsapp", current.get("csWhatsapp") ?: JsonNull.INSTANCE)
        val settings = api.put("admin/settings", body).asJsonObject
        set { it.copy(settings = settings) }
    }
    fun auditLogs(action: String = "") = run {
        val suffix = action.trim().takeIf(String::isNotBlank)?.let { "&action=${query(it)}" }.orEmpty()
        val rows = api.get("admin/audit-logs?size=50$suffix").rows()
        set { it.copy(auditLogs = rows) }
    }
    fun disputes() = run {
        val rows = api.get("admin/disputes?size=50").rows()
        set { it.copy(disputes = rows) }
    }
    fun decideDispute(id: String, approve: Boolean, reason: String) = run("Keputusan sengketa tersimpan.") {
        api.put("admin/disputes/$id/decide", json("decision" to if (approve) "setujui_refund" else "tutup_sengketa", "reason" to reason.trim()))
        val rows = api.get("admin/disputes?size=50").rows(); set { it.copy(disputes = rows) }
    }
    fun latePayments(admin: Boolean) = run {
        val prefix = if (admin) "admin" else "buyer"
        val rows = api.get("$prefix/late-payment-reports?size=50").rows()
        set { it.copy(latePayments = rows) }
    }
    fun reportLatePayment(orderId: String, reason: String, amount: Long, uri: String) = run("Laporan pembayaran terlambat dikirim.") {
        upload("buyer/late-payment-reports", uri, mapOf("orderId" to orderId, "reason" to reason.trim(), "claimedAmount" to amount.toString()))
        val rows = api.get("buyer/late-payment-reports?size=50").rows(); set { it.copy(latePayments = rows) }
    }
    fun reviewLatePayment(id: String, followUp: Boolean, reason: String) = run("Pemeriksaan laporan tersimpan.") {
        api.put("admin/late-payment-reports/$id/review", json("decision" to if (followUp) "tindak_lanjut" else "bukti_tidak_cukup", "reason" to reason.trim()))
        val rows = api.get("admin/late-payment-reports?size=50").rows(); set { it.copy(latePayments = rows) }
    }
    fun closeLatePayment(id: String, reason: String) = run("Laporan ditutup.") {
        api.put("admin/late-payment-reports/$id/close", json("reason" to reason.trim()))
        val rows = api.get("admin/late-payment-reports?size=50").rows(); set { it.copy(latePayments = rows) }
    }

    fun productPhoto(id: String, uri: String) = run("Foto produk tersimpan.") {
        upload("petani/products/$id/photo", uri, partName = "file")
        val updated = api.get("petani/products/$id").asJsonObject
        set { it.copy(products = it.products.map { product -> if (product.text("id") == id) updated else product }) }
    }
    fun saveFarmerIdentity(owner: String, phone: String, store: String, done: () -> Unit) = run("Profil tersimpan.") {
        require(role == "petani") { "Gunakan akun petani." }
        val current = api.get("petani/profile").asJsonObject
        val account = api.put("auth/profile", json("fullName" to owner, "contactPhone" to phone.takeIf(String::isNotBlank))).asJsonObject
        val partial = current.deepCopy().apply { addProperty("ownerName", account.text("fullName")); addProperty("contactPhone", account.text("contactPhone")) }
        set { it.copy(profile = partial) }
        val body = id.panenku.app.ui.connected.farmer.farmerProfileBody(current, store = store)
        val profile = api.put("petani/profile", body).asJsonObject
        set { it.copy(profile = profile) }; done()
    }
    fun farmerQris(id: String) = run {
        require(role == "petani") { "Gunakan akun petani." }
        set { it.copy(farmerQrisId = id, farmerQrisImage = null) }
        val bytes = api.image("petani/payment-methods/$id/qris")
        set { it.copy(farmerQrisImage = bytes) }
    }
    fun farmerVerification() = run {
        val status = api.get("petani/ktp/status").asJsonObject
        set { it.copy(farmerVerification = status) }
    }
    fun farmerResubmitKtp(uri: String) = run("Dokumen contoh dikirim ulang untuk verifikasi.") {
        require(role == "petani") { "Gunakan akun petani." }
        val current = state.value.farmerVerification ?: error("Muat status verifikasi terlebih dahulu.")
        require(current.text("status") in listOf("ditolak", "belum_diajukan")) { "Pengajuan tidak sedang memerlukan perbaikan." }
        val name = current.text("dummyName").ifBlank { state.value.profile?.text("ownerName").orEmpty() }
        require(name.isNotBlank()) { "Lengkapi nama pemilik terlebih dahulu." }
        val nik = current.text("dummyNik").takeIf { Regex("DUMMY-[A-Z0-9]{3,32}").matches(it) } ?: farmerDummyId
        val status = upload("petani/ktp/application", uri, mapOf("dummyName" to name, "dummyNik" to nik)).asJsonObject
        set { it.copy(farmerVerification = status) }
        val profile = api.get("petani/profile").asJsonObject; set { it.copy(profile = profile) }
    }
    fun farmerRefundDetail(id: String) = run {
        set { it.copy(farmerRefund = null, farmerRefundOrder = null) }
        val refund = api.get("petani/refunds/$id").asJsonObject
        set { it.copy(farmerRefund = refund) }
        val order = api.get("petani/orders/${refund.text("orderId")}").asJsonObject
        set { it.copy(farmerRefundOrder = order) }
    }
    private fun updateFarmerRefund(refund: JsonObject) = set { state -> state.copy(farmerRefund = refund,
        refunds = state.refunds.map { if (it.text("id") == refund.text("id")) refund else it }) }
    fun farmerRefundDecision(id: String, accepted: Boolean, reason: String, done: () -> Unit) = run("Tanggapan refund tersimpan.") {
        require(accepted || reason.isNotBlank()) { "Alasan penolakan wajib diisi." }
        val refund = api.put("petani/refunds/$id/respond", json("decision" to if(accepted) "terima" else "tolak", "reason" to reason.trim())).asJsonObject
        updateFarmerRefund(refund); done()
    }
    fun farmerRefundTransfer(id: String, uri: String) = run("Bukti transfer balik tersimpan. Menunggu konfirmasi pembeli.") {
        val refund = upload("petani/refunds/$id/transfer-proof", uri).asJsonObject
        updateFarmerRefund(refund)
    }

    fun ktp(name: String, nik: String, uri: String) = run("Dokumen pengembangan dikirim untuk verifikasi.") {
        upload("petani/ktp/application", uri, mapOf("dummyName" to name, "dummyNik" to nik))
        val profile = api.get("petani/profile").asJsonObject; set { it.copy(profile = profile) }
    }

}
