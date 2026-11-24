package id.panenku.app.ui.admin

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.buyer.*

@Composable internal fun AdminCommoditiesScreen(state:AdminUiState,back:()->Unit,save:(String?,String,Boolean,()->Unit)->Unit,moderation:()->Unit) {
    var query by rememberSaveable{mutableStateOf("")};var filter by rememberSaveable{mutableStateOf("all")};var selected by rememberSaveable{mutableStateOf<String?>(null)}
    val row=state.commodities.firstOrNull{it.text("id")==selected}
    var name by rememberSaveable(selected,row?.toString()){mutableStateOf(row?.text("name").orEmpty())};var active by rememberSaveable(selected,row?.toString()){mutableStateOf(row?.flag("isActive") ?: true)};var disabling by remember{mutableStateOf(false)}
    val visible=state.commodities.filter{it.text("name").contains(query,true) && when(filter){"active"->it.flag("isActive");"inactive"->!it.flag("isActive");else->true}}
    APage("Kelola komoditas","Kategori rapi, harga tetap dalam Rp/kg",back){
        ASearch(query,{query=it},"Cari komoditas"){}
        AChips(listOf("all" to "Semua (${state.commodities.size})","active" to "Aktif (${state.commodities.count{it.flag("isActive")}})","inactive" to "Nonaktif (${state.commodities.count{!it.flag("isActive")}})"),filter){filter=it}
        FCard("Daftar komoditas"){
            if(visible.isEmpty())FText("Belum ada komoditas pada filter ini.",12f,color=FMuted)
            visible.forEachIndexed{i,c->Row(Modifier.fillMaxWidth().clickable(role=Role.Button){selected=c.text("id")},horizontalArrangement=Arrangement.SpaceBetween){FText(c.text("name")+(if(c.text("id")==selected)" · Dipilih" else ""),12f,color=FMuted);FText(if(c.flag("isActive"))"Aktif" else "Nonaktif",12f,if(c.text("id")==selected)600 else 400,color=if(c.flag("isActive"))FForest else FMuted)};if(i<visible.lastIndex)FRule()}
            FButton("+ Tambah komoditas baru",{selected=null;name="";active=true},primary=false,enabled=!state.busy)
        }
        FCard(if(row==null)"Tambah komoditas" else "Edit komoditas · ${row.text("name")}"){
            FField("Nama komoditas",name,{name=it.take(100)},"Nama kategori",enabled=!state.busy)
            FField("Deskripsi singkat","",{},"Belum tersedia pada layanan komoditas",enabled=false)
            FField("Satuan pembanding","Kilogram (kg) · Rp/kg",{},enabled=false)
            AChips(listOf("active" to "Aktif","inactive" to "Nonaktif"),if(active)"active" else "inactive"){active=it=="active"}
            FText("Komoditas aktif dapat dipilih saat petani membuat atau mengedit produk.",12f,color=FMuted)
            FButton(if(row==null)"Simpan komoditas baru" else "Simpan perubahan ${row.text("name")}",{save(selected,name,active){selected=null;name="";active=true}},enabled=name.isNotBlank() && !state.busy)
        }
        row?.let{FCard("Nonaktifkan tanpa menghapus"){
            FField("Alasan nonaktif","",{},"Belum dapat disimpan oleh layanan komoditas",enabled=false)
            FButton("Nonaktifkan komoditas ${it.text("name")}",{disabling=true},primary=false,enabled=it.flag("isActive") && !state.busy)
            FText("Komoditas tidak lagi dapat dipilih untuk produk baru. Riwayat pesanan lama tetap tersimpan. Tinjau produk terkait secara terpisah.",12f,color=FMuted)
        }}
        BNotice("Perubahan tercatat","Tidak ada penghapusan permanen. Nama dan status disimpan di server; deskripsi serta alasan komoditas belum didukung.")
        FButton("Buka moderasi produk",moderation,primary=false)
    }
    if(disabling && row!=null)AConfirm("Nonaktifkan ${row.text("name")}?","Komoditas akan dinonaktifkan tanpa menghapus riwayatnya.",cancel={disabling=false}){disabling=false;save(row.text("id"),row.text("name"),false){}}
}
@Composable internal fun AdminDisputesScreen(state:AdminUiState,back:()->Unit,select:(String)->Unit,more:()->Unit,evidence:()->Unit,decide:(Boolean,String)->Unit,user:(String)->Unit) {
    var filter by rememberSaveable{mutableStateOf("open")};var reason by rememberSaveable(state.dispute?.text("id")){mutableStateOf("")};var confirmation by remember{mutableStateOf<Boolean?>(null)}
    val rows=when(filter){"closed"->state.decidedDisputes;"review"->emptyList();else->state.disputes}
    val d=state.dispute;val r=state.refund
    APage("Sengketa refund","${state.disputes.size} tiket dimuat · Tinjau kronologi",back){
        AChips(listOf("open" to "Terbuka","review" to "Ditinjau","closed" to "Ditutup sesi ini"),filter){filter=it}
        if(filter=="review")BNotice("Status peninjauan","Server saat ini menyediakan tiket terbuka dan keputusan akhir. Belum ada penyimpanan status ditinjau atau catatan sementara.")
        if(filter=="closed")FText("Hanya keputusan yang sudah diterima server pada sesi ini. Riwayat lengkap tersedia di audit.",11f,color=FMuted)
        FCard("Antrean tiket"){
            if(rows.isEmpty())FText("Tidak ada tiket pada filter ini.",12f,color=FMuted)
            rows.forEachIndexed{i,t->AMenu(R.drawable.admin_help,"#${shortId(t.text("id"))}${if(d?.text("id")==t.text("id"))" · Dipilih" else ""}","${t.text("reason").take(80)} · #${shortId(t.text("refundId"))}"){select(t.text("id"))};if(i<rows.lastIndex)FRule()}
            if(filter=="open" && state.disputeMore)FButton("Muat tiket lainnya",more,primary=false,enabled=!state.busy)
        }
        if(d!=null){
            FCard("Detail tiket #${shortId(d.text("id"))}"){
                FBadge(if(d.text("status")=="terbuka")"Terbuka · Belum ada keputusan admin" else "Ditutup admin",if(d.text("status")=="terbuka")"orange" else "green")
                FKeyValue("Pesanan / refund","#${shortId(d.text("orderId"))} / #${shortId(d.text("refundId"))}")
                FKeyValue("Pembeli",shortId(d.text("buyerId")));r?.let{FKeyValue("Petani",shortId(it.text("farmerId")));FKeyValue("Dana disengketakan",money(it.long("refundAmount")),true)}
                FText(d.text("reason"),12f,color=FMuted)
                if(d.text("decisionReason").isNotBlank())FText("Keputusan: ${d.text("decisionReason")}",12f,color=FForest)
            }
            FCard("Kronologi & bukti"){
                AHistory(r?.list("history").orEmpty())
                FText("Tiket dibuat ${dateTimeLabel(d.text("createdAt"))}",11f,color=FMuted)
                FButton("Tinjau seluruh bukti",evidence,primary=false,enabled=!state.busy && r!=null)
            }
            FCard("Catatan keputusan admin"){
                FField("Hasil pemeriksaan / tindak lanjut",reason,{reason=it.take(1000)},"Tulis hasil pemeriksaan dan alasan keputusan",multiline=true,enabled=!state.busy && d.text("status")=="terbuka")
                FText("Catatan disimpan bersama keputusan. Tidak ada penyimpanan catatan sementara di server.",11f,color=FMuted)
                FButton("Setujui refund",{confirmation=true},enabled=!state.busy && reason.isNotBlank() && d.text("status")=="terbuka")
                FButton("Tutup tiket dengan penjelasan",{confirmation=false},primary=false,enabled=!state.busy && reason.isNotBlank() && d.text("status")=="terbuka")
                r?.text("farmerId")?.takeIf{it.isNotBlank()}?.let{id->FButton("Tinjau akun petani secara terpisah",{user(id)},primary=false,enabled=!state.busy)}
            }
        }
        BNotice("Ditutup admin ≠ Dana diterima","Penutupan mengakhiri penanganan tiket, bukan mengonfirmasi dana refund sudah diterima pembeli. Refund lama tetap dapat diselesaikan meski akun disuspensi.","orange")
        FText("Audit menyimpan admin, waktu, alasan, dan referensi bukti. Tidak ada pemindahan dana otomatis oleh admin.",10f,color=FMuted)
    }
    confirmation?.let{approved->AConfirm(if(approved)"Setujui refund ini?" else "Tutup sengketa ini?",reason,confirm="Simpan keputusan",cancel={confirmation=null}){confirmation=null;decide(approved,reason)}}
}
@Composable internal fun AdminModerationScreen(state:AdminUiState,back:()->Unit) {
    var filter by rememberSaveable{mutableStateOf("review")}
    val report=state.selectedReport
    APage("Moderasi produk","Tinjau laporan dan alasan perbaikan",back){
        AChips(listOf("review" to "Perlu tinjau","action" to "Ditindak","clear" to "Tidak melanggar"),filter){filter=it}
        FCard(report?.text("id")?.let{"Laporan #${shortId(it)}"} ?: "Laporan produk"){
            if(report==null)FText("Laporan produk belum tersedia dari layanan admin.",12f,color=FMuted)
            else {FBadge(report.text("category"),"orange");FKeyValue("Dilaporkan",dateTimeLabel(report.text("createdAt")));FText(report.text("reason"),13f)}
        }
        FCard("Produk yang dilaporkan"){
            val p=report?.obj("product")
            if(p!=null){Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){FPhoto(p.text("photoAccessUrl"),p.text("name"),Modifier.size(90.dp));Column(verticalArrangement=Arrangement.spacedBy(6.dp)){FText(p.text("name"),14f,600);FText("${money(p.long("pricePerKg"))}/kg",12f);FText(p.text("storeName"),11f,color=FMuted)}};FKeyValue("Kategori terpasang",p.text("commodity"));FKeyValue("Stok tersedia","${quantity(p.text("stockAvailable"))} kg")}
            else FText("Pilih laporan untuk menampilkan produk dan toko terkait.",12f,color=FMuted)
        }
        FCard("Keputusan moderasi"){
            FField("Alasan penonaktifan (wajib)","",{},"Layanan keputusan moderasi belum tersedia",multiline=true,enabled=false)
            FButton("Nonaktifkan produk",{},enabled=false);FButton("Tandai tidak melanggar",{},primary=false,enabled=false)
            FText("Keputusan baru dapat dikirim setelah layanan moderasi tersedia. Tidak ada produk atau akun yang diubah dari halaman ini.",12f,color=FMuted)
        }
        BNotice("Layanan moderasi belum tersedia","Backend saat ini belum menyediakan daftar laporan maupun tindakan moderasi produk. Tampilan ini disiapkan tanpa menampilkan keputusan palsu.")
        FCard("Konteks audit sebelumnya"){AHistory(state.audit.filter{it.text("targetType")=="product"})}
    }
}
@Composable internal fun AdminOrdersScreen(state:AdminUiState,back:()->Unit,reports:()->Unit) {
    var query by rememberSaveable{mutableStateOf("")};var filter by rememberSaveable{mutableStateOf("all")};var selectedId by rememberSaveable{mutableStateOf("")};val order=state.orders.firstOrNull{it.text("orderId")==selectedId} ?: state.selectedOrder
    APage("Pantau pesanan","Pemantauan transaksi lintas toko",back){
        ASearch(query,{query=it},"Cari ID pesanan / pembeli / toko"){}
        AChips(listOf("all" to "Semua","payment" to "Pembayaran","fulfillment" to "Pemenuhan","refund" to "Refund"),filter){filter=it}
        FCard("Pesanan terbaru"){
            if(state.orders.isEmpty())FText("Daftar seluruh pesanan belum tersedia dari layanan admin.",12f,color=FMuted)
            state.orders.filter{it.toString().contains(query,true) && when(filter){"payment"->it.text("orderStatus") in setOf("menunggu_pembayaran","verifikasi_pembayaran");"fulfillment"->it.text("orderStatus") in setOf("diproses","diantar","menunggu_penerimaan");"refund"->it.text("orderId")==state.refund?.text("orderId");else->true}}.forEachIndexed{i,row->AMenu(when(row.text("orderStatus")){"verifikasi_pembayaran"->R.drawable.admin_bank;"menunggu_penerimaan","diantar"->R.drawable.buyer8_truck;else->R.drawable.admin_file},"#${shortId(row.text("orderId"))} · ${money(row.long("totalPayment"))}","${dateTimeLabel(row.text("createdAt"))} · ${row.text("orderStatus").replace('_',' ')}"){selectedId=row.text("orderId")};if(i<state.orders.lastIndex)FRule()}
        }
        FCard(order?.let{"Detail #${shortId(it.text("orderId"))}"} ?: "Detail pesanan"){
            if(order==null)FText("Detail pesanan akan ditampilkan setelah layanan pemantauan tersedia.",12f,color=FMuted)
            else {FBadge(order.text("orderStatus").replace('_',' '),"orange");FKeyValue("Pembeli",order.text("buyerName"));FKeyValue("Petani / toko",order.text("storeName"));BItems(order.list("items"));FRule();FKeyValue("Produk + ongkir","${money(order.long("subtotalItems"))} + ${money(order.long("deliveryFee"))}");FKeyValue("Total tersimpan",money(order.long("totalPayment")),true);FText("${order.text("scheduledDate")} · ${order.text("scheduledTimeSlot")}",12f,color=FMuted)}
        }
        FCard("Riwayat waktu & aktor"){AHistory(order?.list("history").orEmpty())}
        BNotice("Admin memantau, bukan menyetujui dana","Persetujuan bukti pembayaran dilakukan petani. Backend saat ini belum memiliki endpoint daftar/detail seluruh pesanan admin.")
        FButton("Lihat laporan terkait pesanan",reports,primary=false)
    }
}
