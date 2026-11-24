package id.panenku.app.ui.connected.buyer

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import id.panenku.app.data.remote.*

/** Ticket references and supplementary notes are local; server tickets remain authoritative. */
internal class BuyerHelpStore(context: Context) {
    private val prefs=context.getSharedPreferences("buyer_help",Context.MODE_PRIVATE)
    private fun write(key:String,value:String) { check(prefs.edit().putString(key,value).commit()){ "Catatan belum dapat disimpan di perangkat." } }
    fun ticket(user:String,refund:String)=prefs.getString("ticket:$user:$refund",null)
    fun rememberTicket(user:String,refund:String,id:String)=write("ticket:$user:$refund",id)
    fun draft(user:String,ticket:String):JsonObject=runCatching{Gson().fromJson(prefs.getString("draft:$user:$ticket","{}"),JsonObject::class.java)}.getOrNull()?:JsonObject()
    fun saveDraft(user:String,ticket:String,note:String,uri:String?,name:String)=write("draft:$user:$ticket",json("note" to note,"uri" to uri,"name" to name).toString())
    fun pendingReason(user:String,refund:String)=prefs.getString("pending:$user:$refund",null)
    fun pending(user:String,refund:String,reason:String?) {check(prefs.edit().apply{if(reason==null)remove("pending:$user:$refund") else putString("pending:$user:$refund",reason)}.commit())}
    fun reorder(user:String,order:String):JsonObject?=runCatching{Gson().fromJson(prefs.getString("reorder:$user:$order",null),JsonObject::class.java)}.getOrNull()
    fun reorder(user:String,order:String,plan:JsonObject?) {check(prefs.edit().apply{if(plan==null)remove("reorder:$user:$order") else putString("reorder:$user:$order",plan.toString())}.commit())}
}
internal fun buyerReviewComment(tags:Set<String>,comment:String)=listOf(tags.takeIf{it.isNotEmpty()}?.sorted()?.joinToString(", ")?.let{"Aspek: $it"},comment.trim().takeIf(String::isNotBlank)).filterNotNull().joinToString("\n")
internal fun canEscalateBuyerRefund(refund:JsonObject,now:java.time.Instant=java.time.Instant.now()):Boolean=when(refund.text("status")){
    "ditolak"->true
    "menunggu_konfirmasi_dana"->refund.flag("hasTransferProof")
    "diajukan","disetujui"->runCatching{
        val at=if(refund.text("status")=="disetujui")refund.text("farmerDecidedAt").ifBlank{refund.text("createdAt")} else refund.text("createdAt")
        !now.isBefore(java.time.Instant.parse(at).plusSeconds(86400))
    }.getOrDefault(false)
    else->false
}
internal fun canConfirmBuyerRefund(refund:JsonObject)=refund.text("status")=="menunggu_konfirmasi_dana" && refund.flag("hasTransferProof")
internal fun buyerCsMessage(ticket:JsonObject):String = "Halo CS Panenku, mohon tinjau tiket #${ticket.text("id")}, pesanan #${ticket.text("orderId")}, refund #${ticket.text("refundId")}. ${if(ticket.text("reason").contains("Dana belum diterima",true)) "Dana belum diterima." else "Mohon bantu peninjauan refund."}"
internal const val BUYER_CS_NUMBER="62812345678"
