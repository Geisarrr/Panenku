package id.panenku.app.ui.connected.buyer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.*
import java.time.Instant

@Composable internal fun BuyerRefundScreen(s: ConnectedState, vm: ConnectedViewModel, back: () -> Unit, done: () -> Unit) {
    val order=s.detail
    if(order==null) {BPage("Ajukan refund","Memuat pesanan…",back){FText("Memuat pesanan…")};return}
    val id=order.text("orderId")
    var reason by rememberSaveable(id){mutableStateOf("")};var details by rememberSaveable(id){mutableStateOf("")}
    var bank by rememberSaveable(id){mutableStateOf("")};var account by rememberSaveable(id){mutableStateOf("")};var name by rememberSaveable(id){mutableStateOf("")}
    var uri by rememberSaveable(id){mutableStateOf<String?>(null)};var file by rememberSaveable(id){mutableStateOf("")}
    var agreed by rememberSaveable(id){mutableStateOf(false)};var imageError by remember{mutableStateOf<String?>(null)}
    val now=transactionNow()
    val eligible=s.buyerTransactionLoaded && canRequestBuyerRefund(order,s.refunds,now)
    val pick=rememberBuyerImagePicker({u,n,_->uri=u;file=n;imageError=null},{imageError=it})
    val combined=listOf(reason,details).filter(String::isNotBlank).joinToString(": ")
    val complete=eligible && !s.busy && reason.isNotBlank() && details.isNotBlank() && combined.length<=1000 && bank.isNotBlank() && bank.length<=100 && account.matches(Regex("[0-9]{1,100}")) && name.isNotBlank() && name.length<=255 && uri!=null && agreed
    BPage("Ajukan refund","#${shortId(id)} · ${order.text("storeName")}",back) {
        BNotice(if(eligible) "Masih dalam waktu pengajuan" else "Pengajuan belum tersedia",
            if(eligible && order.text("orderStatus")=="gagal_dipenuhi") "Pesanan berbayar gagal dipenuhi. Refund penuh mengikuti ketentuan pesanan."
            else if(eligible) "Pesanan diterima ${dateTimeLabel(order.text("receivedAt"))}. Ajukan paling lambat ${runCatching{dateTimeLabel(Instant.parse(order.text("receivedAt")).plusSeconds(86400).toString())}.getOrDefault("24 jam setelah diterima")}. Hanya satu pengajuan aktif."
            else "Perbarui status. Refund memerlukan pembayaran terverifikasi, belum memiliki refund aktif/selesai, dan memenuhi batas waktu penerimaan.")
        FCard("Pesanan yang diterima") {
            FBadge("${buyerStatus(order.text("orderStatus"))} · ${if(order.text("receivedAt").isNotBlank()) "Dikonfirmasi pembeli" else "Status dari server"}")
            BItems(order.list("items"),s.products);FRule()
            FKeyValue("Produk sudah dibayar",money(order.long("subtotalItems")))
            FKeyValue("Ongkir sudah dibayar",money(order.long("deliveryFee")))
            FKeyValue("Refund penuh",money(order.long("totalPayment")),true)
            FText("Jumlah tetap, berasal dari total yang benar-benar dibayar. Tidak melebihi ${money(order.long("totalPayment"))}.",10f,color=FMuted)
        }
        FCard("Alasan & bukti") {
            val choices=listOf("Kondisi sayuran tidak sesuai","Jumlah barang tidak sesuai","Produk berbeda dari pesanan","Lainnya")
            FSelectField("Alasan pengajuan",reason,choices.map{it to it},reason,{reason=it})
            FField("Ceritakan masalahnya",details,{details=it},"Jelaskan kondisi barang yang diterima.",multiline=true)
            FText("${combined.length}/1000 karakter",10f,color=FMuted)
            FUploadCard(if(file.isBlank()) "Tambah foto bukti" else file,"Foto bukti · JPG/PNG · Maks. 5 MB · ${if(uri==null) "Belum dipilih" else "Terlampir"}",icon=R.drawable.buyer_shared_refund_file,click={if(!s.busy)pick()})
            uri?.let{FPhoto(it,"Foto bukti pengajuan",Modifier.fillMaxWidth().height(140.dp))}
            imageError?.let{FText(it,12f,color=Color(0xFFA63A28))}
            BButton("Tambah / ganti foto bukti",pick,primary=false,enabled=!s.busy)
        }
        FCard("Rekening tujuan refund") {
            FText("Isi rekening penerima dana yang benar.",11f,color=FMuted)
            FField("Bank tujuan",bank,{bank=it},"Nama bank")
            FField("Nomor rekening",account,{account=it},keyboard=KeyboardType.Number)
            FField("Nama penerima",name,{name=it})
            FText("Pastikan rekening sesuai. Pengembalian dilakukan petani di luar aplikasi, bukan dari saldo Panenku.",11f,color=FMuted)
        }
        BNotice("Satu pengajuan aktif per pesanan","Refund penuh saja. Jika pesanan berbayar gagal dipenuhi, pengajuan dapat dilakukan tanpa menunggu barang diterima. Petani memiliki 24 jam untuk menanggapi.")
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(agreed,{agreed=it},colors=CheckboxDefaults.colors(checkedColor=FGreen));FText("Saya memastikan alasan, bukti, dan tujuan refund sudah benar.",11f,modifier=Modifier.weight(1f))}
        BButton("Kirim pengajuan ${money(order.long("totalPayment"))}",{uri?.let{vm.refund(id,combined,bank,account,name,it,done)}},enabled=complete)
        BButton("Perbarui kelayakan",{vm.buyerTransaction(id)},primary=false,enabled=!s.busy)
    }
}
