package id.panenku.app.ui.connected.buyer

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.*

@Composable internal fun BuyerStoreScreen(s:ConnectedState,vm:ConnectedViewModel,back:()->Unit) {
    val detail=s.buyerStore;val product=detail?.obj("product");val context=LocalContext.current
    var tab by rememberSaveable(product?.text("farmerId")){mutableStateOf("Produk")}
    var error by remember{mutableStateOf<String?>(null)}
    BPage("Profil toko","Segar langsung dari kebun",back,paper=FPaper){
        if(detail==null || product==null) FText("Memuat profil toko…",12f,color=FMuted)
        else {
            FCard {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Box(Modifier.size(60.dp).background(Color(0xFFE3F7E1),CircleShape),contentAlignment=Alignment.Center){FIcon(R.drawable.buyer8_store,22.dp)}
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){FText(product.text("storeName"),18f,700,line=27f);FText("Petani Panenku",12f,color=FMuted);if(product.flag("farmerVerified"))FBadge("Petani terverifikasi")}
                }
                FRule()
                BMenuRow(R.drawable.buyer_shared_location,detail.text("pickupLocation").ifBlank{"Lokasi pengambilan belum tersedia"},"Lokasi ambil dari petani"){
                    if(detail.text("pickupLocation").isNotBlank()) runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q=${Uri.encode(detail.text("pickupLocation"))}")))}.onFailure{error="Peta belum dapat dibuka."}
                }
                FKeyValue("Jam layanan",detail.getAsJsonArray("timeSlots")?.joinToString(" · "){it.asString}.orEmpty().ifBlank{"Belum tersedia"})
                FText(if(detail.flag("shippingEnabled")) "Antar area ${detail.getAsJsonArray("serviceArea")?.joinToString(", "){it.asString}.orEmpty()} · ${money(detail.long("deliveryFee"))}/pesanan. Ambil sendiri Rp0." else "Ambil sendiri di lokasi petani. Layanan antar belum tersedia.",12f)
                val summary=detail.obj("farmerRating")
                FKeyValue("Penilaian pembeli",if(summary.long("reviewCount")>0)"★ ${summary.text("average")} · ${summary.long("reviewCount")} ulasan" else "Belum ada ulasan",true)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf("Produk" to s.buyerStoreProducts.size.toLong(),"Ulasan" to detail.obj("farmerRating").long("reviewCount")).forEach{(label,n)->
                    Box(Modifier.background(if(tab==label)FGreen else Color.White,RoundedCornerShape(999.dp)).border(1.dp,FBorder,RoundedCornerShape(999.dp)).clickable{tab=label}.padding(12.dp,8.dp)){FText("$label ($n)",12f,600,if(tab==label)Color.White else FMuted)}
                }
            }
            if(tab=="Produk"){
                if(s.buyerStoreProducts.isEmpty()) FCard {FText("Belum ada produk yang tersedia untuk dibeli.",12f,color=FMuted)}
                s.buyerStoreProducts.forEach{p->FCard {
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                        FPhoto(p.text("photoAccessUrl"),p.text("name"),Modifier.size(72.dp))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){FText(p.text("name"),14f,600);FText("${money(p.long("pricePerKg"))}/kg",14f,700,FForest);FText("Tersedia ${quantity(p.text("stockAvailable"))} kg · Siap ${dateLabel(p.text("readyDate"),true)}",12f,color=FMuted)}
                    }
                    BButton(if(p.flag("availableForPurchase"))"Tambah ke keranjang" else "Stok tidak tersedia",{vm.add(p.text("id"),"1")},primary=false,enabled=!s.busy && p.flag("availableForPurchase"))
                }}
            }
            FCard("Ulasan pembeli"){
                if(s.buyerStoreReviews.isEmpty()) FText(if(detail.obj("farmerRating").long("reviewCount")>0)"Rating keseluruhan ditampilkan di atas. Rincian ulasan pembeli belum tersedia pada layanan toko ini." else "Belum ada ulasan pembeli untuk toko ini.",12f,color=FMuted)
                s.buyerStoreReviews.forEach{review->Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){FText(review.text("buyerName"),12f,color=FMuted);FText("★".repeat(review.long("rating").toInt().coerceIn(0,5)),12f)};FText(review.text("comment"),12f);FRule()}}
                FText("Ulasan berasal dari pesanan yang sudah diterima pembeli.",10f,color=FMuted)
            }
        }
    }
    error?.let{AlertDialog(onDismissRequest={error=null},text={Text(it)},confirmButton={TextButton(onClick={error=null}){Text("OK")}})}
}

