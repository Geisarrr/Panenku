package id.panenku.app.features.f5_order_status.api

import id.panenku.app.data.*

interface F5FarmerOperations {
    suspend fun changeOrder(id: String, target: OrderStatus, reason: String = "")
}
