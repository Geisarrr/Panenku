package id.panenku.app.features.f2_stock_reservation

import id.panenku.app.data.Product
import id.panenku.app.data.OrderLine

/** Pure stock operations. Lifecycle/transaction guards remain in the repository coordinator. */
object StockReservations {
    fun available(physical: Int, reserved: Int): Int = (physical - reserved).coerceAtLeast(0)
    fun reserve(products: List<Product>, quantities: Map<String, Int>): List<Product> = products.map {
        it.copy(reserved = it.reserved + (quantities[it.id] ?: 0))
    }
    fun consume(products: List<Product>, lines: List<OrderLine>): List<Product> = products.map { product ->
        val quantity = lines.filter { it.productId == product.id }.sumOf { it.quantity }
        product.copy(physical = product.physical - quantity, reserved = product.reserved - quantity)
    }
}
