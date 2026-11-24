package id.panenku.app.ui.connected.buyer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.*

@Composable internal fun BuyerRefundDetailScreen(s:ConnectedState,vm:ConnectedViewModel,id:String,back:()->Unit,ticket:(String)->Unit) {
    val refund=s.buyerRefund
    var confirm by remember{mutableStateOf(false)};var report by remember{mutableStateOf(false)}
    var reason by rememberSaveable(id,s.buyerTicketPendingReason){mutableStateOf(s.buyerTicketPendingReason?:"Dana belum diterima. Saya sudah memeriksa rekening tujuan.")}
    var lookup by remember{mutableStateOf(false)};var ticketId by rememberSaveable(id){mutableStateOf("")}
    BPage("Status refund",refund?.let{"#${shortId(it.text("id"))} · #${shortId(it.text("orderId"))}"}?:"Memuat…",back,brand=false){
        if(refund==null){FText("Memuat detail refund…");BButton("Coba muat kembali",{vm.buyerRefundDetail(id)},primary=false,enabled=!s.busy)}
        else {
            BNotice(if(canConfirmBuyerRefund(refund))"Cek rekening tujuanmu" else refundStatus(refund.text("status")),
                if(canConfirmBuyerRefund(refund))"Petani sudah mengunggah bukti transfer balik. Refund belum selesai sampai kamu mengonfirmasi dana benar-benar diterima." else "Status pengajuan mengikuti pemeriksaan petani dan keputusan yang tersimpan pada pesanan.",if(canConfirmBuyerRefund(refund))"orange" else "green")
            FCard("Pengembalian penuh"){
                FBadge(refundStatus(refund.text("status")),if(refund.text("status")=="selesai")"green" else "orange")
                FKeyValue("Nilai refund",money(refund.long("refundAmount")),true)
                FText("Refund mencakup total pembayaran yang tercatat pada pesanan.",12f,color=FMuted)
                FKeyValue("Tujuan", "${refund.text("destinationProvider")} · ••••${refund.text("destinationAccount").takeLast(4)} · ${refund.text("destinationName")}")
                FText("Alasan: ${refund.text("reason")}",12f,color=FMuted)
                refund.text("farmerReason").takeIf(String::isNotBlank)?.let{FText("Catatan petani: $it",12f)}
                if(refund.flag("hasEvidence")) FText("Lihat bukti pengajuan",12f,600,FGreen,Modifier.clickable(enabled=!s.busy){vm.image("buyer/refunds/$id/evidence")})
            }
            BHistory("Riwayat refund",refund.list("history"),if(refund.text("status")!="selesai")"Refund selesai hanya setelah pembeli menyatakan dana diterima." else null)
            if(refund.flag("hasTransferProof")) FCard("Bukti transfer balik"){
                Column(Modifier.fillMaxWidth().background(Color(0xFFF5F5F5),RoundedCornerShape(10.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                    FText("Ringkasan tujuan pengembalian",12f,600,color=FMuted)
                    FIcon(R.drawable.buyer_shared_bank,20.dp)
                    FText(money(refund.long("refundAmount")),20f,700,FForest,line=30f)
                    FKeyValue("Penerima",refund.text("destinationName"))
                    FKeyValue("Bank", "${refund.text("destinationProvider")} · ••••${refund.text("destinationAccount").takeLast(4)}")
                    FText("Diunggah ${dateTimeLabel(refund.text("transferSubmittedAt"))}. Periksa bukti asli dan mutasi rekeningmu.",10f,color=FMuted)
                }
                BButton("Lihat bukti lengkap",{vm.image("buyer/refunds/$id/transfer-proof")},primary=false,enabled=!s.busy)
            }
            if(canConfirmBuyerRefund(refund)){
                BButton("Dana sudah diterima",{confirm=true},enabled=!s.busy)
                BButton("Dana belum diterima",{report=true},primary=false,enabled=!s.busy)
            }else if(canEscalateBuyerRefund(refund)) BButton("Ajukan bantuan admin",{report=true},primary=false,enabled=!s.busy)
            if(refund.text("status") in setOf("diajukan","disetujui") && !canEscalateBuyerRefund(refund)) FText("Bantuan admin tersedia setelah 24 jam tanpa tindak lanjut petani, jika refund ditolak, atau transfer balik belum diterima.",12f,color=FMuted)
            if(refund.text("status")=="dieskalasi") BButton("Lihat tiket bantuan",{s.buyerTicketId?.let(ticket)?:run{lookup=true}},primary=false,enabled=!s.busy)
            if(s.buyerTicketPendingReason!=null) BButton("Lanjutkan permintaan tiket",{report=true},primary=false,enabled=!s.busy)
            FText("“Dana sudah diterima” menyelesaikan refund. Jika belum masuk, buat tiket eskalasi sebelum menghubungi CS.",12f,color=FMuted)
            FText("Perbarui refund",12f,600,FGreen,Modifier.clickable(enabled=!s.busy){vm.buyerRefundDetail(id)})
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Dana benar-benar sudah diterima?")},text={Text("Periksa mutasi rekening tujuan. Konfirmasi ini menyelesaikan refund.")},confirmButton={TextButton(onClick={confirm=false;vm.buyerConfirmRefund(id)}){Text("Ya, sudah diterima")}},dismissButton={TextButton(onClick={confirm=false}){Text("Kembali")}})
    if(report) AlertDialog(onDismissRequest={report=false},title={Text("Catat tiket bantuan")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){FText("Tiket akan disimpan di server sebelum WhatsApp dibuka. Refund tetap terbuka.",12f);FField("Jelaskan masalah",reason,{reason=it},multiline=true,enabled=s.buyerTicketPendingReason==null);if(s.buyerTicketPendingReason!=null)FText("Melanjutkan permintaan sebelumnya dengan alasan yang sama.",11f,color=FMuted)}},confirmButton={TextButton(onClick={report=false;vm.buyerCreateTicket(id,reason,ticket)},enabled=!s.busy && reason.isNotBlank() && reason.length<=1000){Text("Buat tiket")}},dismissButton={TextButton(onClick={report=false}){Text("Kembali")}})
    if(lookup) AlertDialog(onDismissRequest={lookup=false},title={Text("Buka tiket yang sudah ada")},text={Column{FText("Masukkan ID tiket dari CS jika dibuat di perangkat lain.",12f);FField("ID tiket",ticketId,{ticketId=it})}},confirmButton={TextButton(onClick={lookup=false;ticket(ticketId.trim())},enabled=ticketId.isNotBlank()){Text("Buka tiket")}},dismissButton={TextButton(onClick={lookup=false}){Text("Kembali")}})
}

@Composable internal fun BuyerTicketScreen(s:ConnectedState,vm:ConnectedViewModel,id:String,back:()->Unit) {
    val ticket=s.buyerTicket;val refund=s.buyerRefund;val context=LocalContext.current
    var edit by remember{mutableStateOf(false)};var note by rememberSaveable(id,s.buyerTicketDraft.toString()){mutableStateOf(s.buyerTicketDraft.text("note"))}
    var uri by rememberSaveable(id,s.buyerTicketDraft.toString()){mutableStateOf(s.buyerTicketDraft.text("uri").takeIf(String::isNotBlank))}
    var filename by rememberSaveable(id,s.buyerTicketDraft.toString()){mutableStateOf(s.buyerTicketDraft.text("name"))}
    var error by remember{mutableStateOf<String?>(null)}
    val pick=rememberBuyerImagePicker({u,n,_->uri=u;filename=n},{error=it})
    BPage("Bantuan & eskalasi","Tiket tersimpan sebelum WhatsApp dibuka",back,brand=false){
        if(ticket==null){FText("Memuat tiket bantuan…");BButton("Coba muat kembali",{vm.buyerTicket(id)},primary=false,enabled=!s.busy)}
        else {
            BNotice("Tiket #${shortId(ticket.text("id"))} berhasil dicatat","Dibuat ${dateTimeLabel(ticket.text("createdAt"))}. Tim CS dapat meninjau riwayat refund tanpa meminta data rekening lewat chat.")
            FCard("Tiket aktif"){
                FBadge(if(ticket.text("status")=="selesai")"Tiket selesai" else "Terbuka · Menunggu tinjauan CS",if(ticket.text("status")=="selesai")"green" else "orange")
                FKeyValue("Pesanan","#${shortId(ticket.text("orderId"))}")
                FKeyValue("Refund","#${shortId(ticket.text("refundId"))} · ${refund?.let{money(it.long("refundAmount"))}.orEmpty()}")
                FKeyValue("Masalah",if(ticket.text("reason").contains("Dana belum diterima",true))"Dana belum diterima" else "Peninjauan refund",true)
                FText(ticket.text("reason"),12f)
                if(filename.isNotBlank() || note.isNotBlank()){
                    FUploadCard(filename.ifBlank{"Catatan tambahan"},"Catatan perangkat · belum dikirim ke server tiket",icon=R.drawable.buyer_shared_refund_file,click={edit=true})
                    if(note.isNotBlank()) FText(note,12f,color=FMuted)
                }
                BButton("Tambah keterangan / bukti",{edit=true},primary=false,enabled=!s.busy)
                FText("Catatan tambahan disimpan di perangkat. Kirim lampiran sendiri melalui chat setelah dibuka.",10f,color=FMuted)
            }
            FCard("Riwayat tiket"){
                FText("Tiket dibuat",13f,600);FText("${dateTimeLabel(ticket.text("createdAt"))} · Dicatat di Panenku.",12f,color=FMuted)
                FText(if(ticket.text("status")=="selesai")"Keputusan admin" else "Peninjauan admin",13f,600)
                FText(ticket.text("decisionReason").ifBlank{"Menunggu CS menelaah bukti dan menghubungi pihak terkait."},12f,color=FMuted)
                ticket.text("decidedAt").takeIf(String::isNotBlank)?.let{FText(dateTimeLabel(it),11f,color=FMuted)}
            }
            FCard("Lanjutkan ke WhatsApp CS"){
                FText("Pesan yang akan dibawa",13f,600)
                FText(buyerCsMessage(ticket),12f)
                FText("Hanya ID tiket dan ringkasan masalah. Tidak menyertakan rekening, KTP, alamat, atau bukti transaksi sensitif.",10f,color=FMuted)
                BButton("Hubungi WhatsApp CS",{
                    runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/$BUYER_CS_NUMBER?text=${Uri.encode(buyerCsMessage(ticket))}")))}.onFailure{error="WhatsApp atau browser belum dapat dibuka."}
                },enabled=ticket.text("id").isNotBlank() && !s.busy)
            }
            FCard("Kapan bisa eskalasi?"){
                FText("• Pengajuan refund ditolak petani.\n• Tidak ada tanggapan petani sesuai batas layanan.\n• Transfer balik belum diterima pembeli.",12f)
                FText("Tiket dicatat terlebih dahulu. Penutupan tiket admin tidak otomatis berarti dana sudah diterima.",12f,color=FMuted)
            }
            FText("Perbarui tiket",12f,600,FGreen,Modifier.clickable(enabled=!s.busy){vm.buyerTicket(id)})
        }
    }
    if(edit) AlertDialog(onDismissRequest={edit=false},title={Text("Keterangan tambahan")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){FField("Catatan perangkat",note,{note=it},multiline=true);BButton(if(filename.isBlank())"Pilih foto bukti" else filename,pick,primary=false);FText("Simpan lokal, lalu bagikan ke CS melalui WhatsApp. Berkas tidak diunggah ke server tiket.",11f,color=FMuted)}},confirmButton={TextButton(onClick={edit=false;vm.buyerTicketNote(id,note,uri,filename)},enabled=note.length<=1000 && (note.isNotBlank() || uri!=null)){Text("Simpan catatan")}},dismissButton={TextButton(onClick={edit=false}){Text("Kembali")}})
    error?.let{AlertDialog(onDismissRequest={error=null},title={Text("Belum berhasil")},text={Text(it)},confirmButton={TextButton(onClick={error=null}){Text("OK")}})}
}
