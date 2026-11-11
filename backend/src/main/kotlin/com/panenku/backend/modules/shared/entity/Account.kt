package com.panenku.backend.modules.shared.entity

import jakarta.persistence.*
import java.util.UUID

// Shared profile mappings used by auth, catalog and checkout.
@Entity @Table(name = "farmer_profiles")
class FarmerProfile(
    @Id var id: UUID = UUID.randomUUID(),
    var storeName: String = "",
    var contactPhone: String? = null,
    var pickupLocation: String? = null,
    var serviceArea: String = "",
    var deliveryFee: Long = 0,
    var shippingEnabled: Boolean = false,
    var ktpStatus: String = "belum_diajukan",
    // Pipe-separated slots, e.g. 08:00-10:00|13:00-15:00; managed by profile module.
    var timeSlots: String = ""
)

@Entity @Table(name = "farmer_payment_methods")
class FarmerPaymentMethod(
    @Id var id: UUID = UUID.randomUUID(),
    var farmerId: UUID = UUID.randomUUID(),
    var methodType: String = "bank_transfer",
    var qrisImagePath: String? = null,
    var bankName: String? = null,
    var accountNumber: String? = null,
    var accountHolderName: String? = null
) {
    fun complete(): Boolean = when (methodType) {
        "qris" -> !qrisImagePath.isNullOrBlank() && !accountHolderName.isNullOrBlank()
        "bank_transfer" -> !bankName.isNullOrBlank() && !accountNumber.isNullOrBlank() && !accountHolderName.isNullOrBlank()
        else -> false
    }
}

@Entity @Table(name = "settings")
class Setting(@Id var key: String = "transaction_limit", var value: String = "10000000")
