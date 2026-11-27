package id.panenku.app.ui.connected.buyer

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.*
import java.time.Instant

@Composable internal fun transactionNow(): Instant {
    val clock=LocalBuyerClock.current
    var now by remember { mutableStateOf(clock()) }
    LaunchedEffect(Unit) { while(true) { kotlinx.coroutines.delay(1000);now=clock() } }
    return now
}

// Anchor the backend clock to monotonic elapsed time so the refund window keeps
// advancing even when the device wall clock differs from the server's clock.
@Composable internal fun refundNow(order: JsonObject): Instant {
    val serverTime = order.text("serverTime")
    val clock = LocalBuyerClock.current
    val anchor = remember(serverTime) { runCatching { Instant.parse(serverTime) }.getOrNull() }
    val started = remember(serverTime) { SystemClock.elapsedRealtime() }
    var elapsed by remember(serverTime) { mutableLongStateOf(0L) }
    LaunchedEffect(serverTime) {
        while (true) {
            elapsed = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
            kotlinx.coroutines.delay(1000)
        }
    }
    return anchor?.plusMillis(elapsed) ?: clock()
}
@Composable internal fun BuyerPaymentScreen(s: ConnectedState, vm: ConnectedViewModel, back: () -> Unit) {
    val order=s.detail
    if(order==null) { BPage("Pembayaran ke Petani","Memuat pesanan…",back) { FText("Memuat data pembayaran…") };return }
    val id=order.text("orderId")
    val rejected=order.text("paymentStatus")=="bukti_ditolak"
    val allowed=canUploadBuyerProof(order,transactionNow()) && s.buyerTransactionLoaded && !s.busy
    val clipboard=LocalClipboardManager.current
    var uri by rememberSaveable(id,order.text("paymentStatus")) { mutableStateOf<String?>(null) }
    var file by rememberSaveable(id,order.text("paymentStatus")) { mutableStateOf("") }
    var pickerError by remember { mutableStateOf<String?>(null) }
    val pick=rememberBuyerImagePicker({u,n,_ ->uri=u;file=n;pickerError=null},{pickerError=it})
    val last=s.proofs.lastOrNull { it.text("decision")=="tolak" || it.text("status")=="bukti_ditolak" } ?: s.proofs.lastOrNull()
    BPage(if(rejected) "Bukti pembayaran ditolak" else "Pembayaran ke Petani","#${shortId(id)} · ${order.text("storeName")}",back,brand=rejected) {
        if(rejected) {
            BNotice("Belum terverifikasi / belum lunas","Petani belum dapat mencocokkan dana masuk dengan bukti yang kamu kirim. Pesanan belum diproses.","red")
            FCard("Catatan dari petani") {
                FBadge("Ditolak · ${dateTimeLabel(last?.text("decidedAt").orEmpty())}","red")
                FText(last?.text("reason").orEmpty().ifBlank{"Periksa kembali bukti dan tujuan pembayaran sesuai catatan petani."},14f,600)
                FText("Unggah ulang bukti yang memperlihatkan nominal, penerima, tanggal, dan status transfer.",12f,color=FMuted)
                FKeyValue("Nominal seharusnya",money(order.long("totalPayment")),true)
            }
        } else BNotice(if(order.text("orderStatus")=="verifikasi_pembayaran") "Bukti menunggu verifikasi petani" else "Petani sudah menerima pesanan",
            if(order.text("orderStatus")=="verifikasi_pembayaran") "Bukti diperiksa manual. Mengunggah bukti tidak otomatis membuat pembayaran terverifikasi." else "Status saat ini: ${buyerStatus(order.text("orderStatus"))}.")
        if(!rejected) FCard {
            FText("Bayar langsung ke ${order.text("storeName")}",12f,color=FMuted,modifier=Modifier.fillMaxWidth(),align=TextAlign.Center)
            FText(money(order.long("totalPayment")),20f,700,FForest,Modifier.fillMaxWidth(),line=30f,align=TextAlign.Center)
            FText("Produk ${money(order.long("subtotalItems"))} + ongkir ${money(order.long("deliveryFee"))}",10f,color=FMuted,modifier=Modifier.fillMaxWidth(),align=TextAlign.Center)
            FRule()
            TextButton(onClick={clipboard.setText(AnnotatedString(order.long("totalPayment").toString()))},modifier=Modifier.align(Alignment.CenterHorizontally),contentPadding=PaddingValues(0.dp)) {
                FIcon(R.drawable.buyer_copy,15.dp);Spacer(Modifier.width(4.dp));FText("Salin nominal",11f,600,FGreen)
            }
        }
        BNotice(if(rejected) "Batas unggah baru · 24 jam" else "Batas unggah bukti · 24 jam",
            "Unggah paling lambat ${dateTimeLabel(order.text("expiresAt"))}. ${if(rejected) "Batas ini mengikuti keputusan petani terbaru." else "Bukti pembayaran diperiksa oleh petani."}","orange")
        if(s.proofs.isNotEmpty()) FCard("Riwayat bukti") {
            s.proofs.forEach { proof ->
                Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { FIcon(R.drawable.buyer_shared_check_info,20.dp); FText(buyerStatus(proof.text("status")),13f,600) }
                    FText("${dateTimeLabel(proof.text("submittedAt"))} · ${proof.text("originalFileName")}",11f,color=FMuted)
                    proof.text("reason").takeIf(String::isNotBlank)?.let { FText(it,12f,color=FMuted) }
                    FText("Lihat bukti",12f,600,FGreen,Modifier.clickable(enabled=!s.busy){vm.image("buyer/orders/$id/payment-proofs/${proof.text("id")}/image")})
                }
            }
            FText("Riwayat tetap tersimpan saat kamu mengunggah ulang.",10f,color=FMuted)
        }
        if(!rejected || s.destination==null) {
            s.options.forEach { option ->
                val qris=option.text("methodType")=="qris"
                val selected=s.destination?.let { it.text("methodType")==option.text("methodType") && it.text("accountNumber")==option.text("accountNumber") && it.text("accountHolderName")==option.text("accountHolderName") && it.text("bankName")==option.text("bankName") } == true
                FCard(if(qris) "Atau gunakan QRIS petani" else "Transfer ke rekening petani") {
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        FIcon(if(qris) R.drawable.buyer_shared_scan else R.drawable.buyer_shared_bank,24.dp)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) { FText(option.text("bankName").ifBlank{"QRIS"}+" · Rekening petani",14f,600);FText(option.text("accountHolderName"),12f,color=FMuted) }
                    }
                    if(!qris) {
                        Row(Modifier.fillMaxWidth().background(Color(0xFFF5F5F5),RoundedCornerShape(10.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            FText(option.text("accountNumber"),16f,700,modifier=Modifier.weight(1f))
                            TextButton(onClick={clipboard.setText(AnnotatedString(option.text("accountNumber")))},contentPadding=PaddingValues(0.dp)) {FIcon(R.drawable.buyer_copy,16.dp);FText("Salin",11f,600,FGreen)}
                        }
                    } else {
                        s.buyerQris?.takeIf { s.buyerQrisId==option.text("id") }?.let { bytes -> val bitmap=remember(bytes){BitmapFactory.decodeByteArray(bytes,0,bytes.size)}; if(bitmap!=null) Image(bitmap.asImageBitmap(),"QRIS petani",Modifier.fillMaxWidth().height(180.dp)) }
                        BButton("Lihat QRIS petani",{vm.buyerQris(id,option.text("id"))},primary=false,enabled=!s.busy)
                    }
                    BButton(if(selected) "Tujuan pembayaran dipilih" else "Gunakan ${if(qris) "QRIS" else "rekening ini"}",{vm.choosePayment(id,option.text("id"))},primary=!selected,enabled=allowed && !selected)
                }
            }
        }
        if(!rejected) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            FText("Bayar di luar aplikasi Panenku",14f,600)
            FText("Buka aplikasi bank atau pembayaranmu, lalu transfer ke petani sesuai nominal. Panenku tidak menampung dana dan tidak memotong saldo.",12f,color=FMuted)
        }
        FCard(if(rejected) "Perbaiki bukti" else "Unggah bukti pembayaran") {
            FUploadCard(if(file.isBlank()) if(rejected) "Pilih bukti pengganti" else "Pilih foto bukti pembayaran" else file,
                "JPG atau PNG · Maks. 5 MB · ${if(uri==null) "Belum ada berkas dipilih" else "Terlampir"}",click={if(allowed) pick()})
            uri?.let { FPhoto(it,"Bukti pembayaran dipilih",Modifier.fillMaxWidth().height(120.dp)) }
            pickerError?.let { FText(it,12f,color=Color(0xFFA63A28)) }
            if(rejected) BButton("Unggah ulang bukti",{uri?.let{vm.buyerProof(id,it)}},enabled=allowed && uri!=null && s.destination!=null)
            FText(if(rejected) "Setelah dikirim, status menjadi Verifikasi Pembayaran. Petani memeriksa manual; menunggu verifikasi tidak otomatis kedaluwarsa." else "Bukti diperiksa manual oleh petani. Mengunggah bukti tidak berarti pembayaran sudah terverifikasi.",12f,color=FMuted)
            if(s.destination==null) FText("Pilih tujuan pembayaran di atas sebelum mengirim bukti.",11f,color=FMuted)
            if(!allowed) FText("Pengiriman bukti tersedia saat menunggu pembayaran, sebelum batas waktu, tanpa usulan jadwal terbuka.",11f,color=FMuted)
        }
        if(!rejected) {
            BButton("Kirim Bukti Pembayaran",{uri?.let{vm.buyerProof(id,it)}},enabled=allowed && uri!=null && s.destination!=null)
            FText("Tombol aktif setelah bukti dan tujuan pembayaran dipilih.",10f,color=FMuted,modifier=Modifier.fillMaxWidth(),align=TextAlign.Center)
        }
        BButton("Perbarui status pembayaran",{vm.buyerTransaction(id)},primary=false,enabled=!s.busy)
    }
}

