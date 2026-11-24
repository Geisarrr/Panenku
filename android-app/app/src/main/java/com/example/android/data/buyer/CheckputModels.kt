package id.panenku.app.data.buyer

import id.panenku.app.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

data class BuyerAddress(val id: String, val label: String, val recipient: String, val phone: String,
                        val street: String, val district: String, val city: String = "Kota Malang", val courierNote: String = "")
fun initialBuyerAddresses() = listOf(
    BuyerAddress("utama", "Dapur utama", "Rina Catering", "080000000000", "Gang H. Gumat 1 no. 14, Lowokwaru, Kota Malang, Jawa Timur 64293", "Lowokwaru", courierNote = "Dekat masjid Al Hidayah"),
    BuyerAddress("cabang", "Dapur cabang", "Rina Catering", "080000000000", "Jl. Contoh 8, Sukun, Malang", "Sukun")
)
fun canDeliverTo(address: BuyerAddress): Boolean = address.district.trim().equals("Lowokwaru", true) && address.city.contains("Malang", true)
enum class Fulfillment(val label: String, val fee: Long) { PICKUP("Ambil sendiri", 0), DELIVERY("Antar", 2500) }
enum class ProofStatus(val label: String) { SUBMITTED("Menunggu pemeriksaan"), REJECTED("Ditolak petani"), APPROVED("Disetujui petani") }
data class PaymentProof(val uri: String, val submittedAt: Instant, val status: ProofStatus = ProofStatus.SUBMITTED,
                        val reviewedAt: Instant? = null, val reason: String = "")
enum class TrackingStage(val label: String) { PREPARING("Pesanan disiapkan"), EN_ROUTE("Diantar oleh petani"),
    ARRIVED("Menunggu Penerimaan"), PICKUP_READY("Siap Diambil"), RECEIVED("Selesai") }
enum class ProposalStatus(val label: String) { PENDING("Menunggu keputusanmu"), ACCEPTED("Jadwal baru disetujui"), REJECTED("Usulan ditolak · Jadwal lama berlaku") }
data class ScheduleProposal(val date: LocalDate, val slot: String, val reason: String, val submittedAt: Instant,
                            val status: ProposalStatus = ProposalStatus.PENDING, val originalSchedule: String? = null)
data class CheckoutRequest(val requestId: String, val addressId: String, val fulfillment: Fulfillment,
                           val date: LocalDate, val slot: String)
val deliverySlots = listOf("09.00–12.00 WIB", "13.00–16.00 WIB")
val pickupSlots = listOf("08.00–12.00 WIB", "13.00–16.00 WIB")
fun scheduleLabel(date: LocalDate, slot: String): String = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("id-ID"))) + " · " + slot
fun productReadyDate(product: Product): LocalDate {
    val format = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMMM uuuu").toFormatter(Locale.forLanguageTag("id-ID"))
    return LocalDate.parse(product.readyDate, format)
}
fun earliestCheckoutDate(data: BuyerData, now: Instant): LocalDate =
    (data.cart.keys.mapNotNull { id -> data.products.find { it.id == id } }.map(::productReadyDate) + now.atZone(ZoneId.of("Asia/Jakarta")).toLocalDate()).max()
fun cartSubtotal(data: BuyerData): Long = data.cart.entries.sumOf { (id, quantity) -> (data.products.find { it.id == id }?.price ?: 0) * quantity }
fun canConfirmReceipt(order: BuyerOrder): Boolean = order.tracking in listOf(TrackingStage.ARRIVED, TrackingStage.PICKUP_READY) && order.order.status in listOf(OrderStatus.DELIVERY, OrderStatus.PROCESSING)
