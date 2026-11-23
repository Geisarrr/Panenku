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
import java.security.MessageDigest
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** All active screens use this state; legacy SampleData is never copied into it. */
data class ConnectedState(
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
    val farmerOrderProofs: Map<String, List<JsonObject>> = emptyMap(),
    val farmerVerification: JsonObject? = null,
    val farmerRefund: JsonObject? = null, val farmerRefundOrder: JsonObject? = null,
    val farmerQrisId: String? = null, val farmerQrisImage: ByteArray? = null,
    val farmerOnboarding: FarmerOnboardingState = FarmerOnboardingState()
)
class ConnectedViewModel @JvmOverloads constructor(app: Application,
    private val api: PanenkuApi = PanenkuApi(BuildConfig.API_BASE_URL)) : AndroidViewModel(app) {
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
    private fun cartItems() = state.value.cart.list("items").map { p -> json("productId" to p.text("productId"), "quantityKg" to p.get("quantityKg")) }
    fun clearQuote() = set { it.copy(quote = null, checkoutBody = null) }
    fun schedules(method: String, district: String) = run {
        val result = api.post("buyer/fulfillment-options", json("items" to cartItems(), "fulfillmentMethod" to method,
            "deliveryDistrict" to district.takeIf { method == "delivery" })).asJsonObject
        set { it.copy(fulfillment = result, quote = null, checkoutBody = null) }
    }
    fun preview(method: String, district: String, address: String, date: String, slot: String) = run {
        val result = api.post("buyer/checkout/preview", json("items" to cartItems(), "fulfillmentMethod" to method,
            "deliveryDistrict" to district.takeIf { method == "delivery" }, "deliveryAddress" to address.takeIf { method == "delivery" },
            "scheduledDate" to date, "scheduledTimeSlot" to slot)).asJsonObject
        val items = result.list("items").map { json("productId" to it.text("productId"), "quantityKg" to it.get("quantityKg"), "expectedPricePerKg" to it.long("pricePerKg")) }
        val body = json("items" to items, "fulfillmentMethod" to method, "scheduledDate" to date, "scheduledTimeSlot" to slot,
            "expectedDeliveryFee" to result.long("deliveryFee"), "deliveryDistrict" to district.takeIf { method == "delivery" },
            "deliveryAddress" to address.takeIf { method == "delivery" }, "expectedFulfillmentToken" to result.text("expectedFulfillmentToken"),
            "expectedCheckoutToken" to result.text("quoteToken"))
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
    private suspend fun detail(id: String) {
        val order = api.get("$role/orders/$id").asJsonObject
        set { it.copy(detail = order, options = emptyList(), destination = null, proofs = emptyList(), image = null,
            review = null, reviewLoaded = false) }
        if (role == "buyer" && order.text("orderStatus") == "selesai") {
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
    fun choosePayment(id: String, method: String) = run("Tujuan pembayaran dipilih.") {
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
