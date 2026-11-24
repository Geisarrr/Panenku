package id.panenku.app.data.buyer

import id.panenku.app.features.f2_stock_reservation.StockReservations

import id.panenku.app.data.*
import id.panenku.app.features.f3_delivery_schedule.api.F3BuyerOperations
import id.panenku.app.features.f4_checkout.api.F4BuyerOperations
import id.panenku.app.features.f5_order_status.api.F5BuyerOperations
import id.panenku.app.features.f6_payment_verification.api.F6BuyerOperations
import id.panenku.app.features.f7_refund.api.F7BuyerOperations
import kotlinx.coroutines.flow.*
import java.time.*

interface BuyerRepository : F3BuyerOperations, F4BuyerOperations, F5BuyerOperations, F6BuyerOperations, F7BuyerOperations {
    val data: StateFlow<BuyerData>
    val now: Instant
    suspend fun saveProfile(profile: BuyerProfile)
}
/** Jam tetap hanya untuk demo sesuai tanggal pada desain; backend harus menyediakan waktu server. */
class DemoBuyerRepository(private val clock: Clock = BuyerSamples.clock, initialData: BuyerData = BuyerSamples.initial) : BuyerRepository {
    private val mutableData = MutableStateFlow(initialData)
    override val data = mutableData.asStateFlow()
    override val now: Instant get() = clock.instant()
    private fun order(id: String) = data.value.orders.firstOrNull { it.id == id } ?: error("Pesanan tidak ditemukan.")
    private fun refund(id: String) = data.value.refundFor(id) ?: error("Pengajuan refund belum ada.")
    private fun updateRefund(value: BuyerRefund) {
        mutableData.value = data.value.copy(refunds = data.value.refunds.map { if (it.id == value.id) value else it })
    }
    override suspend fun saveProfile(profile: BuyerProfile) {
        require(profile.name.isNotBlank()) { "Nama wajib diisi." }
        require(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(profile.email)) { "Email tidak valid." }
        require(profile.phone.length in 8..15 && profile.phone.all(Char::isDigit)) { "Nomor telepon harus 8–15 angka." }
        require(profile.selectedAddress in data.value.addresses.indices) { "Alamat tidak ditemukan." }
        mutableData.value = data.value.copy(profile = profile)
    }
    override suspend fun saveReview(orderId: String, rating: Int, improvements: Set<String>, comment: String) {
        val order = order(orderId)
        require(order.order.status == OrderStatus.COMPLETED) { "Ulasan hanya untuk pesanan yang selesai." }
        require(rating in 1..5) { "Pilih nilai 1–5 bintang." }
        require(data.value.reviewFor(orderId) == null) { "Ulasan untuk pesanan ini sudah dikirim." }
        require(comment.length <= 1000) { "Komentar maksimal 1000 karakter." }
        mutableData.value = data.value.copy(reviews = data.value.reviews + BuyerReview(orderId, order.storeId,
            data.value.profile.name, rating, improvements.toSet(), comment.trim(), now))
    }
    override suspend fun submitRefund(request: RefundRequest) {
        val order = order(request.orderId)
        require(isRefundEligible(order, now)) { "Pesanan berada di luar syarat atau batas pengajuan refund." }
        require(data.value.refunds.none { it.orderId == order.id && it.status != BuyerRefundStatus.REJECTED }) { "Pesanan sudah memiliki pengajuan aktif atau refund yang selesai." }
        require(request.reason.isNotBlank() && request.description.trim().length >= 10) { "Isi alasan dan uraian minimal 10 karakter." }
        require(request.photoUri.startsWith("content://")) { "Pilih foto bukti lokal yang valid." }
        require(request.bank in listOf("BCA", "BRI", "BNI", "Mandiri")) { "Pilih bank tujuan." }
        require(request.account.length in 8..24 && request.account.all(Char::isDigit) && request.recipient.isNotBlank()) { "Lengkapi rekening tujuan refund." }
        require(request.confirmed) { "Konfirmasi data pengajuan terlebih dahulu." }
        val id = "RF-" + (data.value.refunds.size + 2).toString().padStart(3, '0')
        val created = BuyerRefund(id, order.id, order.total, request, now,
            events = listOf(RefundEvent("Pengajuan dikirim", "${buyerDate(now)} · ${request.reason}")))
        mutableData.value = data.value.copy(refunds = data.value.refunds + created)
    }
    override suspend fun simulateRefundResponse(orderId: String, approved: Boolean) {
        val current = refund(orderId)
        require(current.status == BuyerRefundStatus.SUBMITTED) { "Pengajuan sudah ditanggapi." }
        updateRefund(current.copy(status = if (approved) BuyerRefundStatus.AWAITING_FUNDS else BuyerRefundStatus.REJECTED,
            events = current.events + if (approved) listOf(RefundEvent("Disetujui petani", "${buyerDate(now)} · Refund penuh"),
                RefundEvent("Bukti transfer balik diunggah", "transfer-balik-demo.png · ilustrasi")) else listOf(RefundEvent("Ditolak petani", "Alasan contoh: kondisi perlu diperiksa ulang"))))
    }
    override suspend fun confirmRefundReceived(orderId: String) {
        val current = refund(orderId)
        require(current.status == BuyerRefundStatus.AWAITING_FUNDS) { "Dana hanya dapat dikonfirmasi setelah bukti transfer tersedia." }
        updateRefund(current.copy(status = BuyerRefundStatus.COMPLETED, missingReported = false,
            events = current.events + RefundEvent("Dana diterima · Refund selesai", "${buyerDate(now)} · Dikonfirmasi pembeli")))
    }
    override suspend fun reportMissingRefund(orderId: String) {
        val current = refund(orderId)
        require(current.status == BuyerRefundStatus.AWAITING_FUNDS) { "Pelaporan dana hanya untuk refund yang menunggu konfirmasi." }
        if (!current.missingReported) updateRefund(current.copy(missingReported = true,
            events = current.events + RefundEvent("Dana belum diterima dilaporkan", "${buyerDate(now)} · Refund tetap terbuka")))
    }
    override suspend fun createTicket(orderId: String): String {
        val current = refund(orderId)
        data.value.ticketFor(current.id)?.let { return it.id }
        val issue = escalationIssue(current, now) ?: error("Tiket tersedia jika refund ditolak, tanpa tanggapan 24 jam, atau dana belum diterima sudah dilaporkan.")
        val ticket = BuyerTicket("TKT-" + (data.value.tickets.size + 26).toString().padStart(3, '0'), current.id, orderId, issue, now)
        mutableData.value = data.value.copy(tickets = data.value.tickets + ticket)
        return ticket.id
    }
    override suspend fun addTicketEvidence(ticketId: String, note: String, uri: String?) {
        require(note.isNotBlank() || !uri.isNullOrBlank()) { "Isi keterangan atau pilih bukti." }
        require(note.length <= 1000) { "Keterangan maksimal 1000 karakter." }
        if (uri != null) require(uri.startsWith("content://")) { "Bukti lokal tidak valid." }
        require(data.value.tickets.any { it.id == ticketId }) { "Tiket belum tercatat." }
        mutableData.value = data.value.copy(tickets = data.value.tickets.map { ticket ->
            if (ticket.id == ticketId) ticket.copy(notes = ticket.notes + listOfNotNull(note.trim().takeIf { it.isNotBlank() }),
                evidenceUris = ticket.evidenceUris + listOfNotNull(uri)) else ticket
        })
    }
    override suspend fun addToCart(productId: String, quantity: Int) {
        require(quantity > 0) { "Jumlah harus lebih dari nol." }
        setCartQuantity(productId, (data.value.cart[productId] ?: 0) + quantity)
    }
    override suspend fun setCartQuantity(productId: String, quantity: Int) {
        val product = data.value.products.firstOrNull { it.id == productId } ?: error("Produk tidak ditemukan.")
        require(quantity >= 0 && quantity <= product.available) { "Jumlah melebihi stok tersedia." }
        require(quantity == 0 || product.status == ProductStatus.ACTIVE) { "Produk belum aktif." }
        mutableData.value = data.value.copy(cart = if (quantity == 0) data.value.cart - productId else data.value.cart + (productId to quantity))
    }
    override suspend fun reorder(orderId: String) {
        val previous = order(orderId)
        val next = data.value.cart.toMutableMap()
        previous.order.lines.forEach { line ->
            val product = data.value.products.firstOrNull { it.id == line.productId } ?: error("Produk tidak lagi tersedia.")
            val quantity = (next[line.productId] ?: 0) + line.quantity
            require(product.status == ProductStatus.ACTIVE && quantity <= product.available) { "Stok ${line.name} tidak cukup untuk membeli lagi." }
            next[line.productId] = quantity
        }
        mutableData.value = data.value.copy(cart = next)
    }
    private fun updateOrder(value: BuyerOrder) {
        mutableData.value = data.value.copy(orders = data.value.orders.map { if (it.id == value.id) value else it })
    }
    override suspend fun saveAddress(address: BuyerAddress, makeDefault: Boolean) {
        require(address.id.isNotBlank() && address.label.isNotBlank() && address.recipient.isNotBlank()) { "Lengkapi label dan nama penerima." }
        require(address.phone.length in 8..15 && address.phone.all(Char::isDigit)) { "Nomor kontak harus 8–15 angka." }
        require(address.street.trim().length >= 10 && address.district.isNotBlank() && address.city.isNotBlank()) { "Lengkapi alamat, kecamatan, dan kota." }
        val previous = data.value.addresses
        val addresses = if (previous.any { it.id == address.id }) previous.map { if (it.id == address.id) address else it } else previous + address
        mutableData.value = data.value.copy(addresses = addresses, profile = if (makeDefault) data.value.profile.copy(selectedAddress = addresses.indexOfFirst { it.id == address.id }) else data.value.profile)
    }
    override suspend fun selectAddress(addressId: String) {
        val index = data.value.addresses.indexOfFirst { it.id == addressId }
        require(index >= 0) { "Alamat tidak ditemukan." }
        mutableData.value = data.value.copy(profile = data.value.profile.copy(selectedAddress = index))
    }
    override suspend fun createOrder(request: CheckoutRequest, limit: Long): String {
        require(request.requestId.isNotBlank()) { "Identitas permintaan checkout tidak valid." }
        data.value.checkoutRequests[request.requestId]?.let { return it }
        val current = data.value
        require(current.cart.isNotEmpty()) { "Keranjang masih kosong." }
        val address = current.addresses.firstOrNull { it.id == request.addressId } ?: error("Pilih alamat yang tersimpan.")
        require(request.fulfillment != Fulfillment.DELIVERY || canDeliverTo(address)) { "Alamat di luar area antar. Pilih ambil sendiri atau alamat Lowokwaru." }
        require(!request.date.isBefore(earliestCheckoutDate(current, now))) { "Tanggal sebelum produk siap dikirim." }
        require(request.date.dayOfWeek != DayOfWeek.SUNDAY) { "Toko tutup pada hari Minggu." }
        require(request.slot in if (request.fulfillment == Fulfillment.DELIVERY) deliverySlots else pickupSlots) { "Pilih slot layanan yang tersedia." }
        val lines = current.cart.map { (id, quantity) ->
            val product = current.products.firstOrNull { it.id == id } ?: error("Produk tidak ditemukan.")
            require(product.status == ProductStatus.ACTIVE && quantity > 0 && quantity <= product.available) { "Stok ${product.name} tidak cukup." }
            OrderLine(product.id, product.name, quantity, product.price)
        }
        val total = lines.sumOf { it.total } + request.fulfillment.fee
        require(limit > 0 && total <= limit) { "Total termasuk ongkir melebihi batas checkout." }
        val prefix = "PES-2026-"
        val number = (current.orders.filter { it.id.startsWith(prefix) }.mapNotNull { it.id.removePrefix(prefix).toIntOrNull() }.maxOrNull() ?: 0) + 1
        val id = prefix + number.toString().padStart(3, '0')
        val snapshot = BuyerOrder(Order(id, address.recipient, lines, deliveryFee = request.fulfillment.fee,
            schedule = scheduleLabel(request.date, request.slot)), address = address, fulfillment = request.fulfillment, createdAt = now, holdsStock = true)
        val products = StockReservations.reserve(current.products, current.cart)
        mutableData.value = current.copy(orders = listOf(snapshot) + current.orders, products = products, cart = emptyMap(),
            checkoutRequests = current.checkoutRequests + (request.requestId to id))
        return id
    }
    override suspend fun simulateAcceptance(orderId: String) {
        val current = order(orderId)
        require(current.order.status == OrderStatus.PENDING) { "Pesanan sudah ditanggapi petani." }
        require(now.isBefore(current.createdAt.plus(Duration.ofHours(24)))) { "Batas konfirmasi petani telah lewat." }
        updateOrder(current.copy(order = current.order.copy(status = OrderStatus.PAYMENT), acceptedAt = now))
    }
    override suspend fun submitPaymentProof(orderId: String, uri: String) {
        val current = order(orderId)
        require(current.order.status == OrderStatus.PAYMENT) { "Bukti hanya dapat dikirim setelah petani menerima pesanan." }
        require(current.paymentDeadline != null && now.isBefore(current.paymentDeadline)) { "Batas unggah bukti sudah berakhir." }
        require(uri.startsWith("content://")) { "Pilih foto bukti pembayaran lokal." }
        updateOrder(current.copy(order = current.order.copy(status = OrderStatus.REVIEW), proofs = current.proofs + PaymentProof(uri, now)))
    }
    override suspend fun simulatePaymentReview(orderId: String, approved: Boolean) {
        val current = order(orderId)
        require(current.order.status == OrderStatus.REVIEW && current.proofs.lastOrNull()?.status == ProofStatus.SUBMITTED) { "Belum ada bukti yang perlu diperiksa." }
        val last = current.proofs.last().copy(status = if (approved) ProofStatus.APPROVED else ProofStatus.REJECTED,
            reviewedAt = now, reason = if (approved) "Dana masuk telah diperiksa petani (demo)" else "Nominal dan penerima terpotong. Unggah bukti utuh yang memperlihatkan nominal, penerima, tanggal, dan status transfer.")
        updateOrder(current.copy(order = current.order.copy(status = if (approved) OrderStatus.PROCESSING else OrderStatus.PAYMENT),
            proofs = current.proofs.dropLast(1) + last))
    }
    override suspend fun simulateArrival(orderId: String) {
        val current = order(orderId)
        require(current.order.status in listOf(OrderStatus.PROCESSING, OrderStatus.DELIVERY) && !canConfirmReceipt(current)) { "Pesanan belum bisa dilanjutkan atau sudah tiba." }
        updateOrder(current.copy(order = current.order.copy(status = if (current.fulfillment == Fulfillment.DELIVERY) OrderStatus.DELIVERY else OrderStatus.PROCESSING),
            tracking = if (current.fulfillment == Fulfillment.DELIVERY) TrackingStage.ARRIVED else TrackingStage.PICKUP_READY))
    }
    override suspend fun confirmReceipt(orderId: String) {
        val current = order(orderId)
        require(canConfirmReceipt(current)) { "Pesanan belum tiba atau siap diambil." }
        val received = current.copy(order = current.order.copy(status = OrderStatus.COMPLETED), tracking = TrackingStage.RECEIVED, receivedAt = now, holdsStock = false)
        val products = if (current.holdsStock) StockReservations.consume(data.value.products, current.order.lines) else data.value.products
        mutableData.value = data.value.copy(orders = data.value.orders.map { if (it.id == current.id) received else it }, products = products)
    }
    override suspend fun reportDeliveryIssue(orderId: String, description: String) {
        val current = order(orderId)
        require(description.trim().length in 10..1000) { "Tuliskan masalah pengiriman 10–1000 karakter." }
        require(current.order.status in listOf(OrderStatus.PROCESSING, OrderStatus.DELIVERY, OrderStatus.COMPLETED)) { "Pesanan belum dalam pemenuhan." }
        updateOrder(current.copy(deliveryIssue = description.trim()))
    }
    override suspend fun respondSchedule(orderId: String, accept: Boolean) {
        val current = order(orderId);val proposal = current.proposal ?: error("Tidak ada usulan jadwal.")
        require(current.order.status in listOf(OrderStatus.PROCESSING, OrderStatus.DELIVERY)) { "Pesanan tidak sedang dipenuhi." }
        val target = if (accept) ProposalStatus.ACCEPTED else ProposalStatus.REJECTED
        if (proposal.status == target) return
        require(proposal.status == ProposalStatus.PENDING) { "Usulan sudah ditanggapi." }
        updateOrder(current.copy(order = current.order.copy(schedule = if (accept) scheduleLabel(proposal.date, proposal.slot) else current.order.schedule),
            proposal = proposal.copy(status = target, originalSchedule = current.order.schedule)))
    }

}