@Composable internal fun BuyerScheduleScreen(s: ConnectedState, vm: ConnectedViewModel, back: () -> Unit) {
    val order=s.detail ?: return
    val proposal=order.obj("pendingScheduleChange")
    var reject by remember {mutableStateOf(false)};var reason by rememberSaveable {mutableStateOf("")}
    BPage("Perubahan jadwal","#${shortId(order.text("orderId"))} · ${order.text("storeName")}",back) {
        if(proposal.text("id").isBlank()) BNotice("Tidak ada usulan terbuka","Jadwal saat ini mengikuti keputusan terakhir yang tersimpan pada pesanan.")
        else FBadge("Menunggu keputusanmu","orange")
        FCard("Jadwal saat ini") {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {FIcon(R.drawable.buyer_truck,22.dp);Column{FText(if(order.text("fulfillmentMethod")=="pickup") "Ambil di toko petani" else "Antar ke alamat pesanan",14f,600);FText(order.text("deliveryAddressSnapshot").ifBlank{order.text("pickupLocationSnapshot")},12f,color=FMuted)}}
            FKeyValue("Tanggal berlaku",dateLabel(effectiveDate(order)))
            FKeyValue("Jam antar",effectiveSlot(order))
        }
        if(proposal.text("id").isNotBlank()) {
            FCard("Usulan jadwal baru") {
                FBadge("Diajukan ${dateTimeLabel(proposal.text("createdAt"))}")
                FKeyValue("Tanggal usulan",dateLabel(proposal.text("proposedDate")),true)
                FKeyValue("Jam antar",proposal.text("proposedSlot"))
                FText("Alasan dari ${order.text("storeName")}",14f,600)
                FText(proposal.text("reason"),12f)
            }
            BNotice("Jadwal tidak berubah otomatis","Jika setuju, jadwal baru berlaku dan petani diberi tahu. Jika ditolak, jadwal lama tetap berlaku; petani perlu memenuhi jadwal lama atau menghubungimu.")
            BButton("Setujui jadwal baru",{vm.decideSchedule(order.text("orderId"),true,"")},enabled=!s.busy)
            BButton("Tolak perubahan jadwal",{reject=true},primary=false,enabled=!s.busy)
        }
        FCard("Ringkasan tetap sama") {FKeyValue("Isi pesanan","${order.list("items").size} produk");FKeyValue("Total dibayar",money(order.long("totalPayment")),true);FText("Alamat, jumlah, dan harga pesanan tidak berubah.",12f,color=FMuted)}
    }
    if(reject) AlertDialog(onDismissRequest={reject=false},title={Text("Tolak perubahan jadwal?")},text={FField("Alasan penolakan",reason,{reason=it},multiline=true)},confirmButton={TextButton(onClick={reject=false;vm.decideSchedule(order.text("orderId"),false,reason) },enabled=reason.isNotBlank()){Text("Tolak usulan")}},dismissButton={TextButton(onClick={reject=false}){Text("Kembali")}})
}

