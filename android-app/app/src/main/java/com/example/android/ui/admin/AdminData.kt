package id.panenku.app.ui.admin

import com.google.gson.JsonObject

/** Server snapshots only. Empty defaults never create sample transactions or account decisions. */
data class AdminUiState(
    val loaded:Boolean=false,val verificationCount:Long?=null,val disputeCount:Long?=null,val suspendedCount:Long?=null,
    val priorities:List<JsonObject> = emptyList(),val recentAudit:List<JsonObject> = emptyList(),
    val queue:List<JsonObject> = emptyList(),val queueStatus:String="menunggu",val queueQuery:String="",val queuePage:Int=0,val queueMore:Boolean=false,val queueTotal:Long=0,
    val verification:JsonObject?=null,val verificationUser:String="",
    val users:List<JsonObject> = emptyList(),val user:JsonObject?=null,val usersRole:String="",val usersStatus:String="",val usersQuery:String="",val usersPage:Int=0,val usersMore:Boolean=false,
    val commodities:List<JsonObject> = emptyList(),val disputes:List<JsonObject> = emptyList(),val disputePage:Int=0,val disputeMore:Boolean=false,
    val dispute:JsonObject?=null,val refund:JsonObject?=null,val decidedDisputes:List<JsonObject> = emptyList(),
    val audit:List<JsonObject> = emptyList(),val auditPage:Int=0,val auditMore:Boolean=false,
    val busy:Boolean=false,
    // The existing backend does not expose order monitoring or product-report moderation APIs.
    val orders:List<JsonObject> = emptyList(),val selectedOrder:JsonObject?=null,
    val reports:List<JsonObject> = emptyList(),val selectedReport:JsonObject?=null
)
internal fun adminPhoneValid(value:String)=Regex("\\+[1-9][0-9]{7,14}").matches(value.replace(" ",""))
