package id.panenku.app.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Frontend workflow using existing endpoints. A failed upload never means "submitted". */
class FarmerEnrollment(private val api: PanenkuApi) {
    var accountCreated: Boolean = false
        private set
    private var registeredEmail: String? = null
    fun clear() { accountCreated = false; registeredEmail = null }

    suspend fun submit(email: String, password: String, name: String, phone: String,
        store: String, location: String, areas: List<String>, sendDocument: suspend () -> JsonElement): JsonObject {
        val normalizedEmail = email.trim().lowercase()
        require(registeredEmail == null || registeredEmail == normalizedEmail) { "Selesaikan akun yang sedang didaftarkan terlebih dahulu." }
        if (!accountCreated) {
            api.register(normalizedEmail, password, name, "petani", phone, store)
            registeredEmail = normalizedEmail
            accountCreated = true
        }
        val session = api.session?.takeIf { it.user.email.equals(normalizedEmail, true) }
            ?: api.login(normalizedEmail, password)
        require(session.user.role == "petani") { "Gunakan akun petani untuk verifikasi toko." }
        api.put("auth/profile", json("fullName" to name, "contactPhone" to phone))
        api.put("petani/profile", json("storeName" to store, "pickupLocation" to location,
            "serviceArea" to areas, "deliveryFee" to 0, "shippingEnabled" to false, "timeSlots" to emptyList<String>()))
        // The caller supplies private multipart bytes and a stable idempotency key.
        val response = sendDocument().asJsonObject
        check(response.text("status") == "menunggu") {
            if (response.text("status") == "disetujui") "Pengajuan sudah disetujui. Silakan masuk kembali."
            else "Pengajuan belum berstatus menunggu. Silakan periksa status akun Anda."
        }
        return response
    }
}
