package id.panenku.app.features.f3_delivery_schedule.api

import id.panenku.app.data.*
import id.panenku.app.data.buyer.*

interface F3BuyerOperations {
    suspend fun saveAddress(address: BuyerAddress, makeDefault: Boolean)
    suspend fun selectAddress(addressId: String)
    suspend fun respondSchedule(orderId: String, accept: Boolean)
}
