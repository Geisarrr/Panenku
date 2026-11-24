package id.panenku.app.data

import id.panenku.app.features.f1_catalog.api.F1FarmerOperations
import id.panenku.app.features.f3_delivery_schedule.api.F3FarmerOperations
import id.panenku.app.features.f5_order_status.api.F5FarmerOperations

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface PanenkuRepository : F1FarmerOperations, F3FarmerOperations, F5FarmerOperations {
    val data: StateFlow<DashboardData>
    suspend fun load()
}
class DemoPanenkuRepository : PanenkuRepository {
    private val mutableData = MutableStateFlow(SampleData.data)
    override val data = mutableData.asStateFlow()
    override suspend fun load() = Unit
    override suspend fun saveProduct(product: Product) {
        require(product.name.isNotBlank()) { "Nama produk wajib diisi." }
        require(product.price > 0) { "Harga harus lebih dari nol." }
        require(product.physical >= product.reserved) { "Stok fisik tidak boleh kurang dari stok dipesan." }
        val products = data.value.products
        mutableData.value = data.value.copy(products = if (products.any { it.id == product.id })
            products.map { if (it.id == product.id) product else it } else products + product)
    }
    override suspend fun changeOrder(id: String, target: OrderStatus, reason: String) {
        val order = data.value.orders.firstOrNull { it.id == id } ?: error("Pesanan tidak ditemukan.")
        val allowed = when (order.status) {
            OrderStatus.PENDING -> setOf(OrderStatus.PAYMENT, OrderStatus.CANCELLED)
            OrderStatus.PAYMENT -> setOf(OrderStatus.REVIEW)
            OrderStatus.REVIEW -> setOf(OrderStatus.PROCESSING, OrderStatus.PAYMENT)
            OrderStatus.PROCESSING -> setOf(OrderStatus.DELIVERY, OrderStatus.CANCELLED)
            OrderStatus.DELIVERY -> emptySet()
            else -> emptySet()
        }
        require(target in allowed) { "Perubahan status pesanan tidak sesuai tahap saat ini." }
        if (target == OrderStatus.CANCELLED || (order.status == OrderStatus.REVIEW && target == OrderStatus.PAYMENT)) {
            require(reason.isNotBlank()) { "Alasan wajib diisi." }
        }
        val products = if (target == OrderStatus.CANCELLED || target == OrderStatus.DELIVERY) {
            data.value.products.map { product ->
                val quantity = order.lines.filter { it.productId == product.id }.sumOf { it.quantity }
                product.copy(reserved = (product.reserved - quantity).coerceAtLeast(0),
                    physical = if (target == OrderStatus.DELIVERY) (product.physical - quantity).coerceAtLeast(0) else product.physical)
            }
        } else data.value.products
        mutableData.value = data.value.copy(products = products,
            orders = data.value.orders.map { if (it.id == id) it.copy(status = target, note = reason) else it })
    }
    override suspend fun proposeSchedule(id: String, schedule: String, reason: String) {
        require(schedule.isNotBlank() && reason.isNotBlank()) { "Jadwal dan alasan wajib diisi." }
        val order = data.value.orders.first { it.id == id }
        require(order.status == OrderStatus.PROCESSING) { "Usulan jadwal hanya untuk pesanan diproses." }
        // Jadwal tersimpan tidak berubah sebelum pembeli menyetujui.
        mutableData.value = data.value.copy(orders = data.value.orders.map {
            if (it.id == id) it.copy(note = "Usulan: $schedule · $reason · menunggu persetujuan pembeli") else it
        })
    }
}
