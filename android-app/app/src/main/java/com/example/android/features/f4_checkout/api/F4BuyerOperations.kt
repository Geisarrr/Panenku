package id.panenku.app.features.f4_checkout.api

import id.panenku.app.data.*
import id.panenku.app.data.buyer.*

interface F4BuyerOperations {
    suspend fun addToCart(productId: String, quantity: Int)
    suspend fun reorder(orderId: String)
    suspend fun setCartQuantity(productId: String, quantity: Int)
    suspend fun createOrder(request: CheckoutRequest, limit: Long = 10000000): String
}
