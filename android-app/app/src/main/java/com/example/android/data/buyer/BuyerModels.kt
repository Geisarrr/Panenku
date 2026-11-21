package id.panenku.app.data.buyer

import id.panenku.app.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BuyerOrder(
    val order: Order, val storeId: String = "sobianto", val storeName: String = "Toko Sobianto",
    val farmerName: String = "Darto Sobianto", val serviceFee: Long = 0, val packagingFee: Long = 0,
    val paymentMethod: String = "Transfer langsung ke petani", val receivedAt: Instant? = null,
    val paidFulfillmentFailed: Boolean = false,
    val address: BuyerAddress = initialBuyerAddresses().first(), val fulfillment: Fulfillment = Fulfillment.DELIVERY,
    val createdAt: Instant = Instant.parse("2026-06-21T02:00:00Z"),
    val acceptedAt: Instant? = if (order.status in listOf(OrderStatus.PAYMENT, OrderStatus.REVIEW)) Instant.parse("2026-06-21T01:00:00Z") else null,
    val proofs: List<PaymentProof> = emptyList(), val tracking: TrackingStage = TrackingStage.PREPARING,
    val proposal: ScheduleProposal? = null, val holdsStock: Boolean = false, val deliveryIssue: String = ""
) {
    val id get() = order.id
    val total get() = order.total + serviceFee + packagingFee
    val paymentDeadline: Instant? get() = proofs.lastOrNull { it.status == ProofStatus.REJECTED }?.reviewedAt?.plus(Duration.ofHours(24)) ?: acceptedAt?.plus(Duration.ofHours(24))
}
data class BuyerProfile(val name: String = "Rina Catering", val email: String = "rina@example.com",
                        val phone: String = "080000000000", val selectedAddress: Int = 0, val notifications: Boolean = true)
data class BuyerReview(val orderId: String, val storeId: String, val author: String, val rating: Int,
                       val improvements: Set<String>, val comment: String, val submittedAt: Instant)
enum class BuyerRefundStatus(val label: String) {
    SUBMITTED("Menunggu tanggapan petani"), REJECTED("Ditolak petani"),
    AWAITING_FUNDS("Menunggu konfirmasi dana"), COMPLETED("Refund selesai")
}
data class RefundRequest(val orderId: String, val reason: String, val description: String,
                         val photoUri: String, val bank: String, val account: String, val recipient: String, val confirmed: Boolean)
data class RefundEvent(val title: String, val detail: String)
data class BuyerRefund(val id: String, val orderId: String, val amount: Long, val request: RefundRequest,
                       val submittedAt: Instant, val status: BuyerRefundStatus = BuyerRefundStatus.SUBMITTED,
                       val missingReported: Boolean = false, val events: List<RefundEvent> = emptyList())
enum class TicketIssue(val label: String) {
    REJECTED("Refund ditolak"), NO_RESPONSE("Tidak ada tanggapan lebih dari 24 jam"), MISSING_FUNDS("Dana belum diterima")
}
data class BuyerTicket(val id: String, val refundId: String, val orderId: String, val issue: TicketIssue,
                       val createdAt: Instant, val notes: List<String> = emptyList(), val evidenceUris: List<String> = emptyList())
data class BuyerData(val profile: BuyerProfile = BuyerProfile(), val orders: List<BuyerOrder> = emptyList(),
                     val products: List<Product> = emptyList(), val reviews: List<BuyerReview> = emptyList(),
                     val refunds: List<BuyerRefund> = emptyList(), val tickets: List<BuyerTicket> = emptyList(),
                     val cart: Map<String, Int> = emptyMap(), val addresses: List<BuyerAddress> = initialBuyerAddresses(),
                     val checkoutRequests: Map<String, String> = emptyMap()) {
    fun refundFor(orderId: String) = refunds.lastOrNull { it.orderId == orderId }
    fun reviewFor(orderId: String) = reviews.firstOrNull { it.orderId == orderId }
    fun ticketFor(refundId: String) = tickets.firstOrNull { it.refundId == refundId }
}
fun refundDeadline(order: BuyerOrder): Instant? = order.receivedAt?.plus(Duration.ofHours(24))
fun isRefundEligible(order: BuyerOrder, now: Instant): Boolean = order.paidFulfillmentFailed ||
        (order.order.status == OrderStatus.COMPLETED && order.receivedAt != null &&
                !now.isBefore(order.receivedAt) && now.isBefore(refundDeadline(order)))
