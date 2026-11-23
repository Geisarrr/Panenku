package id.panenku.app.ui.connected.buyer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.*

@Composable internal fun BuyerCheckoutScreen(s: ConnectedState, vm: ConnectedViewModel, back: () -> Unit, addresses: () -> Unit, done: () -> Unit) {
    var method by rememberSaveable { mutableStateOf(s.buyerFulfillment.takeIf { it=="pickup" || s.shippingEnabled } ?: "pickup") }
    var district by rememberSaveable(s.buyerAddressId) { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(s.quote?.text("scheduledDate").orEmpty()) }
    var slot by rememberSaveable { mutableStateOf(s.quote?.text("scheduledTimeSlot").orEmpty()) }
    var agreed by rememberSaveable(s.quote?.toString()) { mutableStateOf(false) }
    val address = s.buyerAddresses.firstOrNull { it.id == s.buyerAddressId }
    val selectedDistrict = district.ifBlank { address?.district.orEmpty() }
    val inArea = selectedDistrict in s.deliveryAreas
    val validAddress = method == "pickup" || (address != null && inArea && address.snapshot().length <= 2000)
    val quote = s.quote
    val rows = selectedBuyerRows(s.cart,s.buyerSelectionInitialized,s.buyerSelected)
    BPage("Checkout","Periksa pesanan sebelum dibuat",back,brand=false,paper=FPaper,footer={
        Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            FKeyValue("Total pesanan",quote?.let { money(it.long("totalPayment")) } ?: "Periksa total dahulu",true,14f)
            BButton("Buat Pesanan",{ vm.buyerCheckout(done) },enabled=!s.busy && agreed && validAddress && quote?.flag("canCheckout") == true)
        }
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            listOf("1 Buat pesanan","2 Konfirmasi petani","3 Bayar langsung").forEachIndexed { i,t -> FText(t,10f,color=if(i==0) FForest else FMuted) }
        }
        FCard("Cara terima pesanan") {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                listOf("pickup" to "Ambil sendiri","delivery" to "Antar").forEach { (value,label) ->
                    val enabled = value == "pickup" || s.shippingEnabled && s.deliveryAreas.isNotEmpty()
                    Column(Modifier.weight(1f).background(if(method==value) BPaper else Color.White,RoundedCornerShape(10.dp))
                        .border(1.dp,if(method==value) FGreen else FBorder,RoundedCornerShape(10.dp))
                        .clickable(enabled,role=Role.RadioButton) { method=value; date="";slot="";vm.invalidateSchedules() }.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            FIcon(if(value=="pickup") R.drawable.buyer_store else R.drawable.buyer_truck,20.dp)
                            if(method==value) RadioButton(true, null,Modifier.size(18.dp),colors=RadioButtonDefaults.colors(selectedColor=FGreen))
                        }
                        FText(label,13f,600,color=if(enabled) FInk else FMuted)
                        FText(if(value=="pickup") "Ongkir Rp0" else quote?.takeIf{method=="delivery"}?.let{"Ongkir ${money(it.long("deliveryFee"))}"} ?: "Sesuai area toko",11f,color=FMuted)
                    }
                }
            }
            FText(if(method=="pickup") "Lokasi ambil: ${s.fulfillment?.text("pickupLocation").orEmpty().ifBlank{"Pilih jadwal untuk melihat lokasi toko."}}" else "Pengantaran sesuai area layanan petani.",10f,color=FMuted)
        }
        if(method=="delivery") FCard("Alamat pengiriman") {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                FIcon(R.drawable.buyer_shared_location,20.dp)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    FText(address?.recipient ?: "Pilih alamat penerima",13f,600)
                    FText(address?.address ?: "Belum ada alamat dipilih.",11f,color=FMuted)
                }
                TextButton(onClick=addresses,contentPadding=PaddingValues(0.dp)) { FText("Ubah",11f,600,FGreen) }
            }
            if(!inArea) FSelectField("Kecamatan layanan toko",selectedDistrict,s.deliveryAreas.map{it to it},selectedDistrict,{ district=it; date="";slot="";vm.invalidateSchedules() })
            address?.note?.takeIf(String::isNotBlank)?.let { FText("Catatan kurir: $it",12f,color=FMuted) }
            FText(if(address!=null && inArea) "Alamat masuk area antar petani." else "Pilih alamat dan kecamatan yang termasuk layanan toko.",11f,color=if(inArea) FGreen else FMuted)
        }
        FCard("Jadwal pengiriman") {
            FText(s.fulfillment?.text("earliestDate")?.takeIf(String::isNotBlank)?.let{"Produk siap kirim mulai ${dateLabel(it)}."} ?: "Pilih jadwal sesuai layanan petani.",12f,color=FMuted)
            if(s.fulfillment==null) BButton("Cari jadwal tersedia",{ date="";slot="";vm.schedules(method,selectedDistrict) },enabled=!s.busy && rows.isNotEmpty() && (method=="pickup" || inArea),primary=false)
            else {
                val dates=s.fulfillment.list("dates").filter { it.getAsJsonArray("timeSlots")?.size()?.let { n -> n>0 } == true }
                BDateField(dateLabel(date).takeIf{date.isNotBlank()}.orEmpty(),dates.map{it.text("date") to dateLabel(it.text("date"))}) { date=it;slot="";vm.clearQuote() }
                val slots=dates.firstOrNull{it.text("date")==date}?.getAsJsonArray("timeSlots")?.map{it.asString}.orEmpty()
                slots.forEach { time ->
                    Row(Modifier.fillMaxWidth().background(if(slot==time) BPaper else Color.White,RoundedCornerShape(8.dp))
                        .border(1.dp,if(slot==time) FGreen else FBorder,RoundedCornerShape(8.dp)).clickable(role=Role.RadioButton){slot=time;vm.clearQuote()}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        FText(time,12f,600,modifier=Modifier.weight(1f));RadioButton(slot==time,null,Modifier.size(18.dp),colors=RadioButtonDefaults.colors(selectedColor=FGreen))
                    }
                }
                if(dates.isEmpty()) FText("Belum ada jadwal tersedia. Coba muat ulang pilihan toko.",12f,color=FMuted)
                FText("Sesuai jam layanan dan tanggal siap kirim petani.",10f,color=FMuted)
                if(quote==null) BButton("Periksa total",{ vm.preview(method,selectedDistrict,address?.snapshot().orEmpty(),date,slot,address?.note.orEmpty()) },primary=false,enabled=!s.busy && date.isNotBlank() && slot in slots && validAddress)
                FText("Perbarui jadwal",11f,600,FGreen,Modifier.clickable(enabled=!s.busy){date="";slot="";vm.schedules(method,selectedDistrict)})
            }
        }
        FCard("Ringkasan pesanan · ${rows.size} produk") {
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { FIcon(R.drawable.buyer_sprout,16.dp);FText(s.cart.text("storeName"),11f,color=FForest) }
            BItems(rows,s.products)
        }
        quote?.let {
            BTotals(it)
            if(!it.flag("canCheckout")) BNotice("Checkout belum tersedia",it.text("validationCode"),"orange")
            Row(verticalAlignment=Alignment.Top) {
                Checkbox(agreed,{agreed=it},modifier=Modifier.testTag("buyer-checkout-consent").size(20.dp),enabled=!s.busy && quote.flag("canCheckout"),colors=CheckboxDefaults.colors(checkedColor=FGreen))
                FText("Saya menyetujui produk, total pembayaran, alamat, dan jadwal di atas.",11f,modifier=Modifier.weight(1f).padding(start=8.dp))
            }
        }
        BNotice("Belum perlu membayar","Pesanan menunggu penerimaan petani maksimal 24 jam. Setelah diterima, bayar langsung ke QRIS atau rekening petani, lalu unggah bukti.")
    }
}

