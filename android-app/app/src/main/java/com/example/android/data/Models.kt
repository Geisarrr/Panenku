package id.panenku.app.data

import id.panenku.app.features.f2_stock_reservation.StockReservations

import java.text.NumberFormat
import java.util.Locale

enum class Role(val label: String) { BUYER("Pembeli"), FARMER("Petani"), ADMIN("Admin") }
enum class ProductStatus(val label: String) { ACTIVE("Aktif"), DRAFT("Draf"), INACTIVE("Nonaktif") }
data class Product(
    val id: String, val name: String, val commodity: String, val description: String,
    val price: Long, val physical: Int, val reserved: Int = 0,
    val status: ProductStatus = ProductStatus.ACTIVE, val imageKey: String = "shallot",
    val readyDate: String = "23 Juni 2026", val photoUri: String? = null
) { val available: Int get() = StockReservations.available(physical, reserved) }
enum class OrderStatus(val label: String) {
    PENDING("Menunggu Petani"), PAYMENT("Menunggu Pembayaran"),
    REVIEW("Verifikasi Pembayaran"), PROCESSING("Diproses"),
    DELIVERY("Diantar"), COMPLETED("Selesai"), CANCELLED("Dibatalkan")
}
data class OrderLine(val productId: String, val name: String, val quantity: Int, val price: Long) {
    val total: Long get() = quantity * price
}
data class Order(
    val id: String, val buyer: String, val lines: List<OrderLine>,
    val deliveryFee: Long = 2500, val status: OrderStatus = OrderStatus.PENDING,
    val schedule: String = "24 Juni 2026 · 09.00–12.00 WIB", val note: String = ""
) { val subtotal: Long get() = lines.sumOf { it.total }; val total: Long get() = subtotal + deliveryFee }
data class DashboardData(val products: List<Product> = emptyList(), val orders: List<Order> = emptyList())
fun rupiah(value: Long): String = "Rp" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID")).format(value)

object SampleData {
    val products = listOf(
        Product("sawi", "Sawi Putih", "Sayur", "Segar, bersih, dipanen dari kebun lokal.", 5000, 50, 20, imageKey = "cabbage"),
        Product("tomat", "Tomat Sayur", "Sayur", "Tomat merah segar, kualitas pilihan.", 10000, 25, 10, imageKey = "tomato"),
        Product("cabai", "Cabai Merah", "Cabai", "Cabai merah segar.", 28000, 0, status = ProductStatus.INACTIVE, imageKey = "chili"),
        Product("bawang", "Bawang Merah", "Bawang", "Bawang merah kering, ukuran sedang, bersih dan disortir.", 35000, 12, status = ProductStatus.DRAFT)
    )
    val order = Order("PES-2026-004", "Rina Catering", listOf(
        OrderLine("sawi", "Sawi Putih", 20, 5000), OrderLine("tomat", "Tomat Sayur", 10, 10000)
    ))
    val data = DashboardData(products, listOf(order))
}
