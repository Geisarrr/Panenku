package id.panenku.app.ui.connected.buyer

import com.google.gson.JsonObject
import id.panenku.app.data.remote.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder

data class BuyerSearch(val query:String="",val commodity:String="",val sort:String="price_asc",val stores:Boolean=false) {
    fun path(page:Int=0):String {
        require(sort in setOf("price_asc","price_desc")) { "Urutan tidak didukung." }
        require(commodity.isBlank() || commodity.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        return "buyer/products?search=${URLEncoder.encode(query.trim(),"UTF-8")}&sort=$sort&page=$page&size=20" + if(commodity.isNotBlank()) "&commodityId=$commodity" else ""
    }
}
internal fun selectedBuyerRows(cart:JsonObject, initialized:Boolean, selected:Set<String>) = cart.list("items").filter { !initialized || it.text("id") in selected }
internal fun buyerLineTotal(price:Long,kg:String):Long = try { BigDecimal(kg).multiply(BigDecimal.valueOf(price)).setScale(0,RoundingMode.HALF_UP).longValueExact() } catch(e:Exception) { 0L }
internal fun buyerLink(value:String):Pair<String,String>? {
    // Internal routes only. An API payload cannot open an external site or escape a path segment.
    val match=Regex("^/(orders|refunds|escalations)/([A-Za-z0-9_-]{1,128})(/schedule)?$").matchEntire(value) ?: return null
    if(match.groupValues[3].isNotEmpty() && match.groupValues[1]!="orders")return null
    return match.groupValues[1] to match.groupValues[2]
}
