package id.panenku.app.features.f5_order_status.api

import id.panenku.app.data.*
import id.panenku.app.data.buyer.*

interface F5BuyerOperations {
    suspend fun saveReview(orderId: String, rating: Int, improvements: Set<String>, comment: String)
    suspend fun simulateAcceptance(orderId: String)
    suspend fun simulateArrival(orderId: String)
    suspend fun confirmReceipt(orderId: String)
    suspend fun reportDeliveryIssue(orderId: String, description: String)
}
