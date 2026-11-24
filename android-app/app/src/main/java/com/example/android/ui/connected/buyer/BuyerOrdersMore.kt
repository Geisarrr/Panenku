package id.panenku.app.ui.connected.buyer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.*

@Composable internal fun BuyerOrdersScreen(s:ConnectedState,vm:ConnectedViewModel,back:()->Unit,open:(JsonObject)->Unit,payment:(JsonObject)->Unit,review:(JsonObject)->Unit,reorder:(JsonObject)->Unit) {
    var tab by rememberSaveable{mutableStateOf("Semua")}
    val ended=setOf("selesai","ditolak","dibatalkan","kedaluwarsa","gagal_dipenuhi")
    val rows=s.orders.filter{when(tab){"Aktif"->it.text("orderStatus") !in ended;"Selesai"->it.text("orderStatus")=="selesai";else->true}}
    BPage("Pesanan","Pantau pesanan hasil panenmu",back,brand=false,paper=FPaper){
        Row(Modifier.fillMaxWidth().background(Color(0xFFF1F1F1),RoundedCornerShape(10.dp)).padding(4.dp)){
            listOf("Semua","Aktif","Selesai").forEach{t->Box(Modifier.weight(1f).background(if(tab==t)Color.White else Color.Transparent,RoundedCornerShape(8.dp)).clickable(role=Role.Tab){tab=t}.padding(vertical=9.dp),contentAlignment=Alignment.Center){FText(t,12f,if(tab==t)600 else 400,if(tab==t)FForest else FMuted)}}
        }
        if(rows.isEmpty()) BNotice(if(s.busy)"Memuat pesanan…" else "Belum ada pesanan", "Pesanan yang sesuai pilihanmu akan tampil di sini.")
        rows.forEach{order->
            val status=order.text("orderStatus")
            FCard(gap=10.dp){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){FText("#${shortId(order.text("orderId"))}",13f,700);FText(dateLabel(order.text("createdAt"),true),10f,color=FMuted)}
                FBadge(buyerStatus(status),when(status){"menunggu_petani","verifikasi_pembayaran"->"orange";"menunggu_pembayaran"->"blue";else->"green"})
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){order.list("items").take(2).forEach{item->FPhoto(s.products.firstOrNull{it.text("id")==item.text("productId")}?.text("photoAccessUrl").orEmpty(),item.text("productNameSnapshot"),Modifier.size(38.dp))}}
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                        FText(order.list("items").joinToString(" & "){it.text("productNameSnapshot")},12f,maxLines=2)
                        FText("${order.list("items").size} produk · ${order.text("storeName")}",10f,color=FMuted)
                    }
                }
                FText(when(status){"menunggu_petani"->"Konfirmasi petani paling lambat ${dateTimeLabel(order.text("expiresAt"))}.";"menunggu_pembayaran"->"Petani menyetujui. Unggah bukti sebelum ${dateTimeLabel(order.text("expiresAt"))}.";"verifikasi_pembayaran"->"Bukti diterima, belum terverifikasi. Menunggu pemeriksaan petani.";"selesai"->"Pesanan diterima. Bagaimana hasil panennya?";else->"Status terbaru: ${buyerStatus(status)}."},11f,color=FMuted)
                FRule();FKeyValue("Total pesanan",money(order.long("totalPayment")))
                if(status=="selesai") Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    BOrderAction("Beri Ulasan",{review(order)},Modifier.weight(1f),primary=false)
                    BOrderAction("Beli Lagi",{reorder(order)},Modifier.weight(1f))
                } else BOrderAction(when(status){"menunggu_pembayaran"->"Bayar & Unggah Bukti";"verifikasi_pembayaran"->"Lihat Bukti";"menunggu_petani"->"Lihat Pesanan";else->"Lacak Pesanan"},{if(status in setOf("menunggu_pembayaran","verifikasi_pembayaran"))payment(order) else open(order)},primary=status !in setOf("menunggu_petani","verifikasi_pembayaran"))
                if(status=="selesai") FText("Lihat detail pesanan",11f,600,FGreen,Modifier.clickable{open(order)})
            }
        }
        if(s.more) BButton("Muat pesanan berikutnya",{vm.orders(s.page+1)},primary=false,enabled=!s.busy)
        FText("Perbarui pesanan",12f,600,FGreen,Modifier.clickable(enabled=!s.busy){vm.orders()})
    }
}

