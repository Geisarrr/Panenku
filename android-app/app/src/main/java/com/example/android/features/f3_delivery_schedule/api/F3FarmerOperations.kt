package id.panenku.app.features.f3_delivery_schedule.api

import id.panenku.app.data.*

interface F3FarmerOperations {
    suspend fun proposeSchedule(id: String, schedule: String, reason: String)
}
