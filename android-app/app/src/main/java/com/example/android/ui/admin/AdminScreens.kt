package id.panenku.app.ui.admin

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.buyer.*
import java.time.ZoneId

/** Callback signatures of the older navigation graph remain compatible; live entry supplies server snapshots. */
@Composable fun AdminHomeScreen(onNavigate:(String)->Unit,onLogout:()->Unit,state:AdminUiState=AdminUiState(),actor:String="") {
    val date=LocalBuyerClock.current().atZone(ZoneId.of("Asia/Jakarta")).toLocalDate().toString()
    APage("Ringkasan admin","${dateLabel(date)}${if(actor.isNotBlank())" · $actor" else ""}",onLogout,brand=false){
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            listOf(Triple(state.verificationCount,"Verifikasi petani","admin-verification"),Triple(state.disputeCount,"Tiket eskalasi","admin-disputes")).forEach{(count,label,route)->
                Column(Modifier.weight(1f).background(BPaper,RoundedCornerShape(14.dp)).clickable(role=Role.Button){onNavigate(route)}.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){FText(count?.toString() ?: "—",22f,700,FForest);FText(label,12f,color=FMuted)}
            }
        }
        FCard("Prioritas hari ini"){
            FBadge("Perlu tindakan","orange")
            state.priorities.firstOrNull()?.let{q->AMenu(R.drawable.admin_seal,"Tinjau #${shortId(q.text("applicationId").ifBlank{q.text("farmerId")})}","${q.text("storeName")} · Menunggu sejak ${dateTimeLabel(q.text("submittedAt"))}"){onNavigate("admin-verification")};FRule()}
            if((state.disputeCount ?: 0)>0){AMenu(R.drawable.admin_help,"Sengketa refund menunggu","${state.disputeCount} tiket · Buka kronologi"){onNavigate("admin-disputes")};FRule()}
            AMenu(R.drawable.admin_box,"Moderasi produk","Layanan laporan produk belum tersedia"){onNavigate("admin-moderation")};FRule()
            AMenu(R.drawable.admin_user,"Pengguna & status akun",state.suspendedCount?.let{"$it akun disuspensi · Tinjau status"} ?: "Periksa akses pengguna"){onNavigate("admin-users")}
            if(state.loaded && state.priorities.isEmpty() && state.disputeCount==0L)FText("Tidak ada pengajuan atau sengketa terbuka saat dimuat.",11f,color=FMuted)
        }
        FCard("Ruang administrasi"){
            AMenu(R.drawable.admin_user,"Pengguna & status akun","Cari pengguna, suspensi / pulihkan"){onNavigate("admin-users")};FRule()
            AMenu(R.drawable.admin_box,"Komoditas & moderasi","Kelola kategori aktif dan produk dilaporkan"){onNavigate("admin-commodities")};FRule()
            AMenu(R.drawable.admin_file,"Pantau seluruh pesanan","Status, pembayaran, jadwal, dan aktor"){onNavigate("admin-orders")};FRule()
            AMenu(R.drawable.admin_gear,"Pengaturan admin","Limit transaksi & kontak WhatsApp CS"){onNavigate("admin-settings")}
        }
        FCard("Keputusan terbaru · Audit"){
            AHistory(state.recentAudit)
            FText("Setiap keputusan menyimpan waktu, admin, dan alasan. Ringkasan ini tidak menunjukkan saldo titipan atau escrow.",10f,color=FMuted)
            FText("Lihat seluruh audit",12f,600,FGreen,Modifier.clickable(role=Role.Button){onNavigate("admin-audit")})
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
                FText("Notifikasi",11f,600,FGreen,Modifier.clickable(role=Role.Button){onNavigate("admin-notifications")})
                FText("Pembayaran terlambat",11f,600,FGreen,Modifier.clickable(role=Role.Button){onNavigate("admin-late-payments")})
            }
        }
    }
}
@Composable fun AdminVerificationScreen(decisions:Map<String,String>,onBack:()->Unit,onDecision:(String,String,String,Boolean)->Unit,state:AdminUiState=AdminUiState(),onFilter:(String,String)->Unit={_,_->},onSelect:(String)->Unit={},onDocument:()->Unit={},onMore:()->Unit={}) {
    var query by rememberSaveable{mutableStateOf(state.queueQuery)}
    val row=state.verification
    var nameCheck by rememberSaveable(state.verificationUser,row?.text("applicationId"),row?.text("submittedAt")){mutableStateOf(false)};var locationCheck by rememberSaveable(state.verificationUser,row?.text("applicationId"),row?.text("submittedAt")){mutableStateOf(false)};var documentCheck by rememberSaveable(state.verificationUser,row?.text("applicationId"),row?.text("submittedAt")){mutableStateOf(false)}
    var reason by rememberSaveable(state.verificationUser,row?.text("applicationId"),row?.text("submittedAt")){mutableStateOf("")};var confirm by remember{mutableStateOf<Boolean?>(null)}
    val checked=nameCheck && locationCheck && documentCheck
    APage("Verifikasi petani","${state.queueTotal} pengajuan pada filter ini",onBack){
        ASearch(query,{query=it.take(100)},"Cari nama toko / ID pengajuan"){onFilter(state.queueStatus,query)}
        AChips(listOf("menunggu" to "Menunggu${state.verificationCount?.let{" ($it)"}.orEmpty()}","disetujui" to "Disetujui","ditolak" to "Ditolak"),state.queueStatus){onFilter(it,query)}
        FCard("Antrean pengajuan"){
            if(state.queue.isEmpty())FText("Tidak ada pengajuan pada filter ini.",12f,color=FMuted)
            state.queue.forEachIndexed{i,q->AMenu(R.drawable.admin_file,"#${shortId(q.text("applicationId").ifBlank{q.text("farmerId")})} · ${q.text("storeName")}","${dateTimeLabel(q.text("submittedAt"))} · ${if(q.text("farmerId")==state.verificationUser)"Dipilih untuk ditinjau" else "Buka pengajuan"}"){onSelect(q.text("farmerId"))};if(i<state.queue.lastIndex)FRule()}
            if(state.queueMore)FButton("Muat pengajuan lainnya",onMore,primary=false,enabled=!state.busy)
        }
        if(row!=null){
            FCard("Tinjau #${shortId(row.text("applicationId").ifBlank{state.verificationUser})}"){
                FBadge(when(row.text("status")){"menunggu"->"Menunggu keputusan admin";"disetujui"->"Disetujui";else->"Ditolak"},if(row.text("status")=="menunggu")"orange" else "green")
                FKeyValue("Pemohon",row.text("dummyName").ifBlank{"Belum tersedia"});FKeyValue("Nama toko",row.text("storeName"));FKeyValue("Lokasi",row.text("pickupLocation").ifBlank{"Belum tersedia"});FKeyValue("Email",row.text("email"))
                FText("Dokumen pengembangan hanya untuk pemeriksaan data ilustratif, bukan pemeriksaan identitas nyata.",10f,color=FMuted)
                Column(Modifier.fillMaxWidth().background(Color(0xFFEDF5FF),RoundedCornerShape(12.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    FText("KTP DUMMY · CONTOH",16f,700,Color(0xFF1766A2))
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        Box(Modifier.size(58.dp,66.dp).background(Color.White,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){FIcon(R.drawable.admin_user,22.dp)}
                        Column(verticalArrangement=Arrangement.spacedBy(6.dp)){FText("Nama: ${row.text("dummyName")}",12f);FText("NIK: ${row.text("dummyNik")}",12f);FText("Bukan dokumen identitas asli",10f,color=FMuted)}
                    }
                }
                FKeyValue("Berkas",row.text("fileName").ifBlank{"Belum ada berkas"});FKeyValue("Ukuran / dikirim","${shortFileSize(row.long("fileSizeBytes"))} · ${dateTimeLabel(row.text("submittedAt"))}")
                FButton("Perbesar dokumen dummy",onDocument,primary=false,enabled=row.flag("hasDocument") && !state.busy)
                if(row.text("rejectionReason").isNotBlank())BNotice("Alasan penolakan",row.text("rejectionReason"),"orange")
            }
            FCard("Pemeriksaan & keputusan"){
                listOf(Triple("Data toko lengkap",nameCheck,{v:Boolean->nameCheck=v}),Triple("Lokasi dan layanan tercantum",locationCheck,{v:Boolean->locationCheck=v}),Triple("Cocokkan keterbacaan identitas dan pemilik",documentCheck,{v:Boolean->documentCheck=v})).forEach{(label,value,change)->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Checkbox(value,change,Modifier.size(18.dp),enabled=!state.busy && row.text("status")=="menunggu",colors=CheckboxDefaults.colors(checkedColor=FGreen));FText(label,12f)}}
                FButton("Setujui verifikasi petani",{confirm=true},enabled=!state.busy && checked && row.flag("hasDocument") && row.text("status")=="menunggu")
                FRule();FField("Alasan penolakan (wajib)",reason,{reason=it.take(1000)},"Jelaskan bagian data yang harus diperbaiki",multiline=true,enabled=row.text("status")=="menunggu")
                FButton("Tolak dengan alasan",{confirm=false},primary=false,enabled=!state.busy && reason.isNotBlank() && row.text("status")=="menunggu")
            }
        }
        BNotice("Keputusan dapat ditelusuri","Persetujuan membuka syarat verifikasi untuk penerbitan produk. Penolakan memerlukan alasan; akun yang disuspensi tetap dibatasi.")
    }
    confirm?.let{approved->AConfirm(if(approved)"Setujui verifikasi petani?" else "Tolak pengajuan?","Keputusan untuk ${row?.text("storeName").orEmpty()} akan disimpan di server.",cancel={confirm=null}){confirm=null;onDecision(state.verificationUser,if(approved)"Disetujui" else "Ditolak",reason,!approved)}}
}
@Composable fun AdminUsersScreen(decisions:Map<String,String>,onBack:()->Unit,onDecision:(String,String,String,Boolean)->Unit,state:AdminUiState=AdminUiState(),onFilter:(String,String,String)->Unit={_,_,_->},onSelect:(String)->Unit={},onMore:()->Unit={}) {
    var query by rememberSaveable{mutableStateOf(state.usersQuery)};val u=state.user
    var reason by rememberSaveable(u?.text("id"),u?.text("status")){mutableStateOf("")};var confirm by remember{mutableStateOf(false)}
    val filter=if(state.usersStatus=="suspended")"suspended" else state.usersRole
    APage("Pengguna & status akun","Kelola akses tanpa menghambat refund lama",onBack){
        ASearch(query,{query=it.take(100)},"Cari nama / email pengguna"){onFilter(state.usersRole,state.usersStatus,query)}
        AChips(listOf("" to "Semua","petani" to "Petani","pembeli" to "Pembeli","suspended" to "Disuspensi"),filter){onFilter(if(it=="suspended")"" else it,if(it=="suspended")"suspended" else "",query)}
        FCard("Hasil pengguna"){
            if(state.users.isEmpty())FText("Tidak ada pengguna pada filter ini.",12f,color=FMuted)
            state.users.forEachIndexed{i,row->AMenu(R.drawable.admin_user,"${row.text("displayName").ifBlank{row.text("email")}} · #${shortId(row.text("id"))}","${row.text("role")} · ${if(row.text("status")=="active")"Aktif" else "Disuspensi"}${if(u?.text("id")==row.text("id"))" · Dipilih" else ""}"){onSelect(row.text("id"))};if(i<state.users.lastIndex)FRule()}
            if(state.usersMore)FButton("Muat pengguna lainnya",onMore,primary=false,enabled=!state.busy)
        }
        if(u!=null){
            FCard("Detail #${shortId(u.text("id"))}"){
                FBadge("${u.text("ktpStatus").takeIf{it.isNotBlank()}?.let{"KTP $it · "}.orEmpty()}${if(u.text("status")=="active")"Akun aktif" else "Disuspensi"}",if(u.text("status")=="active")"green" else "red")
                FKeyValue("Nama pengguna",u.text("ownerName").ifBlank{u.text("displayName")});FKeyValue("Email",u.text("email"));if(u.text("storeName").isNotBlank())FKeyValue("Toko",u.text("storeName"));if(u.text("pickupLocation").isNotBlank())FKeyValue("Lokasi",u.text("pickupLocation"))
                FKeyValue("Refund berjalan",u.long("activeRefundCount").toString());FText("Rekening dan dokumen pribadi tidak ditampilkan pada daftar pengguna.",11f,color=FMuted)
            }
            if(u.text("role")!="admin")FCard(if(u.text("status")=="active")"Tindakan pada akun aktif" else "Pemulihan akun disuspensi"){
                if(u.text("suspensionReason").isNotBlank())FText("Alasan: ${u.text("suspensionReason")}",12f,color=FMuted)
                FField(if(u.text("status")=="active")"Alasan suspensi (wajib)" else "Catatan pemulihan",reason,{reason=it.take(1000)},"Tulis hasil pemeriksaan dan referensi laporan",multiline=true,enabled=!state.busy)
                FButton(if(u.text("status")=="active")"Suspensi akun dengan alasan" else "Tinjau & pulihkan #${shortId(u.text("id"))}",{confirm=true},primary=false,enabled=reason.isNotBlank() && !state.busy)
                FText("Suspensi memblokir produk aktif dan transaksi baru. Pesanan lama serta refund yang belum tuntas tetap dapat ditangani.",11f,color=FMuted)
            }
        }
        state.users.firstOrNull{it.text("status")=="suspended" && it.text("id")!=u?.text("id")}?.let{suspended->FCard("Pemulihan akun disuspensi"){
            FText("#${shortId(suspended.text("id"))} · ${suspended.text("displayName").ifBlank{suspended.text("email")}}",12f)
            FBadge("Disuspensi","red")
            FText("Periksa detail dan hasil peninjauan akun ini sebelum memulihkan aksesnya.",12f,color=FMuted)
            FButton("Tinjau akun #${shortId(suspended.text("id"))}",{onSelect(suspended.text("id"))},primary=false,enabled=!state.busy)
        }}
        BNotice("Semua perubahan masuk log audit","Admin, waktu, status sebelumnya, status baru, dan alasan disimpan. Suspensi bukan keputusan bahwa dana refund telah diterima.")
    }
    if(confirm && u!=null)AConfirm(if(u.text("status")=="active")"Suspensi akun ini?" else "Pulihkan akun ini?","${u.text("displayName").ifBlank{u.text("email")}} · $reason",cancel={confirm=false}){confirm=false;onDecision(u.text("id"),if(u.text("status")=="active")"Disuspensi" else "Aktif",reason,true)}
}
/** Legacy graph adapter; no fabricated defaults or in-memory success state. */
@Composable fun AdminSettingsScreen(values:Map<String,String>,onBack:()->Unit,onSave:(Map<String,String>)->Unit) {
    AdminSettingsLiveScreen(values,onBack,onSave)
}
@Composable internal fun AdminSettingsLiveScreen(values:Map<String,String>,onBack:()->Unit,onSave:(Map<String,String>)->Unit,settings:JsonObject?=null,busy:Boolean=false,onAudit:()->Unit={},onRefresh:()->Unit={}) {
    val snapshot=settings?.toString().orEmpty()
    var limit by rememberSaveable(snapshot){mutableStateOf(settings?.long("transactionLimit")?.toString().orEmpty())}
    var phone by rememberSaveable(snapshot){mutableStateOf(settings?.text("csWhatsapp").orEmpty())}
    var reason by rememberSaveable(snapshot){mutableStateOf("")}
    val old=settings?.long("transactionLimit");val parsed=limit.toLongOrNull();var confirm by remember{mutableStateOf(false)}
    val valid=settings!=null && parsed!=null && parsed>0 && adminPhoneValid(phone) && (parsed==old || reason.isNotBlank())
    APage("Pengaturan admin","Aturan transaksi & kanal dukungan",onBack){
        if(settings==null)AEmpty("Pengaturan belum dimuat dari server.")
        FCard("Batas awal transaksi"){
            FBadge("Berlaku untuk checkout baru")
            AdminCurrencyField(limit,{limit=it.take(19)},enabled=!busy && settings!=null)
            parsed?.takeIf{it>0}?.let{FText("Nilai saat ini: ${money(it)}, termasuk ongkos kirim.",11f,color=FMuted)}
            FKeyValue("Yang dihitung","Σ kg × Rp/kg + ongkir",size=12f)
            FText("Ambil sendiri: ongkir Rp0. Pesanan dengan total melebihi batas tidak dapat dibuat. Perubahan tidak mengubah total pesanan lama.",12f,color=FMuted)
            BNotice("Validasi saat disimpan","Harus berupa nominal rupiah bulat, lebih dari Rp0. Sistem menolak nilai kosong atau bukan angka.")
            if(parsed!=old)FField("Alasan perubahan limit (wajib)",reason,{reason=it.take(1000)},"Jelaskan alasan perubahan",multiline=true,enabled=!busy)
        }
        FCard("WhatsApp customer service"){
            FField("Nomor WhatsApp CS",phone,{phone=it.take(50)},"+62 ...",keyboard=KeyboardType.Phone,enabled=!busy && settings!=null)
            FText("Gunakan kode negara dan angka yang valid. Kontak dibuka setelah tiket eskalasi dicatat di Panenku.",12f,color=FMuted)
            FRule();FText("Pesan aman untuk CS",14f,600)
            FText("ID tiket, ID pesanan/refund, dan ringkasan masalah saja. Jangan menyertakan rekening, KTP, atau alamat pribadi.",12f)
        }
        FButton("Simpan pengaturan admin",{confirm=true},enabled=valid && !busy)
        FCard("Konteks audit"){
            FKeyValue("Versi pengaturan",settings?.long("settingsVersion")?.let{"Versi $it"} ?: "Belum tersedia")
            FKeyValue("Aktor perubahan",settings?.text("lastChangedByName").orEmpty().ifBlank{settings?.text("lastChangedByEmail").orEmpty().ifBlank{"Belum tersedia"}})
            FText("${settings?.text("lastChangedAt").orEmpty().takeIf{it.isNotBlank()}?.let{dateTimeLabel(it)+" · "}.orEmpty()}Setiap penyimpanan mencatat nilai lama, nilai baru, waktu, dan admin yang mengubahnya. Data pribadi tidak ditampilkan di ringkasan.",11f,color=FMuted)
            AMenu(R.drawable.admin_file,"Riwayat perubahan","Lihat aturan dan kontak sebelumnya",onAudit)
        }
        FButton("Muat ulang pengaturan",onRefresh,primary=false,enabled=!busy)
    }
    if(confirm)AConfirm("Simpan pengaturan admin?","Limit ${parsed?.let{money(it)}} dan nomor CS akan disimpan di server. Perubahan limit berlaku untuk checkout baru.",confirm="Simpan",cancel={confirm=false}){confirm=false;onSave(mapOf("Limit" to limit,"WhatsApp" to phone,"Reason" to reason))}
}