@Composable internal fun BuyerCompletedScreen(s:ConnectedState,vm:ConnectedViewModel,back:()->Unit,tracking:()->Unit,review:()->Unit,reorder:()->Unit,store:()->Unit) {
    val order=s.detail
    if(order==null){BPage("Detail Pesanan","Memuat…",back,brand=false,paper=FPaper){FText("Memuat pesanan selesai…")};return}
    BOrderLayout("Detail Pesanan",order.text("orderId"),back,footer={BButton(if(s.buyerReview!=null)"Lihat Ulasan" else "Tulis Ulasan",review,enabled=!s.busy && order.text("orderStatus")=="selesai",height=40.dp)}){
        FCard("Alamat Pengiriman",padding=12.dp,gap=16.dp){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){FIcon(R.drawable.buyer_shared_location,20.dp);Column(verticalArrangement=Arrangement.spacedBy(4.dp)){FText(order.text("buyerName").ifBlank{"Alamat penerima"},14f,600,line=20f);FText(order.text("deliveryAddressSnapshot").ifBlank{order.text("pickupLocationSnapshot")},12f,color=FMuted,line=16f)}}
            FRule();Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FIcon(R.drawable.buyer8_notepad,20.dp);Column(verticalArrangement=Arrangement.spacedBy(4.dp)){FText("Catatan untuk Kurir",14f,600,line=20f);FText(order.text("buyerNotes").ifBlank{"Tidak ada catatan tambahan."},12f,color=FMuted)}}
        }
        FCard(padding=12.dp,gap=16.dp){
            FText("Pesanan Selesai",12f,color=FMuted)
            FText("${order.list("items").size} Produk · Dibeli ${dateLabel(order.text("createdAt"))}",14f,600,line=20f)
            Box(Modifier.fillMaxWidth().padding(vertical=4.dp)){
                Box(Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center).background(Color(0xFFE3F7E1)))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){listOf(R.drawable.buyer8_credit,R.drawable.buyer8_bag,R.drawable.buyer8_truck,R.drawable.buyer8_house,R.drawable.buyer8_seal).forEach{icon->Box(Modifier.size(40.dp).background(Color(0xFFE3F7E1),CircleShape),contentAlignment=Alignment.Center){FIcon(icon,24.dp,tint=FGreen)}}}
            }
            FRule();FText("Lacak Pengiriman",14f,600,FGreen,Modifier.fillMaxWidth().clickable(onClick=tracking),align=TextAlign.Center)
        }
        FCard("Ringkasan Pesanan (${order.list("items").size} Produk)",padding=12.dp,gap=16.dp){
            BOrderItemRows(order.list("items"),s.products)
            FButton("Beli lagi",reorder,height=28.dp,font=13f)
        }
        FCard("Ringkasan Pembayaran",padding=12.dp,gap=12.dp){
            FKeyValue("Subtotal (${order.list("items").size} Produk)",money(order.long("subtotalItems")))
            FKeyValue("Biaya Pemesanan",money(order.long("orderFee")))
            FKeyValue("Biaya Kemasan",money(order.long("packagingFee")))
            FKeyValue("Ongkos Kirim",money(order.long("deliveryFee")))
            FKeyValue("Total Pembayaran",money(order.long("totalPayment")))
            FRule();FKeyValue("Metode Pembayaran",s.destination?.text("bankName")?.ifBlank{"QRIS"}?:"Pembayaran langsung ke petani")
            FKeyValue("Tanggal Transaksi",dateLabel(order.text("createdAt")))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun BuyerReviewScreen(s:ConnectedState,vm:ConnectedViewModel,back:()->Unit) {
    val order=s.detail
    if(order==null){BPage("Ulasan Pesanan","Belum dapat memuat pesanan",back,brand=false,paper=FPaper){FText("Kembali ke detail pesanan untuk mencoba memuat lagi.",12f,color=FMuted)};return}
    var rating by rememberSaveable(order.text("orderId")){mutableStateOf(0)}
    var tags by rememberSaveable(order.text("orderId")){mutableStateOf(listOf<String>())}
    var comment by rememberSaveable(order.text("orderId")){mutableStateOf("")}
    var reviewError by remember{mutableStateOf(false)}
    val existing=s.buyerReview
    val combined=buyerReviewComment(tags.toSet(),comment)
    val allowed=!s.busy && s.buyerReviewLoaded && s.buyerReviewAvailable && existing==null && order.text("orderStatus")=="selesai"
    BOrderLayout("Ulasan Pesanan",order.text("orderId"),back,footer={BButton(if(existing!=null)"Ulasan telah terkirim" else "Kirim",{if(rating !in 1..5)reviewError=true else vm.buyerSubmitReview(order.text("orderId"),rating,tags.toSet(),comment)},enabled=allowed && combined.length<=500,height=40.dp)}){
        FText("Berikan ulasan singkat untuk pesanan ini.",12f,color=FMuted,modifier=Modifier.padding(horizontal=4.dp))
        FCard(padding=12.dp,gap=16.dp){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){FText("Pesanan #${shortId(order.text("orderId"))}",13f);FBadge(buyerStatus(order.text("orderStatus")))};BOrderItemRows(order.list("items"),s.products)}
        if(!s.buyerReviewLoaded) BNotice("Memuat status ulasan","Tunggu pemeriksaan agar ulasan tidak terkirim dua kali.")
        else if(!s.buyerReviewAvailable) BNotice("Ulasan belum tersedia","Layanan ulasan belum tersedia pada backend yang terhubung. Pesananmu tetap tersimpan.","orange")
        if(existing!=null) FCard("Ulasanmu") {FText("${existing.long("rating")} dari 5 bintang",16f,600,FForest);FText(existing.text("comment"),14f);FText(dateTimeLabel(existing.text("createdAt")),11f,color=FMuted)}
        else Column(Modifier.fillMaxWidth().padding(horizontal=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
            FText("Bagaimana pengalaman belanja Anda?",13f)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                (1..5).forEach{star->Box(Modifier.size(32.dp).semantics{selected=rating==star}.clickable(enabled=allowed,role=Role.RadioButton){rating=star},contentAlignment=Alignment.Center){FIcon(R.drawable.buyer8_star,32.dp,"$star bintang",if(star<=rating)FGreen else Color(0xFF727272))}}
            }
            FText("Hal apa yang perlu ditingkatkan?",13f)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                listOf("Lainnya","Aplikasi","Customer Service","Pengiriman","Kelengkapan Produk","Kemasan","Pengalaman Aplikasi").forEach{tag->Box(Modifier.background(if(tag in tags)Color(0xFFE3F7E1) else FPaper).border(1.dp,if(tag in tags)FGreen else Color(0xFFE6E6E6)).clickable(enabled=allowed,role=Role.Checkbox){tags=if(tag in tags)tags-tag else tags+tag}.padding(10.dp,6.dp)){FText(tag,12f)}}
            }
            FText("Berikan Komentar",13f)
            FField("Ceritakan pengalaman",comment,{comment=it},"Ceritakan pengalaman",enabled=allowed,showLabel=false,round=999.dp)
            FText("${combined.length}/500 karakter · Pilihan aspek disertakan dalam komentar.",10f,color=FMuted)
        }
        FText("Perbarui status ulasan",12f,600,FGreen,Modifier.clickable(enabled=!s.busy){vm.buyerReview(order.text("orderId"))})
    }
    if(reviewError)AlertDialog(onDismissRequest={reviewError=false},title={Text("Pilih 1–5 bintang")},text={Text("Berikan penilaian sebelum mengirim ulasan.")},confirmButton={TextButton(onClick={reviewError=false}){Text("OK")}})
}
