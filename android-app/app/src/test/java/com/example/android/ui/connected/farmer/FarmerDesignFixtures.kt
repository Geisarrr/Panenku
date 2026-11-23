package id.panenku.app.ui.connected.farmer

import com.google.gson.JsonObject
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.ConnectedState

/** Figma sample values are confined to tests. Production always renders server responses. */
internal object FarmerDesignFixtures {
    fun product(id: String, name: String, commodity: String, price: Long, physical: Int, reserved: Int, status: String, photo: String) =
        json("id" to id, "name" to name, "commodityId" to commodity, "commodity" to commodity,
            "pricePerKg" to price, "stockPhysical" to physical, "stockReserved" to reserved, "stockAvailable" to physical-reserved,
            "publicationStatus" to status, "readyDate" to "2026-06-23", "photoAccessUrl" to "fixture://$photo", "photoUrl" to "managed/$photo",
            "farmerVerified" to true, "description" to "Bawang merah kering, ukuran sedang, bersih dan disortir. Cocok untuk kebutuhan dapur.")
    fun stock() = listOf(
        product("sawi", "Sawi Putih", "Sayur", 5000, 50, 20, "aktif", "cabbage"),
        product("tomat", "Tomat Sayur", "Sayur", 10000, 25, 10, "aktif", "tomato_stock"),
        product("cabai", "Cabai Merah", "Cabai", 28000, 0, 0, "nonaktif", "chili"),
        product("bawang", "Bawang Merah", "Bawang", 35000, 12, 0, "draft", "shallot")
    )
    fun order(status: String = "menunggu_petani", id: String = "PES-2026-004"): JsonObject = json(
        "orderId" to id, "buyerName" to "Rina Catering", "orderStatus" to status,
        "paymentStatus" to when(status) { "diproses", "diantar", "menunggu_penerimaan" -> "terverifikasi"; "verifikasi_pembayaran" -> "menunggu_verifikasi"; else -> "belum_dibayar" },
        "subtotalItems" to 200000, "deliveryFee" to 2500, "totalPayment" to 202500,
        "fulfillmentMethod" to "delivery", "scheduledDate" to "2026-06-24", "scheduledTimeSlot" to "09:00-12:00",
        "effectiveSchedule" to json("scheduledDate" to "2026-06-24", "scheduledTimeSlot" to "09:00-12:00", "version" to 0),
        "createdAt" to "2026-06-22T10:00:00+07:00", "expiresAt" to "2026-06-23T10:00:00+07:00",
        "deliveryDistrictSnapshot" to "Lowokwaru, Kota Malang", "deliveryAddressSnapshot" to "Gang H.Gumat 1 no.14, Lowokwaru, Kec Lowokwaru, Kota Malang, Jawatimur 64293",
        "pickupLocationSnapshot" to "Kebun Pak Darto", "buyerNotes" to "Dekat masjid al hidayah",
        "items" to listOf(
            json("productId" to "sawi", "productNameSnapshot" to "Sawi Putih", "quantityKg" to 20, "pricePerKgSnapshot" to 5000, "itemSubtotal" to 100000, "readyDateSnapshot" to "2026-06-23"),
            json("productId" to "tomat", "productNameSnapshot" to "Tomat Sayur", "quantityKg" to 10, "pricePerKgSnapshot" to 10000, "itemSubtotal" to 100000, "readyDateSnapshot" to "2026-06-23")
        ), "history" to if (status == "menunggu_petani") emptyList<JsonObject>() else listOf(json("toStatus" to "menunggu_pembayaran", "changedAt" to "2026-06-22T11:00:00+07:00"))
    )
    fun proof() = json("id" to "proof-1", "status" to "menunggu_verifikasi", "expectedAmount" to 202500,
        "submittedAt" to "2026-06-22T11:20:00+07:00", "originalFileName" to "transfer-demo.jpg", "fileSizeBytes" to 1258291, "designDemo" to true)
    fun state() = ConnectedState(user = ApiUser("farmer", "darto@example.test", "petani", "active"), products = stock(),
        commodities = listOf(json("id" to "Bawang", "name" to "Bawang · Bawang Merah", "isActive" to true)),
        orders = listOf(order(), order("verifikasi_pembayaran", "PES-2026-005"), order("diproses", "PES-2026-001")),
        farmerOrderProofs = mapOf("PES-2026-005" to listOf(proof())),
        profile = json("ownerName" to "Pak Darto", "storeName" to "Toko Sobianto", "pickupLocation" to "Kebun Pak Darto", "ktpStatus" to "disetujui", "timeSlots" to listOf("09:00-12:00", "13:00-16:00")),
        paymentMethods = listOf(json("id" to "bank-1", "methodType" to "bank_transfer", "accountHolderName" to "Darto Sobianto", "accountNumberLast4" to "0000", "bankName" to "BRI")),
        proofs = listOf(proof()), destination = json("accountHolderName" to "Darto Sobianto", "bankName" to "BRI", "accountNumber" to "0000"))
}
