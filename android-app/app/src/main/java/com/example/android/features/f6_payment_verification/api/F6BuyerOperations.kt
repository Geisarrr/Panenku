package id.panenku.app.features.f6_payment_verification.api

import id.panenku.app.data.*
import id.panenku.app.data.buyer.*

interface F6BuyerOperations {
    suspend fun submitPaymentProof(orderId: String, uri: String)
    suspend fun simulatePaymentReview(orderId: String, approved: Boolean)
}