@Composable internal fun BuyerAddressesScreen(s: ConnectedState, vm: ConnectedViewModel, back: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var label by rememberSaveable { mutableStateOf("") };var recipient by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") };var address by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf("") };var note by rememberSaveable { mutableStateOf("") }
    var main by rememberSaveable { mutableStateOf(false) }
    BPage("Alamat pengiriman","${s.buyerAddresses.size} alamat tersimpan",back) {
        s.buyerAddresses.forEach { row ->
            FCard(row.label + if(row.id==s.buyerAddressId) " · Terpilih" else "") {
                if(row.id==s.buyerAddressId) FBadge("Alamat utama")
                FText(row.recipient,13f,600); FText(row.address,12f,color=FMuted);FText("Kontak: ${row.phone}",12f,color=FMuted)
                val area=row.district in s.deliveryAreas
                BNotice(if(area) "Masuk area toko" else "Periksa area antar toko",if(area) "Layanan antar tersedia di ${row.district}." else "Pilih kecamatan layanan toko sebelum checkout.",if(area) "green" else "orange")
                if(row.id!=s.buyerAddressId) BButton("Jadikan alamat utama",{vm.selectBuyerAddress(row.id)},primary=false,enabled=!s.busy)
                BButton("Edit alamat${if(row.id==s.buyerAddressId) " utama" else ""}",{
                    editing=row.id;label=row.label;recipient=row.recipient;phone=row.phone;address=row.address;district=row.district;note=row.note;main=row.id==s.buyerAddressId
                },primary=false,enabled=!s.busy)
            }
        }
        FCard(if(editing==null) "Tambah alamat baru" else "Edit alamat") {
            FText("Alamat tambahan tersimpan di perangkat ini untuk akunmu. Alamat utama juga disimpan pada profil.",12f,color=FMuted)
            FField("Label alamat",label,{label=it},"Dapur utama")
            FField("Nama penerima",recipient,{recipient=it})
            FField("Nomor kontak",phone,{phone=it},keyboard=KeyboardType.Phone)
            FField("Alamat lengkap",address,{address=it},multiline=true)
            FSelectField("Kecamatan layanan toko",district,s.deliveryAreas.map{it to it},district,{district=it})
            FField("Catatan pengantaran",note,{note=it},"Pagar putih, hubungi saat tiba")
            Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(main,{main=!main},colors=RadioButtonDefaults.colors(selectedColor=FGreen));FText("Jadikan alamat utama",12f) }
            BNotice(if(district in s.deliveryAreas) "Alamat baru dapat dilayani" else "Area layanan belum dipilih",if(district in s.deliveryAreas) "$district masuk area antar toko." else "Alamat tetap dapat disimpan, tetapi checkout antar memerlukan kecamatan layanan yang sesuai.")
            BButton(if(editing==null) "Simpan alamat baru" else "Simpan perubahan",{
                vm.saveBuyerAddress(SavedBuyerAddress(id=editing ?: java.util.UUID.randomUUID().toString(),label=label,recipient=recipient,phone=phone,address=address,district=district,note=note),main)
            },enabled=!s.busy && listOf(label,recipient,phone,address).all(String::isNotBlank) && (recipient.length+phone.length+address.length+note.length)<1950)
            if(editing!=null) TextButton(onClick={editing=null;label="";recipient="";phone="";address="";district="";note="";main=false}) { FText("Tambah alamat lain",12f,600,FGreen) }
        }
    }
}

