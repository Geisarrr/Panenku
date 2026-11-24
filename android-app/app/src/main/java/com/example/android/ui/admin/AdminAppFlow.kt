package id.panenku.app.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.*
import id.panenku.app.ui.connected.buyer.*
import kotlinx.coroutines.flow.first

@Composable internal fun AdminAppFlow(s:ConnectedState,vm:ConnectedViewModel) {
    var screen by rememberSaveable(s.user?.id){mutableStateOf("admin-home")}
    var targetUser by rememberSaveable{mutableStateOf("")};var loggingOut by remember{mutableStateOf(false)}
    val a=s.adminUi.copy(busy=s.busy)
    val savedScreens=rememberSaveableStateHolder()
    fun go(route:String){if(!s.busy){if(route=="admin-users")targetUser="";screen=route}}
    fun back(){if(!s.busy)when(screen){"admin-home"->loggingOut=true;"admin-evidence"->screen="admin-disputes";"admin-audit"->screen="admin-settings";else->screen="admin-home"}}
    BackHandler(!s.busy){back()}
    LaunchedEffect(screen,targetUser){
        vm.state.first{!it.busy}
        when(screen){
            "admin-home"->vm.adminLoadHome()
            "admin-verification"->vm.adminLoadQueue()
            "admin-users"->vm.adminLoadUsers(selected=targetUser)
            "admin-commodities"->vm.adminLoadCommodities()
            "admin-settings"->vm.adminSettings()
            "admin-disputes"->vm.adminLoadDisputes()
            "admin-audit","admin-moderation"->vm.adminLoadAudit()
            "admin-late-payments"->vm.latePayments(true)
            "admin-notifications"->vm.notifications()
            "admin-evidence"->a.dispute?.let{vm.adminLoadEvidence(it.text("orderId"))}
        }
    }
    AdminTheme {
        Box(Modifier.fillMaxSize()){
            savedScreens.SaveableStateProvider(screen) { when(screen){
                "admin-home"->AdminHomeScreen(::go,{loggingOut=true},a,s.user?.email.orEmpty())
                "admin-verification"->AdminVerificationScreen(emptyMap(),::back,{_,decision,reason,_->vm.adminVerifySelected(decision=="Disetujui",reason,true)},a,{status,q->vm.adminLoadQueue(status,q)},vm::adminInspectFarmer,{vm.image("admin/farmers/${a.verificationUser}/ktp-document")},{vm.adminLoadQueue(a.queueStatus,a.queueQuery,a.queuePage+1)})
                "admin-users"->AdminUsersScreen(emptyMap(),::back,{_,decision,reason,_->vm.adminChangeUser(decision=="Disuspensi",reason)},a,{role,status,q->vm.adminLoadUsers(role,status,q)},vm::adminInspectUser,{vm.adminLoadUsers(a.usersRole,a.usersStatus,a.usersQuery,a.usersPage+1)})
                "admin-settings"->AdminSettingsLiveScreen(emptyMap(),::back,{values->values["Limit"]?.toLongOrNull()?.let{vm.saveAdminSettings(it,values["WhatsApp"].orEmpty(),values["Reason"].orEmpty())}},s.settings,s.busy,{go("admin-audit")},vm::adminSettings)
                "admin-commodities"->AdminCommoditiesScreen(a,::back,vm::adminSaveCommodity){go("admin-moderation")}
                "admin-disputes"->AdminDisputesScreen(a,::back,vm::adminInspectDispute,{vm.adminLoadDisputes(a.disputePage+1)},{go("admin-evidence")},vm::adminDecideSelected){id->targetUser=id;screen="admin-users"}
                "admin-moderation"->AdminModerationScreen(a,::back)
                "admin-orders"->AdminOrdersScreen(a,::back){go("admin-late-payments")}
                "admin-audit"->APage("Riwayat perubahan","Audit tindakan administratif dari server",::back){FCard{AHistory(a.audit)};if(a.auditMore)FButton("Muat audit lainnya",{vm.adminLoadAudit(a.auditPage+1)},primary=false,enabled=!s.busy)}
                "admin-evidence"->APage("Bukti sengketa","Dokumen diambil melalui sesi administrator",::back){
                    val refund=a.refund;val dispute=a.dispute
                    FCard("Bukti refund"){
                        if(refund?.flag("hasEvidence")==true)FButton("Lihat bukti pengajuan refund",{vm.image("admin/refunds/${refund.text("id")}/evidence")},primary=false,enabled=!s.busy)
                        if(refund?.flag("hasTransferProof")==true)FButton("Lihat bukti transfer balik",{vm.image("admin/refunds/${refund.text("id")}/transfer-proof")},primary=false,enabled=!s.busy)
                    }
                    FCard("Bukti pembayaran pesanan"){
                        s.proofs.forEach{proof->FButton("Bukti #${shortId(proof.text("id"))} · ${proof.text("status")}",{vm.image("admin/orders/${dispute?.text("orderId")}/payment-proofs/${proof.text("id")}/image")},primary=false,enabled=!s.busy)}
                        if(s.proofs.isEmpty())FText("Belum ada bukti pembayaran dari server.",12f,color=FMuted)
                    }
                }
                "admin-late-payments"->ConnectedLatePayments(s,vm,true,::back)
                "admin-notifications"->ConnectedNotifications(s,vm,::back)
            }
            }
            if(s.busy)Surface(color=Color.White.copy(alpha=.65f),modifier=Modifier.fillMaxSize().clickable{}){Box(contentAlignment=Alignment.Center){CircularProgressIndicator()}}
        }
    }
    if(loggingOut)AConfirm("Keluar dari akun admin?","Sesi administrator di perangkat ini akan diakhiri.",confirm="Keluar",cancel={loggingOut=false}){loggingOut=false;vm.logout()}
}