@Composable internal fun BuyerTrackingScreen(s: ConnectedState, vm: ConnectedViewModel, back: () -> Unit, payment: () -> Unit, schedule: () -> Unit, refund: () -> Unit, review: () -> Unit) {
    val order=s.detail
    if(order==null) {BPage("Lacak pesanan","Memuat pesanan…",back){FText("Memuat status terbaru…")};return}
    val id=order.text("orderId");val status=order.text("orderStatus")
    var receive by remember{mutableStateOf(false)};var issue by remember{mutableStateOf(false)}
    val eligible=s.buyerTransactionLoaded && canRequestBuyerRefund(order,s.refunds,refundNow(order))
    BPage("Lacak pesanan","#${shortId(id)} · ${if(order.text("fulfillmentMethod")=="pickup") "Ambil sendiri" else "Antar oleh petani"}",back) {
        BNotice(if(status=="menunggu_penerimaan") "Pesanan tiba, periksa hasil panen" else buyerStatus(status),
            if(status=="menunggu_penerimaan") "Menunggu Penerimaan → Belum Selesai. Konfirmasi setelah jumlah dan kondisi barang kamu periksa." else "Periksa status pesanan terbaru sebelum melanjutkan tindakan.")
        if(order.obj("pendingScheduleChange").text("id").isNotBlank()) BButton("Tanggapi usulan jadwal",schedule,enabled=!s.busy)
        FCard("Tujuan & jadwal") {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {FIcon(R.drawable.buyer_shared_location,22.dp);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){FText(order.text("buyerName").ifBlank{"Alamat pesanan"},14f,600);FText(order.text("deliveryAddressSnapshot").ifBlank{order.text("pickupLocationSnapshot")},12f,color=FMuted)}}
            FText("${dateLabel(effectiveDate(order))} · ${effectiveSlot(order)}",12f)
            FBadge("${if(order.text("fulfillmentMethod")=="pickup") "Ambil sendiri" else "Antar"} · Ongkir ${money(order.long("deliveryFee"))}")
            FText("Tetap perlu konfirmasi penerimaan setelah barang diperiksa.",10f,color=FMuted)
        }
        FCard("Perjalanan pesanan") {
            order.list("history").forEach { event ->
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(24.dp).background(Color(0xFFE0F5DE),RoundedCornerShape(50)),contentAlignment=Alignment.Center){FIcon(R.drawable.buyer_shared_check_info,18.dp)}
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {FText(buyerStatus(event.text("toStatus")),13f,600);FText("${dateTimeLabel(event.text("changedAt"))} · ${event.text("note")}",12f,color=FMuted)}
                }
            }
            if(order.list("history").isEmpty()) FText("Riwayat belum tersedia. Perbarui status dari server.",12f,color=FMuted)
            if(status!="selesai") {FText("Selesai",13f,600,color=FMuted);FText("Baru tercapai setelah pembeli mengonfirmasi penerimaan.",12f,color=FMuted)}
        }
        BSummary(order,s.products)
        if(status in listOf("menunggu_pembayaran","verifikasi_pembayaran")) BButton(if(order.text("paymentStatus")=="bukti_ditolak") "Perbaiki bukti pembayaran" else "Lihat pembayaran",payment,enabled=!s.busy)
        if(status=="menunggu_penerimaan") BButton("Konfirmasi pesanan diterima",{receive=true},enabled=!s.busy)
        if(status=="selesai") BButton("Lihat detail pesanan",review,primary=false,enabled=!s.busy)
        if(eligible) BButton("Ajukan refund",refund,primary=false,enabled=!s.busy)
        BButton("Laporkan masalah pesanan",{issue=true},primary=false,enabled=!s.busy)
        FText("Konfirmasi mencatat waktu penerimaan. Jika ada masalah yang memenuhi syarat, refund dapat diajukan maksimal 24 jam sejak diterima.",12f,color=FMuted)
        BButton("Perbarui status pesanan",{vm.buyerTransaction(id)},primary=false,enabled=!s.busy)
    }
    if(receive) AlertDialog(onDismissRequest={receive=false},title={Text("Barang sudah diterima?")},text={Text("Periksa jumlah dan kondisi barang. Konfirmasi menyelesaikan pesanan dan memulai batas pengajuan refund 24 jam.")},confirmButton={TextButton(onClick={receive=false;vm.buyerReceive(id)}){Text("Sudah diterima")}},dismissButton={TextButton(onClick={receive=false}){Text("Kembali")}})
    if(issue) AlertDialog(onDismissRequest={issue=false},title={Text("Masalah pada pesanan")},text={Text(if(eligible) "Kamu dapat mengajukan refund disertai alasan dan foto bukti." else "Pengajuan refund tersedia untuk pembayaran terverifikasi sesuai status dan batas waktu pesanan. Hubungi petani untuk kendala pengantaran; pelaporan pengiriman melalui aplikasi belum tersedia.")},confirmButton={TextButton(onClick={issue=false;if(eligible) refund()}){Text(if(eligible) "Ajukan refund" else "Mengerti")}})
}
