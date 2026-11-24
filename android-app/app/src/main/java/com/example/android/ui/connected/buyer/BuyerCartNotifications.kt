package id.panenku.app.ui.connected.buyer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import id.panenku.app.R
import id.panenku.app.data.remote.*
import id.panenku.app.ui.connected.*
import java.time.*

@Composable internal fun BuyerCartScreen(s:ConnectedState,vm:ConnectedViewModel,back:()->Unit,checkout:()->Unit,product:(String)->Unit) {
    var deleting by remember {mutableStateOf(false)}
    val rows=s.cart.list("items")
    val selected=selectedBuyerRows(s.cart,s.buyerSelectionInitialized,s.buyerSelected)
    val eligible=rows.filter { it.flag("available") }
    val total=selected.sumOf { it.long("itemSubtotal") }
    Column(Modifier.fillMaxSize()){
        BCatalogHeader("Keranjang",back)
        if(rows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()){
                // Faint empty rows retain the Figma empty-state composition; these contain no sample products.
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){repeat(3){Box(Modifier.fillMaxWidth().height(66.dp).background(Color(0xFFF7F8F7),RoundedCornerShape(14.dp)))}}
                Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Image(painterResource(R.drawable.buyer15_empty_cart),null,Modifier.size(76.dp,78.dp))
                    FText("Belum ada Pesanan",20f,600,line=28f)
                    FText("Keranjang Anda masih kosong.",12f,color=FMuted)
                    TextButton(onClick=back){FText("Mulai belanja",13f,600,FGreen)}
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                FText("Cara menerima pesanan",13f,600)
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    listOf("pickup" to "Ambil sendiri","delivery" to "Antar ke alamat").forEach{(value,label)->
                        val enabled=value=="pickup" || s.shippingEnabled && s.deliveryAreas.isNotEmpty()
                        Column(Modifier.weight(1f).background(if(s.buyerFulfillment==value)BPaper else Color.White,RoundedCornerShape(14.dp)).border(1.dp,if(s.buyerFulfillment==value)FGreen else FBorder,RoundedCornerShape(14.dp)).clickable(enabled,role=Role.RadioButton){vm.buyerFulfillment(value)}.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            FIcon(if(value=="pickup")R.drawable.buyer_store else R.drawable.buyer_truck,24.dp)
                            FText(label,12f,600,color=if(enabled)FInk else FMuted)
                            FText(if(value=="pickup")"Sesuai jam toko" else if(enabled)"Jadwal saat checkout" else "Belum tersedia",10f,color=FMuted)
                        }
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(eligible.isNotEmpty() && eligible.all{it.text("id") in s.buyerSelected},{vm.buyerSelectCart(null,it)},Modifier.size(20.dp),colors=CheckboxDefaults.colors(checkedColor=FGreen))
                    FText("Pilih semua",12f,modifier=Modifier.weight(1f).padding(start=8.dp))
                    FText("Hapus",12f,600,FGreen,Modifier.clickable(enabled=s.buyerSelected.isNotEmpty() && !s.busy,role=Role.Button){deleting=true})
                }
                FText(s.cart.text("storeName"),13f,600,FForest)
                rows.forEach{row->
                    Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Checkbox(row.text("id") in s.buyerSelected,{vm.buyerSelectCart(row.text("id"),it)},Modifier.size(20.dp),enabled=!s.busy,colors=CheckboxDefaults.colors(checkedColor=FGreen))
                        FPhoto(row.text("photoAccessUrl"),row.text("name"),Modifier.size(50.dp).clickable(role=Role.Button){product(row.text("productId"))})
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                            FText(row.text("name"),13f,500);FText("${money(row.long("pricePerKg"))}/kg",10f,color=FMuted)
                            FText(money(row.long("itemSubtotal")),13f,700)
                            if(!row.flag("available"))FText(row.text("unavailableReason").ifBlank{"Produk tidak tersedia."},10f,color=Color(0xFF9F3333))
                        }
                        BCartQuantity(row,s.busy){vm.buyerQuantity(row.text("id"),it)}
                    }
                }
                val suggestions=s.buyerHome.filter { it.text("farmerId")==s.cart.text("farmerId") && rows.none { row->row.text("productId")==it.text("id") } }.take(4)
                if(suggestions.isNotEmpty()){
                    FText("Rekomendasi untukmu",16f,700)
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)){suggestions.forEach{BProductTile(it,vm,product,Modifier.width(165.dp),s.busy)}}
                }
                FText("Satu pesanan untuk satu toko. Harga dan jadwal diperiksa lagi saat checkout.",11f,color=FMuted)
            }
            Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){FText("Total (${selected.size} produk)",12f,color=FMuted);FText(money(total),18f,700,FForest)}
                BButton("Buat Pesanan",checkout,enabled=!s.busy && selected.isNotEmpty() && selected.all{it.flag("available")})
            }
        }
    }
    if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("Hapus produk terpilih?")},text={Text("${s.buyerSelected.size} produk akan dihapus dari keranjang.")},confirmButton={TextButton(onClick={deleting=false;vm.buyerDeleteSelected()}){Text("Hapus")}},dismissButton={TextButton(onClick={deleting=false}){Text("Kembali")}})
}
@Composable private fun BCartQuantity(row:JsonObject,busy:Boolean,save:(String)->Unit) {
    var editing by remember {mutableStateOf(false)}
    var value by rememberSaveable(row.text("id"),row.text("quantityKg")){mutableStateOf(quantity(row.text("quantityKg")))}
    Row(Modifier.background(Color.White,CircleShape).border(1.dp,FBorder,CircleShape).height(32.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(26.dp).clickable(enabled=!busy,role=Role.Button){val next=(row.text("quantityKg").toBigDecimalOrNull() ?: java.math.BigDecimal.ONE)-java.math.BigDecimal.ONE;if(next.signum()>0)save(next.toPlainString())},contentAlignment=Alignment.Center){FText("−",16f,600,color=FMuted)}
        FText("${quantity(row.text("quantityKg"))} kg",10f,600,modifier=Modifier.clickable(enabled=!busy,role=Role.Button){editing=true})
        Box(Modifier.size(26.dp).clickable(enabled=!busy,role=Role.Button){val next=(row.text("quantityKg").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO)+java.math.BigDecimal.ONE;save(next.toPlainString())},contentAlignment=Alignment.Center){FText("+",16f,600,color=FGreen)}
    }
    if(editing)AlertDialog(onDismissRequest={editing=false},title={Text("Jumlah dalam kg")},text={FField("Jumlah ${row.text("id")}",value,{value=it},keyboard=KeyboardType.Decimal)},confirmButton={TextButton(onClick={editing=false;save(value)}){Text("Simpan")}},dismissButton={TextButton(onClick={editing=false}){Text("Kembali")}})
}
@Composable internal fun BuyerNotificationsScreen(s:ConnectedState,vm:ConnectedViewModel,back:()->Unit,open:(String,String)->Unit) {
    var unreadOnly by rememberSaveable {mutableStateOf(false)}
    val zone=ZoneId.of("Asia/Jakarta")
    val today=LocalBuyerClock.current().atZone(zone).toLocalDate()
    val filtered=s.notifications.filter { !unreadOnly || !it.flag("isRead") }
    BPage("Notifikasi","Pembaruan transaksi, bukan promosi",back,brand=false,paper=BPaper){
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            BChoice(!unreadOnly,"Semua"){unreadOnly=false}
            BChoice(unreadOnly,"Belum dibaca (${s.unreadNotificationCount})"){unreadOnly=true}
        }
        FText("Tandai semua dibaca",12f,600,FGreen,Modifier.clickable(enabled=!s.busy && s.unreadNotificationCount>0,role=Role.Button){vm.buyerReadAll()})
        val groups=filtered.groupBy{runCatching{Instant.parse(it.text("createdAt")).atZone(zone).toLocalDate()==today}.getOrDefault(false)}
        listOf(true,false).forEach{isToday->val entries=groups[isToday].orEmpty();if(entries.isNotEmpty()){
            FText(if(isToday)"Hari ini · ${today.dayOfMonth} ${today.month.getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale("id","ID"))} ${today.year}" else "Sebelumnya",12f,600,color=FMuted)
            entries.forEach{notice->FCard {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    val icon=when(notice.text("type")){"schedule"->R.drawable.buyer_calendar;"payment"->R.drawable.buyer8_credit;"refund"->R.drawable.buyer8_menu_orders;else->R.drawable.buyer8_truck}
                    Box(Modifier.size(36.dp).background(BPaper,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){FIcon(icon,22.dp,tint=FGreen)}
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){FText(dateTimeLabel(notice.text("createdAt")),10f,color=FMuted);if(!notice.flag("isRead"))FBadge("Baru",compact=true)}
                        FText(notice.text("title"),14f,600,line=20f)
                        if(notice.text("body").isNotBlank())FText(notice.text("body"),12f,color=FMuted,line=18f)
                        val target=buyerLink(notice.text("deepLink"))
                        val action=when(target?.first){"orders"->if(notice.text("type")=="schedule")"Lihat jadwal" else "Lihat pesanan";"refunds"->"Lihat refund";"escalations"->"Lihat tiket";else->if(!notice.flag("isRead"))"Tandai dibaca" else null}
                        action?.let{FText(it,12f,600,FGreen,Modifier.clickable(enabled=!s.busy,role=Role.Button){vm.buyerOpenNotification(notice,open)})}
                    }
                }
            }}
        }}
        if(filtered.isEmpty())FCard {FText(if(unreadOnly)"Semua notifikasi sudah dibaca." else "Belum ada notifikasi.",13f,color=FMuted)}
        if(s.buyerNotificationMore)BButton("Muat notifikasi lainnya",{vm.buyerNotifications(s.buyerNotificationPage+1)},primary=false,enabled=!s.busy)
        FText("Notifikasi mengikuti pembaruan pesanan dan refund dari layanan Panenku.",11f,color=FMuted,align=TextAlign.Center,modifier=Modifier.fillMaxWidth())
    }
}
