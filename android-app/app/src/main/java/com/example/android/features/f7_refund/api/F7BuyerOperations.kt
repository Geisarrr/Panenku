package id.panenku.app.features.f7_refund.api

import id.panenku.app.data.*
import id.panenku.app.data.buyer.*

interface F7BuyerOperations {
    suspend fun submitRefund(request: RefundRequest)
    suspend fun simulateRefundResponse(orderId: String, approved: Boolean)
    suspend fun confirmRefundReceived(orderId: String)
    suspend fun reportMissingRefund(orderId: String)
    suspend fun createTicket(orderId: String): String
    suspend fun addTicketEvidence(ticketId: String, note: String, uri: String?)
}