@Composable internal fun BuyerAccountScreen(s:ConnectedState,vm:ConnectedViewModel,back:()->Unit,addresses:()->Unit,orders:()->Unit,refunds:()->Unit,notifications:(()->Unit)?=null) {
    var name by rememberSaveable(s.user?.id,s.profile?.text("fullName")){mutableStateOf(s.profile?.text("fullName").orEmpty())}
    var phone by rememberSaveable(s.user?.id,s.profile?.text("contactPhone")){mutableStateOf(s.profile?.text("contactPhone").orEmpty())}
    var logout by remember{mutableStateOf(false)};var privacy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)};val context=LocalContext.current
    BPage("Akun saya","Atur identitas dan kebutuhan belanjamu",back,brand=false,paper=FPaper){
        FCard {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Box(Modifier.size(60.dp).background(Color(0xFFE3F7E1),CircleShape),contentAlignment=Alignment.Center){FText(name.split(' ').filter(String::isNotBlank).take(2).map{it.first().uppercaseChar()}.joinToString("").ifBlank{"P"},22f,700,FForest)}
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)){FText(name.ifBlank{"Akun pembeli"},14f,600);FBadge("Akun pembeli")}
            }
            FField("Nama pembeli / usaha",name,{name=it},enabled=s.profile!=null && !s.busy)
            FField("Email",s.user?.email.orEmpty(),{},enabled=false)
            FField("Nomor telepon",phone,{phone=it},keyboard=KeyboardType.Phone,enabled=s.profile!=null && !s.busy)
            FText("Gunakan nomor yang dapat dihubungi untuk pesananmu.",10f,color=FMuted)
            BButton("Simpan profil",{vm.buyerSaveIdentity(name,phone)},enabled=!s.busy && s.profile!=null && name.isNotBlank() && name.length<=255 && phone.length<=20)
        }
        FCard("Belanja & transaksi"){
            BMenuRow(R.drawable.buyer_shared_location,"Alamat tersimpan","Kelola alamat pengiriman",addresses)
            FRule();BMenuRow(R.drawable.buyer8_menu_orders,"Pesanan saya","Pantau jadwal dan penerimaan",orders)
            FRule();BMenuRow(R.drawable.buyer8_help,"Refund & bantuan","Lihat pengajuan dan tiket eskalasi",refunds)
        }
        FCard("Preferensi & keamanan"){
            BMenuRow(R.drawable.buyer8_bell,"Notifikasi transaksi",if(notifications==null)"Pengaturan notifikasi Android" else "Pembayaran, pesanan, refund"){
                if(notifications!=null)notifications() else
                runCatching{context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}.onFailure{error="Pengaturan notifikasi belum dapat dibuka."}
            }
            FRule();BMenuRow(R.drawable.buyer8_gear,"Kata sandi & privasi","Kelola akses dan pengaturan data"){privacy=true}
            FRule();BMenuRow(R.drawable.buyer8_help,"Pusat bantuan","Panduan belanja dan hubungi CS",refunds)
        }
        BButton("Keluar dari akun",{logout=true},primary=false,enabled=!s.busy)
        FText("Panenku · Belanja hasil panen langsung dari petani.",10f,color=FMuted)
    }
    if(logout) AlertDialog(onDismissRequest={logout=false},title={Text("Keluar dari akun?")},text={Text("Kamu dapat masuk lagi untuk melanjutkan pesanan.")},confirmButton={TextButton(onClick={logout=false;vm.logout()}){Text("Keluar")}},dismissButton={TextButton(onClick={logout=false}){Text("Kembali")}})
    if(privacy) AlertDialog(onDismissRequest={privacy=false},title={Text("Kata sandi & privasi")},text={Text("Perubahan kata sandi dibantu pengelola akun melalui pusat bantuan. Pengaturan izin dan data perangkat tersedia melalui pengaturan Android.")},confirmButton={TextButton(onClick={privacy=false;runCatching{context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}.onFailure{error="Pengaturan aplikasi belum dapat dibuka."}}){Text("Pengaturan Android")}},dismissButton={TextButton(onClick={privacy=false;refunds()}){Text("Pusat bantuan")}})
    error?.let{AlertDialog(onDismissRequest={error=null},text={Text(it)},confirmButton={TextButton(onClick={error=null}){Text("OK")}})}
}

@Composable internal fun BuyerRefundHub(s:ConnectedState,vm:ConnectedViewModel,back:()->Unit,open:(String)->Unit) {
    BPage("Refund & bantuan","Lihat pengajuan pengembalian dan eskalasi",back,brand=false,paper=FPaper){
        if(s.refunds.isEmpty()) BNotice("Belum ada pengajuan refund","Pengajuan yang berhasil dikirim dari pesanan akan tampil di sini.")
        s.refunds.forEach{r->FCard("#${shortId(r.text("id"))}"){
            FBadge(refundStatus(r.text("status")),if(r.text("status")=="selesai")"green" else "orange")
            FKeyValue("Pesanan","#${shortId(r.text("orderId"))}");FKeyValue("Pengembalian penuh",money(r.long("refundAmount")),true)
            FText(r.text("reason"),12f,color=FMuted)
            BButton("Lihat status refund",{open(r.text("id"))},primary=false,enabled=!s.busy)
        }}
        if(s.more)BButton("Muat refund berikutnya",{vm.refunds(s.page+1)},primary=false,enabled=!s.busy)
        FText("Perbarui pengajuan",12f,600,FGreen,Modifier.clickable(enabled=!s.busy){vm.refunds()})
    }
}
