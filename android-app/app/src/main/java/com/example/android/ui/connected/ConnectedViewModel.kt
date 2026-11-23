package id.panenku.app.ui.connected

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.*
import id.panenku.app.BuildConfig
import id.panenku.app.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import id.panenku.app.ui.connected.buyer.*
import java.security.MessageDigest

/** All active screens use this state; legacy SampleData is never copied into it. */
data class ConnectedState(
    val user: ApiUser? = null, val busy: Boolean = false, val error: String? = null, val message: String? = null,
    val products: List<JsonObject> = emptyList(), val cart: JsonObject = JsonObject(),
    val orders: List<JsonObject> = emptyList(), val refunds: List<JsonObject> = emptyList(),
    val commodities: List<JsonObject> = emptyList(), val detail: JsonObject? = null,
    val options: List<JsonObject> = emptyList(), val destination: JsonObject? = null,
    val proofs: List<JsonObject> = emptyList(), val fulfillment: JsonObject? = null,
    val quote: JsonObject? = null, val checkoutBody: JsonObject? = null,
    val image: ByteArray? = null, val profile: JsonObject? = null,
    val page: Int = 0, val more: Boolean = false,
    val deliveryAreas: List<String> = emptyList(), val shippingEnabled: Boolean = false,
    val buyerAddresses: List<SavedBuyerAddress> = emptyList(), val buyerAddressId: String? = null,
    val buyerTransactionLoaded: Boolean = false, val buyerQris: ByteArray? = null, val buyerQrisId: String? = null
)
class ConnectedViewModel @JvmOverloads constructor(app: Application,
    private val api: PanenkuApi = PanenkuApi(BuildConfig.API_BASE_URL)) : AndroidViewModel(app) {
    private val buyerAddressStore = BuyerAddressStore(app)
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
    fun dismiss() = set { it.copy(error = null, message = null, image = null) }
    fun login(email: String, password: String) = run {
        val user = api.login(email, password).user
        mutable.value = ConnectedState(user = user, busy = true)
    }
    fun register(email: String, password: String, name: String, farmer: Boolean) = run("Akun dibuat. Silakan masuk.") {
        api.register(email, password, name, if (farmer) "petani" else "pembeli")
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
    private fun cartItems() = state.value.cart.list("items").map { p -> json("productId" to p.text("productId"), "quantityKg" to p.get("quantityKg")) }
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
    private fun key(path: String, payload: String) = keys.key(state.value.user?.id ?: error("Masuk terlebih dahulu."), path, payload)
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
    private suspend fun detail(id: String) {
        val order = api.get("$role/orders/$id").asJsonObject
        set { it.copy(detail = order, options = emptyList(), destination = null, proofs = emptyList(), image = null) }
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
        detail(id)
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
    private suspend fun upload(path: String, uri: String, fields: Map<String, String> = emptyMap(), partName: String = "image") {
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
        api.upload(path, bytes, mime, fields, key(path, payload), partName)
        keys.complete(state.value.user!!.id, path, payload)
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
    }
    fun invalidateSchedules() = set { it.copy(fulfillment = null, quote = null, checkoutBody = null) }
    fun profile() = run {
        val result = api.get(if (role == "petani") "petani/profile" else "auth/profile").asJsonObject
        set { it.copy(profile = result) }
    }
    fun saveProfile(body: JsonObject) = run("Profil tersimpan.") {
        val result = api.put(if (role == "petani") "petani/profile" else "auth/profile", body).asJsonObject
        set { it.copy(profile = result) }
    }
    fun commodities() = run {
        val result = api.get("buyer/commodities").rows(); set { it.copy(commodities = result) }
    }
    fun saveProduct(id: String?, body: JsonObject, done: () -> Unit) = run("Produk tersimpan.") {
        if (id == null) api.post("petani/products", body) else api.put("petani/products/$id", body)
        done()
    }
    fun bank(bank: String, account: String, name: String) = run("Rekening tersimpan.") {
        val body = json("bankName" to bank, "accountNumber" to account, "accountHolderName" to name)
        val path = "petani/payment-methods/bank"
        keyedPost(path, body)
    }
    fun adminQueue() = run {
        val rows = api.get("admin/farmers/verification-queue").rows()
        set { it.copy(products = rows) }
    }
    fun verifyFarmer(id: String, approved: Boolean, reason: String) = run("Keputusan verifikasi tersimpan.") {
        api.put("admin/farmers/$id/verify", json("approved" to approved, "rejectionReason" to reason))
        val rows = api.get("admin/farmers/verification-queue").rows(); set { it.copy(products = rows) }
    }

    fun productPhoto(id: String, uri: String) = run("Foto produk tersimpan.") {
        upload("petani/products/$id/photo", uri, partName = "file")
    }
    fun ktp(name: String, nik: String, uri: String) = run("Dokumen pengembangan dikirim untuk verifikasi.") {
        upload("petani/ktp/application", uri, mapOf("dummyName" to name, "dummyNik" to nik))
        val profile = api.get("petani/profile").asJsonObject; set { it.copy(profile = profile) }
    }

}
