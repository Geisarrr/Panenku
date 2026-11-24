package id.panenku.app.ui.connected.buyer

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.JsonObject
import id.panenku.app.data.remote.*
import java.util.UUID

/** Additional addresses are local and partitioned by authenticated account, never demo data. */
data class SavedBuyerAddress(val id: String = UUID.randomUUID().toString(), val label: String = "",
    val recipient: String = "", val phone: String = "", val address: String = "", val district: String = "", val note: String = "") {
    fun snapshot() = listOf(recipient, phone, address, note.takeIf(String::isNotBlank)?.let { "Catatan: $it" }).filterNotNull().filter(String::isNotBlank).joinToString("\n")
}
internal class BuyerAddressStore(context: Context) {
    private val prefs = context.getSharedPreferences("buyer_addresses", Context.MODE_PRIVATE)
    private val gson = Gson()
    fun read(user: String): List<SavedBuyerAddress> = runCatching {
        gson.fromJson<List<SavedBuyerAddress>>(prefs.getString("rows:$user", "[]"), object : TypeToken<List<SavedBuyerAddress>>() {}.type)
    }.getOrNull().orEmpty()
    fun selected(user: String) = prefs.getString("selected:$user", null)
    fun write(user: String, rows: List<SavedBuyerAddress>, selected: String?) {
        check(prefs.edit().putString("rows:$user", gson.toJson(rows)).putString("selected:$user", selected).commit()) { "Alamat belum dapat disimpan di perangkat." }
    }
    fun seed(user: String, profile: JsonObject): List<SavedBuyerAddress> {
        val rows = read(user)
        if (rows.isNotEmpty() || profile.text("deliveryAddress").isBlank()) return rows
        val first = SavedBuyerAddress(label = "Alamat utama", recipient = profile.text("fullName"), phone = profile.text("contactPhone"), address = profile.text("deliveryAddress"))
        return listOf(first).also { write(user, it, first.id) }
    }
}