@Composable internal fun BuyerOrderCreatedScreen(order: JsonObject, view: () -> Unit, home: () -> Unit) {
    Column(Modifier.fillMaxSize().background(BPaper).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Image(androidx.compose.ui.res.painterResource(R.drawable.buyer_success),null,Modifier.fillMaxWidth().height(230.dp))
            FText("Pesanan berhasil dibuat",20f,700,FForest,line=30f,align=TextAlign.Center)
            Box(Modifier.background(Color(0xFFE2F7DF),RoundedCornerShape(999.dp)).padding(horizontal=12.dp,vertical=6.dp)) {FText(buyerStatus(order.text("orderStatus")),12f,600,FForest)}
            FText("Pesananmu sudah terkirim ke petani. Belum ada pembayaran yang dilakukan.",12f,color=FMuted,align=TextAlign.Center)
        }
        FCard {
            FKeyValue("Nomor pesanan","#${shortId(order.text("orderId"))}")
            FKeyValue("Petani",order.text("storeName").ifBlank{"Toko pada pesanan"})
            FKeyValue("Isi pesanan","${order.list("items").size} produk")
            FKeyValue("Pengiriman","${dateLabel(effectiveDate(order))} · ${effectiveSlot(order)}")
            FRule();FKeyValue("Total pesanan",money(order.long("totalPayment")),true,14f)
        }
        FCard("Apa selanjutnya?") {
            listOf("Tunggu petani menerima" to "Maksimal 24 jam. Kami akan memberi notifikasi.","Bayar langsung ke petani" to "Setelah diterima, QRIS atau rekening petani akan tersedia pada pesanan.","Unggah bukti pembayaran" to "Bukti diperiksa manual oleh petani. Pesanan diproses setelah disetujui.").forEachIndexed { i,(title,body) ->
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(26.dp).background(Color(0xFFE0F5DE),RoundedCornerShape(50)),contentAlignment=Alignment.Center) { FText("${i+1}",12f,700,FForest) }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { FText(title,14f,600);FText(body,12f,color=FMuted) }
                }
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) { BButton("Lihat Pesanan",view);BButton("Kembali ke Beranda",home,primary=false) }
    }
}