fun escalationIssue(refund: BuyerRefund, now: Instant): TicketIssue? = when {
    refund.status == BuyerRefundStatus.REJECTED -> TicketIssue.REJECTED
    refund.status == BuyerRefundStatus.AWAITING_FUNDS && refund.missingReported -> TicketIssue.MISSING_FUNDS
    refund.status == BuyerRefundStatus.SUBMITTED && !now.isBefore(refund.submittedAt.plus(Duration.ofHours(24))) -> TicketIssue.NO_RESPONSE
    else -> null
}
fun safeCsMessage(ticket: BuyerTicket): String =
    "Halo CS Panenku, mohon tinjau tiket #${ticket.id}, pesanan #${ticket.orderId}, refund #${ticket.refundId}. ${ticket.issue.label}."
fun buyerDate(instant: Instant): String = DateTimeFormatter.ofPattern("d MMM yyyy, HH.mm 'WIB'", Locale.forLanguageTag("id-ID"))
    .withZone(ZoneId.of("Asia/Jakarta")).format(instant)
object BuyerSamples {
    val now: Instant = Instant.parse("2026-06-21T02:00:00Z")
    val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    val address = "Gang H. Gumat 1 no. 14, Lowokwaru, Kota Malang, Jawa Timur 64293"
    val addresses = listOf("Dapur utama" to address, "Dapur kedua" to "Jl. Soekarno Hatta, Lowokwaru, Kota Malang")
    val completed = BuyerOrder(SampleData.order.copy(id = "PES-2026-002", status = OrderStatus.COMPLETED),
        receivedAt = Instant.parse("2026-06-20T03:00:00Z"))
    val historical = BuyerOrder(SampleData.order.copy(id = "PES-2024-001", status = OrderStatus.COMPLETED, deliveryFee = 10000),
        serviceFee = 1000, packagingFee = 1500, paymentMethod = "Gopay Wallet · arsip contoh",
        receivedAt = Instant.parse("2024-06-22T03:00:00Z"))
    val initial = BuyerData(products = SampleData.products.filter { it.id != "bawang" }, orders = listOf(
        BuyerOrder(SampleData.order), BuyerOrder(SampleData.order.copy(id = "PES-2026-003", status = OrderStatus.PAYMENT)),
        BuyerOrder(SampleData.order.copy(id = "PES-2026-005", status = OrderStatus.REVIEW), proofs = listOf(PaymentProof("demo:proof", now))),
        BuyerOrder(SampleData.order.copy(id = "PES-2026-001", status = OrderStatus.DELIVERY), tracking = TrackingStage.ARRIVED),
        BuyerOrder(SampleData.order.copy(id = "PES-2026-006", status = OrderStatus.PROCESSING), proposal = ScheduleProposal(LocalDate.of(2026, 6, 25), "09.00–12.00 WIB", "Hujan deras membuat panen tertunda. Kami perlu satu hari tambahan agar sayur tiba dalam kondisi segar.", now)), completed, historical
    ))
    val refundExample = BuyerRefund("RF-002", completed.id, completed.total,
        RefundRequest(completed.id, "Kondisi sayuran tidak sesuai", "Sawi layu dan beberapa tomat rusak saat diterima.",
            "demo:condition", "BCA", "000000000000", "Rina Catering (contoh)", true), now,
        status = BuyerRefundStatus.AWAITING_FUNDS,
        events = listOf(RefundEvent("Pengajuan dikirim", "Rina · Kondisi sayur dan foto demo"),
            RefundEvent("Disetujui petani", "Darto · Refund penuh"), RefundEvent("Bukti transfer balik diunggah", "transfer-balik-demo.png")))
}